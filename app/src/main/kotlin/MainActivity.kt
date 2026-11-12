// Appurrge, GPL-3.0-only. Based on Batch Uninstaller by Sarbajit Saha.
package com.appurrge

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.InsetDrawable
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.appurrge.databinding.ActivityMainBinding
import com.appurrge.databinding.DialogProgressBinding

/** The one screen. It only draws what [AppModel] says and passes taps back to it. */
class MainActivity : Activity(), AppModel.Listener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var model: AppModel
    private lateinit var adapter: AppInfoAdapter
    private val imm by lazy { getSystemService(InputMethodManager::class.java) }

    private var searchOpen = false
    private var keyboardWasUp = false
    private var lastScrollToken = 0
    private var progressDialog: AlertDialog? = null
    private var progressBinding: DialogProgressBinding? = null

    // ---- lifecycle ------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = AppModel.get(this)
        // A rotation or dark-mode change recreates this screen, but the model (and so the selection) stays.
        // On a fresh launch, start loading now so it overlaps with building the layout.
        if (savedInstanceState == null) model.start()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTopBar()
        setupList()
        watchKeyboard()
        binding.tabUser.setOnClickListener { model.setTab(AppTab.USER) }
        binding.tabSystem.setOnClickListener { model.setTab(AppTab.SYSTEM) }
        binding.fabUninstall.setOnClickListener {
            val s = model.state
            if (s.selectedSystemCount > 0 && !s.privileged) model.connectPrivileged() else confirmUninstall()
        }
    }

    override fun onStart() {
        super.onStart()
        model.listener = this // sends the current state right away
    }

    override fun onResume() {
        super.onResume()
        model.refreshShizuku() // the user may just have started Shizuku or allowed this app in it
    }

    override fun onStop() {
        model.listener = null
        super.onStop()
    }

    override fun onDestroy() {
        progressDialog?.dismiss()
        progressDialog = null
        super.onDestroy()
    }

    /** Back closes search first, then clears the selection, then leaves the app. */
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when {
            searchOpen -> setSearchOpen(false)
            model.state.selectedCount > 0 -> model.clearSelection()
            else -> super.onBackPressed()
        }
    }

    /** Android's own uninstall dialog (the fallback without Shizuku/root) has closed. */
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_UNINSTALL) model.onPromptResult()
    }

    // ---- setup ----------------------------------------------------------------------------------

    private fun setupTopBar() {
        // Right: search (a cross while open) next to sort. Left: a back arrow only while apps are
        // selected. So search and sort both stay usable with a selection.
        binding.btnSearch.setOnClickListener { setSearchOpen(!searchOpen) }
        binding.btnBack.setOnClickListener { model.clearSelection() }
        binding.btnSort.setOnClickListener { showSortDialog() }
        binding.etSearch.setOnEditorActionListener { _, _, _ ->
            imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
            true
        }
        // After a rotation the box is rebuilt empty, but the model still filters: restore it.
        val restored = model.state.query
        if (restored.isNotEmpty()) {
            binding.etSearch.setText(restored)
            setSearchOpen(true, focus = false)
        }
        binding.etSearch.doAfterTextChanged { model.setQuery(it?.toString().orEmpty()) }
    }

    private fun setSearchOpen(open: Boolean, focus: Boolean = true) {
        searchOpen = open
        binding.etSearch.isVisible = open
        binding.tvTitle.isVisible = !open
        if (open) {
            if (focus) {
                binding.etSearch.requestFocus()
                binding.etSearch.post { imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT) }
            }
        } else {
            binding.etSearch.setText("")
            binding.etSearch.clearFocus()
            imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
        }
        updateTopBar()
    }

    private fun updateTopBar() {
        binding.btnBack.isVisible = model.state.selectedCount > 0
        binding.btnSearch.setImageResource(if (searchOpen) R.drawable.ic_close else R.drawable.ic_search)
    }

    /**
     * Back, the keyboard's own hide button or tapping elsewhere hides the keyboard but leaves the blinking
     * cursor in the search box. Whenever the keyboard goes away while searching, take the focus off the box
     * (the search itself stays open; tapping the box again brings the keyboard back).
     */
    private fun watchKeyboard() {
        val root = binding.root
        val threshold = 100 * resources.displayMetrics.density
        root.viewTreeObserver.addOnGlobalLayoutListener {
            if (!searchOpen) {
                keyboardWasUp = false
                return@addOnGlobalLayoutListener
            }
            val frame = Rect()
            root.getWindowVisibleDisplayFrame(frame)
            val up = root.rootView.height - frame.bottom > threshold
            if (keyboardWasUp && !up) {
                binding.etSearch.clearFocus()
                root.requestFocus() // the root takes the focus, so the search box does not get it back
            }
            keyboardWasUp = up
        }
    }

    private fun setupList() {
        adapter = AppInfoAdapter(this, model.icons)
        val list = binding.listApps
        list.adapter = adapter
        list.onRowTap = { position -> if (position < adapter.count) model.toggle(adapter.getItem(position).app.packageName) }
        list.popupText = { adapter.popupText(it) }

        // Long pull needed, so scrolling back to the top does not refresh by accident.
        binding.swipe.setDistanceToTriggerSync((REFRESH_PULL_DP * resources.displayMetrics.density).toInt())
        binding.swipe.setOnRefreshListener { model.refresh() }
    }

    // ---- rendering ------------------------------------------------------------------------------

    override fun onState(state: UiState) {
        adapter.sortMode = state.sort
        adapter.submit(state.rows)
        if (state.scrollToTopToken != lastScrollToken) {
            lastScrollToken = state.scrollToTopToken
            binding.listApps.setSelection(0)
        }
        binding.progressInitial.isVisible = state.loading
        binding.tvEmpty.isVisible = !state.loading && state.rows.isEmpty()
        binding.swipe.isRefreshing = state.refreshing

        for ((tab, active) in listOf(binding.tabUser to (state.tab == AppTab.USER), binding.tabSystem to (state.tab == AppTab.SYSTEM))) {
            tab.isActivated = active // draws the underline (bg_tab)
            tab.alpha = if (active) 1f else 0.7f
        }
        binding.tabUser.setIfChanged(getString(R.string.tab_user, state.userCount))
        binding.tabSystem.setIfChanged(getString(R.string.tab_system, state.systemCount))

        binding.tvTitle.setIfChanged(
            if (state.selectedCount > 0) getString(R.string.n_selected, state.selectedCount) else getString(R.string.app_name)
        )
        updateTopBar()
        renderButton(state)
        renderProgress(state.progress)
    }

    private fun TextView.setIfChanged(value: String) {
        if (text.toString() != value) text = value
    }

    /** Bottom button: "Uninstall" normally; with system apps selected and no access yet: "ROOT ACCESS". */
    private fun renderButton(s: UiState) {
        val fab = binding.fabUninstall
        fab.isVisible = s.selectedCount > 0 && s.progress == null
        if (!fab.isVisible) return
        val needsAccess = s.selectedSystemCount > 0 && !s.privileged
        fab.setIfChanged(
            if (needsAccess) getString(R.string.root_access)
            else getString(R.string.uninstall_fab, s.selectedCount, Formatter.formatShortFileSize(this, s.selectedBytes))
        )
        fab.setCompoundDrawablesRelativeWithIntrinsicBounds(
            if (needsAccess) R.drawable.ic_shizuku else R.drawable.ic_delete, 0, 0, 0
        )
    }

    private fun renderProgress(p: UninstallProgress?) {
        if (p == null || p.cancelling) {
            progressDialog?.dismiss()
            progressDialog = null
            progressBinding = null
            return
        }
        if (progressDialog == null) {
            val b = DialogProgressBinding.inflate(layoutInflater)
            progressBinding = b
            progressDialog = AlertDialog.Builder(this)
                .setTitle(R.string.uninstalling)
                .setView(b.root)
                .setCancelable(false)
                .setNegativeButton(R.string.cancel) { _, _ -> model.cancelUninstall() }
                .setOnDismissListener {
                    progressDialog = null
                    progressBinding = null
                }
                .show()
        }
        progressBinding?.let {
            it.progressBar.max = p.total
            it.progressBar.progress = p.done
            it.tvProgress.text = getString(R.string.uninstalling_item, p.done + 1, p.total, p.current)
        }
    }

    // ---- dialogs --------------------------------------------------------------------------------

    /** Sort order. Every row spans the full width and highlights as a rectangle when pressed. */
    private fun showSortDialog() {
        val d = resources.displayMetrics.density
        val builder = AlertDialog.Builder(this)
        val ctx = builder.context // themed like the dialog itself
        val rowBackground = TypedValue().also {
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }.resourceId
        var dialog: AlertDialog? = null
        val group = RadioGroup(ctx).apply { setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt()) }
        resources.getStringArray(R.array.sort).forEachIndexed { index, label ->
            val row = RadioButton(ctx).apply {
                id = index + 1
                text = label
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                // The default highlight is a circle around the dot; use a rectangle over the whole row.
                setBackgroundResource(rowBackground)
                // The dot is drawn at the very left of its view, so inset it from the dialog edge;
                // the start padding is the gap between the dot and the text.
                buttonDrawable = InsetDrawable(buttonDrawable, (24 * d).toInt(), 0, 0, 0)
                setPaddingRelative((12 * d).toInt(), 0, (24 * d).toInt(), 0)
                setOnClickListener {
                    model.setSort(SortMode.values()[index])
                    dialog?.dismiss()
                }
            }
            group.addView(row, RadioGroup.LayoutParams(RadioGroup.LayoutParams.MATCH_PARENT, (44 * d).toInt()))
        }
        group.check(model.state.sort.ordinal + 1)
        dialog = builder.setTitle(R.string.sort).setView(group).show()
    }

    private fun confirmUninstall() {
        val s = model.state
        if (s.selectedCount == 0) return
        val message = if (s.selectedSystemCount > 0) {
            getString(R.string.system_warning)
        } else {
            getString(R.string.uninstall_confirm_message, Formatter.formatShortFileSize(this, s.selectedBytes))
        }
        AlertDialog.Builder(this)
            .setTitle(resources.getQuantityString(R.plurals.uninstall_confirm_title, s.selectedCount, s.selectedCount))
            .setMessage(message)
            .setPositiveButton(R.string.uninstall) { _, _ -> model.beginUninstall() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---- events from the model ------------------------------------------------------------------

    @Suppress("DEPRECATION") // startActivityForResult: the replacement needs a library this app does not use
    override fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.Message -> Toast.makeText(this, event.text, Toast.LENGTH_LONG).show()
            UiEvent.OpenShizuku -> openShizuku()
            is UiEvent.PromptUninstall -> {
                val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${event.packageName}"))
                    .putExtra(Intent.EXTRA_RETURN_RESULT, true)
                try {
                    startActivityForResult(intent, REQUEST_UNINSTALL)
                } catch (e: ActivityNotFoundException) {
                    model.onPromptResult()
                }
            }
        }
    }

    private fun openShizuku() {
        val launch = packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        val intent = launch ?: Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_URL))
        Toast.makeText(this, if (launch != null) R.string.start_shizuku else R.string.no_shizuku, Toast.LENGTH_LONG).show()
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // nothing can open it; the message above already says what to do
        }
    }

    private companion object {
        const val REQUEST_UNINSTALL = 1
        const val REFRESH_PULL_DP = 140
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val SHIZUKU_URL = "https://shizuku.rikka.app/download/"
    }
}
