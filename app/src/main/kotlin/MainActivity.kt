// Appurrge, GPL-3.0-or-later. Based on Batch Uninstaller by Sarbajit Saha.
package com.appurrge

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.InsetDrawable
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.appurrge.databinding.ActivityMainBinding
import com.appurrge.databinding.DialogProgressBinding
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding
    private val vm: AppListViewModel by viewModels()
    private lateinit var adapter: AppInfoAdapter
    private val imm by lazy { getSystemService(InputMethodManager::class.java) }

    private var searchOpen = false
    private var lastScrollToken = 0
    private var uninstallAfterPermission = false
    private var progressDialog: AlertDialog? = null
    private var progressBinding: DialogProgressBinding? = null

    /** Android's own uninstall dialog (fallback without Shizuku/root). */
    private val uninstallLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.onPromptResult() }

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
        if (requestCode == REQUEST_SHIZUKU) {
            refreshShizuku()
            if (uninstallAfterPermission) {
                uninstallAfterPermission = false
                vm.uninstallSelected() // uses Shizuku if allowed, otherwise root or Android's dialog
            }
        }
    }
    private val shizukuBinderListener = Shizuku.OnBinderReceivedListener { refreshShizuku() }

    /** Back closes search first, then clears the selection, then leaves the app. */
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (searchOpen) setSearchOpen(false) else vm.clearSelection()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTopBar()
        setupList()
        binding.tabUser.setOnClickListener { vm.setTab(AppTab.USER) }
        binding.tabSystem.setOnClickListener { vm.setTab(AppTab.SYSTEM) }
        binding.fabUninstall.setOnClickListener {
            val s = vm.state.value
            if (s.selectedSystemCount > 0 && !s.privileged) connectPrivileged() else confirmUninstall()
        }

        onBackPressedDispatcher.addCallback(this, backCallback)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        Shizuku.addBinderReceivedListener(shizukuBinderListener)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.state.collect { render(it) } }
                launch { vm.events.collect { handleEvent(it) } }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshShizuku() // the user may just have started Shizuku or allowed this app in it
    }

    override fun onDestroy() {
        // Shizuku's remove...() calls Collection.removeIf, which Android 6 does not have; there it throws.
        // Ignoring that only means the (tiny) listeners stay registered.
        runCatching { Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener) }
        runCatching { Shizuku.removeBinderReceivedListener(shizukuBinderListener) }
        progressDialog?.dismiss()
        progressDialog = null
        super.onDestroy()
    }

    // ---- setup ----------------------------------------------------------------------------------

    private fun setupTopBar() {
        // Right: search (a cross while open) next to sort. Left: a back arrow only while apps are
        // selected. So search and sort both stay usable with a selection.
        binding.btnSearch.setOnClickListener { setSearchOpen(!searchOpen) }
        binding.btnBack.setOnClickListener { vm.clearSelection() }
        binding.btnSort.setOnClickListener { showSortDialog() }
        binding.etSearch.setOnEditorActionListener { _, _, _ ->
            imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
            true
        }
        // After a rotation the box is rebuilt empty, but the view model still filters: restore it.
        val restored = vm.state.value.query
        if (restored.isNotEmpty()) {
            binding.etSearch.setText(restored)
            setSearchOpen(true, focus = false)
        }
        binding.etSearch.doAfterTextChanged { vm.setQuery(it?.toString().orEmpty()) }
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
        val selecting = vm.state.value.selectedCount > 0
        backCallback.isEnabled = searchOpen || selecting
        binding.btnBack.isVisible = selecting
        binding.btnSearch.setImageResource(if (searchOpen) R.drawable.ic_close else R.drawable.ic_search)
    }

    private fun setupList() {
        adapter = AppInfoAdapter(this, lifecycleScope, vm.icons) { vm.toggle(it.packageName) }
        val list = binding.rvApplist
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        list.setHasFixedSize(true)
        list.itemAnimator = null // switching tabs and selecting rows must be instant

        FastScroller(list) { adapter.popupText(it) }

        // Long pull needed, so scrolling back to the top does not refresh by accident.
        binding.swipe.setDistanceToTriggerSync((REFRESH_PULL_DP * resources.displayMetrics.density).toInt())
        binding.swipe.setOnRefreshListener { vm.refresh() }
    }

    // ---- rendering ------------------------------------------------------------------------------

    private fun render(s: UiState) {
        adapter.sortMode = s.sort
        adapter.submitList(s.rows) {
            if (s.scrollToTopToken != lastScrollToken) {
                lastScrollToken = s.scrollToTopToken
                binding.rvApplist.scrollToPosition(0)
            }
        }
        binding.progressInitial.isVisible = s.loading
        binding.tvEmpty.isVisible = !s.loading && s.rows.isEmpty()
        binding.swipe.isRefreshing = s.refreshing

        for ((tab, active) in listOf(binding.tabUser to (s.tab == AppTab.USER), binding.tabSystem to (s.tab == AppTab.SYSTEM))) {
            tab.isActivated = active // draws the underline (bg_tab)
            tab.alpha = if (active) 1f else 0.7f
        }
        binding.tabUser.setIfChanged(getString(R.string.tab_user, s.userCount))
        binding.tabSystem.setIfChanged(getString(R.string.tab_system, s.systemCount))

        binding.tvTitle.setIfChanged(
            if (s.selectedCount > 0) getString(R.string.n_selected, s.selectedCount) else getString(R.string.app_name)
        )
        updateTopBar()
        renderButton(s)
        renderProgress(s.progress)
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
                .setNegativeButton(R.string.cancel) { _, _ -> vm.cancelUninstall() }
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

    /** Sort order. Every row spans the full width, so tapping the empty space on the right also selects it. */
    private fun showSortDialog() {
        val d = resources.displayMetrics.density
        var dialog: AlertDialog? = null
        val group = RadioGroup(this).apply { setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt()) }
        resources.getStringArray(R.array.sort).forEachIndexed { index, label ->
            val row = RadioButton(this).apply {
                id = index + 1
                text = label
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                // A radio dot is drawn at the very left edge of its view, so inset it from the dialog edge;
                // the start padding is the gap between the dot and the text.
                buttonDrawable = InsetDrawable(buttonDrawable, (24 * d).toInt(), 0, 0, 0)
                setPaddingRelative((12 * d).toInt(), 0, (24 * d).toInt(), 0)
                setOnClickListener {
                    vm.setSort(SortMode.values()[index])
                    dialog?.dismiss()
                }
            }
            group.addView(row, RadioGroup.LayoutParams(RadioGroup.LayoutParams.MATCH_PARENT, (44 * d).toInt()))
        }
        group.check(vm.state.value.sort.ordinal + 1)
        val shown = AlertDialog.Builder(this).setTitle(R.string.sort).setView(group).show()
        dialog = shown
    }

    private fun confirmUninstall() {
        val s = vm.state.value
        if (s.selectedCount == 0) return
        val message = if (s.selectedSystemCount > 0) {
            getString(R.string.system_warning)
        } else {
            getString(R.string.uninstall_confirm_message, Formatter.formatShortFileSize(this, s.selectedBytes))
        }
        AlertDialog.Builder(this)
            .setTitle(resources.getQuantityString(R.plurals.uninstall_confirm_title, s.selectedCount, s.selectedCount))
            .setMessage(message)
            .setPositiveButton(R.string.uninstall) { _, _ -> beginUninstall() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---- Shizuku / root -------------------------------------------------------------------------

    private fun beginUninstall() {
        refreshShizuku()
        if (!vm.state.value.privileged && shizukuRunning() && shizukuCanAsk()) {
            // Ask once for silent uninstalling; the result listener continues from here.
            uninstallAfterPermission = true
            Shizuku.requestPermission(REQUEST_SHIZUKU)
        } else {
            vm.uninstallSelected()
        }
    }

    /** "ROOT ACCESS" pressed: ask Shizuku for permission, else try root, else open the Shizuku app. */
    private fun connectPrivileged() {
        refreshShizuku()
        if (vm.state.value.privileged) return
        if (shizukuRunning()) {
            if (shizukuCanAsk()) Shizuku.requestPermission(REQUEST_SHIZUKU) else toast(R.string.shizuku_denied)
        } else {
            vm.tryRoot()
        }
    }

    private fun openShizuku() {
        val launch = packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        val intent = launch ?: Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_URL))
        toast(if (launch != null) R.string.start_shizuku else R.string.no_shizuku)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // nothing can open it; the message above already says what to do
        }
    }

    private fun refreshShizuku() = vm.setShizukuReady(shizukuRunning() && shizukuPermitted())

    private fun shizukuRunning() = try {
        Shizuku.pingBinder() && !Shizuku.isPreV11()
    } catch (e: Throwable) {
        false
    }

    private fun shizukuPermitted() = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
        false
    }

    /** False once the user chose "deny and don't ask again". */
    private fun shizukuCanAsk() = try {
        !Shizuku.shouldShowRequestPermissionRationale()
    } catch (e: Throwable) {
        false
    }

    // ---- events ---------------------------------------------------------------------------------

    private fun handleEvent(event: UiEvent) {
        when (event) {
            is UiEvent.Message -> Toast.makeText(this, event.text, Toast.LENGTH_LONG).show()
            UiEvent.OpenShizuku -> openShizuku()
            is UiEvent.PromptUninstall -> {
                val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${event.packageName}"))
                    .putExtra(Intent.EXTRA_RETURN_RESULT, true)
                try {
                    uninstallLauncher.launch(intent)
                } catch (e: ActivityNotFoundException) {
                    vm.onPromptResult()
                }
            }
        }
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_LONG).show()

    private companion object {
        const val REQUEST_SHIZUKU = 1001
        const val REFRESH_PULL_DP = 200
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val SHIZUKU_URL = "https://shizuku.rikka.app/download/"
    }
}
