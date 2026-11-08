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

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saha.batchuninstaller.AppInfo
import com.saha.batchuninstaller.BuildConfig
import com.saha.batchuninstaller.R
import com.saha.batchuninstaller.shizuku.ShizukuUninstaller
import com.saha.batchuninstaller.utils.AppFilter
import com.saha.batchuninstaller.utils.AppListLogic
import com.saha.batchuninstaller.utils.AppTab
import com.saha.batchuninstaller.utils.IconLoader
import com.saha.batchuninstaller.utils.PackageUtils
import com.saha.batchuninstaller.utils.RootUtils
import com.saha.batchuninstaller.utils.SortMode
import com.saha.batchuninstaller.utils.UninstallBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns everything that must survive rotation: the app list, the selection, tab/sort/filter/search
 * and a running uninstall. Always used from the main thread.
 */
class AppListViewModel(application: Application) : AndroidViewModel(application) {
    private val app: Application = application

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val eventChannel = Channel<UiEvent>(Channel.UNLIMITED)
    val events: Flow<UiEvent> = eventChannel.receiveAsFlow()

    val icons = IconLoader(
        application.packageManager,
        (48 * application.resources.displayMetrics.density).toInt()
    )

    /** Every installed app, unfiltered. */
    private var all: List<AppInfo> = emptyList()

    /** Selected package names. Keyed by package so selection survives refresh, search and tabs. */
    private val selected = LinkedHashSet<String>()

    private var loadJob: Job? = null
    private var uninstallJob: Job? = null
    private var cancelRequested = false

    private val promptQueue = ArrayDeque<String>()
    private var currentPrompt: String? = null

    // Must stay below the properties above: it uses them.
    init {
        load()
    }

    // ---------------------------------------------------------------- loading

    fun refresh() = load()

    private fun load() {
        if (loadJob?.isActive == true) return
        val first = all.isEmpty()
        _state.update { it.copy(loading = first, refreshing = !first) }
        loadJob = viewModelScope.launch {
            try {
                val apps = PackageUtils.loadApps(app)
                all = apps
                // Keep the selection across a refresh, minus apps that no longer exist.
                selected.retainAll(apps.mapTo(HashSet()) { it.packageName })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                eventChannel.trySend(UiEvent.Message(app.getString(R.string.load_failed)))
            } finally {
                publish { it.copy(loading = false, refreshing = false) }
            }
        }
    }

    // ---------------------------------------------------------------- list controls

    fun setTab(tab: AppTab) {
        if (_state.value.tab == tab) return
        publish { it.copy(tab = tab, scrollToTopToken = it.scrollToTopToken + 1) }
    }

    fun setSort(sort: SortMode) {
        if (_state.value.sort == sort) return
        publish { it.copy(sort = sort, scrollToTopToken = it.scrollToTopToken + 1) }
    }

    fun setFilter(filter: AppFilter) {
        if (_state.value.filter == filter) return
        publish { it.copy(filter = filter, scrollToTopToken = it.scrollToTopToken + 1) }
    }

    fun setQuery(query: String) {
        if (_state.value.query == query) return
        publish { it.copy(query = query, scrollToTopToken = it.scrollToTopToken + 1) }
    }

    fun toggle(packageName: String) {
        if (_state.value.progress != null) return
        if (!selected.add(packageName)) selected.remove(packageName)
        publish()
    }

    fun clearSelection() {
        if (_state.value.progress != null) return
        selected.clear()
        publish()
    }

    /** Recomputes the visible rows and counters from [all], [selected] and the controls. */
    private fun publish(transform: (UiState) -> UiState = { it }) {
        _state.update { current ->
            val base = transform(current)
            val chosen = all.filter { it.packageName in selected }
            base.copy(
                rows = AppListLogic
                    .visible(all, base.tab, base.query, base.filter, base.sort, System.currentTimeMillis())
                    .map { Row(it, it.packageName in selected) },
                userCount = all.count { !it.isSystem },
                systemCount = all.count { it.isSystem },
                selectedCount = chosen.size,
                selectedSystemCount = chosen.count { it.isSystem },
                selectedBytes = chosen.sumOf { it.size }
            )
        }
    }

    private fun removeApp(packageName: String) {
        all = all.filter { it.packageName != packageName }
        selected.remove(packageName)
        publish()
    }

    // ---------------------------------------------------------------- uninstalling

    /**
     * @param useShizuku true if the UI has already confirmed Shizuku is running and permitted.
     * Otherwise root is tried, and if that is missing too, Android's own dialog is shown per app.
     */
    fun uninstallSelected(useShizuku: Boolean) {
        if (uninstallJob?.isActive == true || selected.isEmpty()) return
        // This app itself goes last, otherwise the batch would die half way through.
        val targets = all
            .filter { it.packageName in selected }
            .sortedBy { it.packageName == BuildConfig.APPLICATION_ID }
        cancelRequested = false
        uninstallJob = viewModelScope.launch {
            val backend: UninstallBackend? = when {
                useShizuku -> ShizukuUninstaller
                RootUtils.isRootAvailable() -> RootUtils
                else -> null
            }
            if (backend == null) startPrompts(targets) else runBatch(backend, targets)
        }
    }

    fun cancelUninstall() {
        cancelRequested = true
        _state.update { s -> s.copy(progress = s.progress?.copy(cancelling = true)) }
    }

    private suspend fun runBatch(backend: UninstallBackend, targets: List<AppInfo>) {
        var ok = 0
        var failed = 0
        try {
            for ((index, target) in targets.withIndex()) {
                if (cancelRequested) break
                _state.update { it.copy(progress = UninstallProgress(index, targets.size, target.appName)) }
                if (backend.uninstall(target)) {
                    ok++
                    removeApp(target.packageName)
                } else {
                    failed++ // stays selected so it is easy to see and retry
                }
            }
        } finally {
            _state.update { it.copy(progress = null) }
            if (backend === ShizukuUninstaller) ShizukuUninstaller.release()
        }
        val message = when {
            cancelRequested -> app.getString(R.string.uninstall_cancelled, ok)
            failed == 0 -> app.getString(R.string.uninstall_complete)
            else -> app.getString(R.string.uninstall_summary, ok, failed)
        }
        eventChannel.trySend(UiEvent.Message(message))
    }

    // Fallback with neither Shizuku nor root: one Android dialog per app, as in the original app.
    private fun startPrompts(targets: List<AppInfo>) {
        val (system, user) = targets.partition { it.isSystem }
        if (system.isNotEmpty()) {
            eventChannel.trySend(
                UiEvent.Message(app.getString(R.string.system_apps_need_privileges, system.size))
            )
        }
        promptQueue.clear()
        promptQueue.addAll(user.map { it.packageName })
        nextPrompt()
    }

    private fun nextPrompt() {
        val next = promptQueue.removeFirstOrNull()
        currentPrompt = next
        if (next != null) eventChannel.trySend(UiEvent.PromptUninstall(next))
    }

    /** Called by the UI when Android's uninstall dialog for the current package has closed. */
    fun onPromptResult() {
        val pkg = currentPrompt ?: return
        if (!PackageUtils.isInstalled(app, pkg)) removeApp(pkg)
        nextPrompt()
        if (currentPrompt == null) {
            eventChannel.trySend(UiEvent.Message(app.getString(R.string.uninstall_complete)))
        }
    }

    override fun onCleared() {
        ShizukuUninstaller.release()
    }
}
