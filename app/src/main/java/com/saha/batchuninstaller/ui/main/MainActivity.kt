/*
 * This file is part of Batch Uninstaller.
 *
 * Batch Uninstaller is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Batch Uninstaller is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Batch Uninstaller.  If not, see <http:></http:>//www.gnu.org/licenses/>.
 */
package com.saha.batchuninstaller.ui.main

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.saha.batchuninstaller.R
import com.saha.batchuninstaller.databinding.ActivityMainBinding
import com.saha.batchuninstaller.databinding.DialogProgressBinding
import com.saha.batchuninstaller.ui.main.adapters.AppInfoAdapter
import com.saha.batchuninstaller.utils.AppFilter
import com.saha.batchuninstaller.utils.AppTab
import com.saha.batchuninstaller.utils.SortMode
import kotlinx.coroutines.launch
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val vm: AppListViewModel by viewModels()
    private lateinit var adapter: AppInfoAdapter

    private lateinit var searchItem: MenuItem
    private lateinit var searchView: SearchView
    private var searchExpanded = false

    private var lastScrollToken = 0
    private var progressDialog: AlertDialog? = null
    private var progressBinding: DialogProgressBinding? = null

    /** Android's own uninstall dialog, used when there is neither Shizuku nor root. */
    private val uninstallLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.onPromptResult() }

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_SHIZUKU) {
                vm.uninstallSelected(useShizuku = grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }

    /** Back closes the search box first, then clears the selection, then leaves the app. */
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            when {
                searchExpanded -> searchItem.collapseActionView()
                vm.state.value.selectedCount > 0 -> vm.clearSelection()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupTabs()
        setupList()
        binding.chipSort.setOnClickListener { showSortDialog() }
        binding.chipFilter.setOnClickListener { showFilterDialog() }
        binding.fabUninstall.setOnClickListener { confirmUninstall() }

        onBackPressedDispatcher.addCallback(this, backCallback)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.state.collect { render(it) } }
                launch { vm.events.collect { handleEvent(it) } }
            }
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        progressDialog?.dismiss()
        progressDialog = null
        super.onDestroy()
    }

    // ---------------------------------------------------------------- setup

    private fun setupToolbar() {
        val toolbar = binding.toolbar
        toolbar.inflateMenu(R.menu.main_menu)
        toolbar.setNavigationOnClickListener { vm.clearSelection() }

        searchItem = toolbar.menu.findItem(R.id.action_search)
        searchView = searchItem.actionView as SearchView
        searchView.queryHint = getString(R.string.search_hint)
        searchView.maxWidth = Int.MAX_VALUE
        searchView.imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI

        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                searchExpanded = true
                updateBackAndNavigation()
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                searchExpanded = false
                updateBackAndNavigation()
                return true
            }
        })

        // After a rotation the search box is rebuilt empty, but the view model still filters.
        val restored = vm.state.value.query
        if (restored.isNotEmpty()) {
            searchItem.expandActionView()
            searchView.setQuery(restored, false)
        }

        // Attached last so that restoring the text above cannot clear the query.
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchView.clearFocus() // just hides the keyboard
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                vm.setQuery(newText.orEmpty())
                return true
            }
        })
    }

    private fun setupTabs() {
        val tabs = binding.tabs
        tabs.addTab(tabs.newTab().setText(getString(R.string.tab_user, 0))) // position 0 = USER
        tabs.addTab(tabs.newTab().setText(getString(R.string.tab_system, 0))) // position 1 = SYSTEM
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                vm.setTab(if (tab.position == 0) AppTab.USER else AppTab.SYSTEM)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun setupList() {
        adapter = AppInfoAdapter(this, lifecycleScope, vm.icons) { vm.toggle(it.packageName) }
        val list = binding.rvApplist
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        list.setHasFixedSize(true)
        // Selecting a row should not flash a cross-fade animation.
        (list.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        // Draggable scroll thumb; its popup shows a letter, a month or a size (see the adapter).
        FastScrollerBuilder(list).useMd2Style().setPopupTextProvider(adapter).build()

        // The default pull distance is easy to trigger by accident while scrolling back to the top.
        binding.swipe.setDistanceToTriggerSync((REFRESH_PULL_DP * resources.displayMetrics.density).toInt())
        binding.swipe.setOnRefreshListener { vm.refresh() }
    }

    // ---------------------------------------------------------------- rendering

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
        binding.tvCount.text = resources.getQuantityString(R.plurals.apps_count, s.rows.size, s.rows.size)

        // Tabs
        val wantedTab = if (s.tab == AppTab.USER) 0 else 1
        if (binding.tabs.selectedTabPosition != wantedTab) binding.tabs.getTabAt(wantedTab)?.select()
        setTabLabel(0, getString(R.string.tab_user, s.userCount))
        setTabLabel(1, getString(R.string.tab_system, s.systemCount))

        // Pinned sort / filter chips
        binding.chipSort.text = getString(R.string.chip_sort, resources.getStringArray(R.array.sort_short)[s.sort.ordinal])
        binding.chipFilter.text = getString(R.string.chip_filter, resources.getStringArray(R.array.filter)[s.filter.ordinal])

        // Selection
        binding.toolbar.title = if (s.selectedCount > 0) {
            getString(R.string.n_selected, s.selectedCount)
        } else {
            getString(R.string.app_name)
        }
        updateBackAndNavigation()
        binding.fabUninstall.isVisible = s.selectedCount > 0 && s.progress == null
        binding.fabUninstall.text = getString(
            R.string.uninstall_fab,
            s.selectedCount,
            Formatter.formatShortFileSize(this, s.selectedBytes)
        )

        renderProgress(s.progress)
    }

    private fun setTabLabel(position: Int, label: String) {
        val tab = binding.tabs.getTabAt(position) ?: return
        if (tab.text?.toString() != label) tab.text = label
    }

    private fun updateBackAndNavigation() {
        val selecting = vm.state.value.selectedCount > 0
        backCallback.isEnabled = searchExpanded || selecting
        val icon: Drawable? = if (selecting && !searchExpanded) {
            AppCompatResources.getDrawable(this, R.drawable.ic_close_24dp)
        } else {
            null
        }
        binding.toolbar.setNavigationIcon(icon)
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
            progressDialog = MaterialAlertDialogBuilder(this)
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

    // ---------------------------------------------------------------- dialogs

    private fun showSortDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sort)
            .setSingleChoiceItems(R.array.sort, vm.state.value.sort.ordinal) { dialog, which ->
                vm.setSort(SortMode.values()[which])
                dialog.dismiss()
            }
            .show()
    }

    private fun showFilterDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.filter)
            .setSingleChoiceItems(R.array.filter, vm.state.value.filter.ordinal) { dialog, which ->
                vm.setFilter(AppFilter.values()[which])
                dialog.dismiss()
            }
            .show()
    }

    private fun confirmUninstall() {
        val s = vm.state.value
        if (s.selectedCount == 0) return
        var message = getString(R.string.uninstall_confirm_message, Formatter.formatShortFileSize(this, s.selectedBytes))
        if (s.selectedSystemCount > 0) message += "\n\n" + getString(R.string.uninstall_system_warning)
        MaterialAlertDialogBuilder(this)
            .setTitle(resources.getQuantityString(R.plurals.uninstall_confirm_title, s.selectedCount, s.selectedCount))
            .setMessage(message)
            .setPositiveButton(R.string.uninstall) { _, _ -> beginUninstall() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------------------------------------------------------------- uninstalling

    /** Order of preference: Shizuku, then root, then Android's own dialog for each app. */
    private fun beginUninstall() {
        if (shizukuRunning()) {
            if (shizukuPermitted()) {
                vm.uninstallSelected(useShizuku = true)
                return
            }
            // "Deny and don't ask again" -> skip Shizuku quietly instead of nagging.
            if (shizukuCanAsk()) {
                Shizuku.requestPermission(REQUEST_SHIZUKU)
                return
            }
        }
        vm.uninstallSelected(useShizuku = false)
    }

    private fun shizukuRunning(): Boolean = try {
        Shizuku.pingBinder() && !Shizuku.isPreV11()
    } catch (e: Throwable) {
        false
    }

    private fun shizukuPermitted(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
        false
    }

    private fun shizukuCanAsk(): Boolean = try {
        !Shizuku.shouldShowRequestPermissionRationale()
    } catch (e: Throwable) {
        false
    }

    private fun handleEvent(event: UiEvent) {
        when (event) {
            is UiEvent.Message -> Toast.makeText(this, event.text, Toast.LENGTH_LONG).show()
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

    private companion object {
        const val REQUEST_SHIZUKU = 1001
        const val REFRESH_PULL_DP = 200
    }
}
