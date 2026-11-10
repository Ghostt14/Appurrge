// Appurrge, GPL-3.0-only. Based on Batch Uninstaller by Sarbajit Saha.
package com.appurrge

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything that must survive rotation: the list, selection, tab/sort/search and a running uninstall. */
class AppListViewModel(private val app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val eventChannel = Channel<UiEvent>(Channel.UNLIMITED)
    val events: Flow<UiEvent> = eventChannel.receiveAsFlow()

    val icons = IconLoader(app.packageManager, (40 * app.resources.displayMetrics.density).toInt())

    private var all: List<AppInfo> = emptyList()
    private val selected = LinkedHashSet<String>()
    private var shizukuReady = false
    private var rootReady = false

    private var loadJob: Job? = null
    private var uninstallJob: Job? = null
    private var rootJob: Job? = null
    private var cancelRequested = false

    private val promptQueue = ArrayDeque<String>()
    private var currentPrompt: String? = null

    // Keep below the properties above: it uses them.
    init {
        viewModelScope.launch(Dispatchers.Default) { AppListLogic.warmUp() }
        load()
    }

    // ---- loading --------------------------------------------------------------------------------

    fun refresh() = load()

    /**
     * Demo build: the list is made-up sample data (see DemoData), not the phone's real apps.
     * Pulling to refresh brings back any apps that were "uninstalled", handy for a second take.
     */
    private fun load() {
        if (loadJob?.isActive == true) return
        val first = all.isEmpty()
        _state.update { it.copy(loading = first, refreshing = !first) }
        loadJob = viewModelScope.launch {
            if (!first) delay(700) // long enough for the refresh spinner to be seen
            all = DemoData.apps()
            selected.retainAll(all.mapTo(HashSet()) { it.packageName })
            publish { it.copy(loading = false, refreshing = false) }
        }
    }

    // ---- list controls --------------------------------------------------------------------------

    fun setTab(tab: AppTab) = change(_state.value.tab != tab) { it.copy(tab = tab) }
    fun setSort(sort: SortMode) = change(_state.value.sort != sort) { it.copy(sort = sort) }
    fun setQuery(query: String) = change(_state.value.query != query) { it.copy(query = query) }

    private fun change(changed: Boolean, transform: (UiState) -> UiState) {
        if (changed) publish { transform(it).let { s -> s.copy(scrollToTopToken = s.scrollToTopToken + 1) } }
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

    fun setShizukuReady(ready: Boolean) {
        if (shizukuReady == ready) return
        shizukuReady = ready
        publish()
    }

    /** "ROOT ACCESS" without Shizuku: try su; if that fails too, send the user to Shizuku. */
    fun tryRoot() {
        if (rootJob?.isActive == true) return
        rootJob = viewModelScope.launch {
            delay(400) // pretend to ask for access
            rootReady = true
            publish()
        }
    }

    private suspend fun isRoot(): Boolean {
        if (!rootReady) rootReady = RootUtils.isRootAvailable()
        return rootReady
    }

    private fun publish(transform: (UiState) -> UiState = { it }) {
        _state.update { current ->
            val base = transform(current)
            val chosen = all.filter { it.packageName in selected }
            base.copy(
                rows = AppListLogic.visible(all, base.tab, base.query, base.sort)
                    .map { Row(it, it.packageName in selected) },
                userCount = all.count { !it.isSystem },
                systemCount = all.count { it.isSystem },
                selectedCount = chosen.size,
                selectedSystemCount = chosen.count { it.isSystem },
                selectedBytes = chosen.sumOf { it.size },
                privileged = shizukuReady || rootReady
            )
        }
    }

    private fun removeApp(packageName: String) {
        all = all.filter { it.packageName != packageName }
        selected.remove(packageName)
        publish()
    }

    // ---- uninstalling ---------------------------------------------------------------------------

    /** Order of preference: Shizuku, root, then Android's own dialog per app. */
    fun uninstallSelected() {
        if (uninstallJob?.isActive == true || selected.isEmpty()) return
        // This app goes last, or the batch would die half way through.
        val targets = all.filter { it.packageName in selected }.sortedBy { it.packageName == BuildConfig.APPLICATION_ID }
        cancelRequested = false
        uninstallJob = viewModelScope.launch {
            val backend: UninstallBackend? = DemoUninstaller // nothing is really uninstalled
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
                    failed++ // stays selected so it is easy to retry
                }
            }
        } finally {
            _state.update { it.copy(progress = null) }
            if (backend === ShizukuUninstaller) ShizukuUninstaller.release()
        }
        say(
            when {
                cancelRequested -> app.getString(R.string.uninstall_cancelled, ok)
                failed == 0 -> app.getString(R.string.uninstall_complete)
                else -> app.getString(R.string.uninstall_summary, ok, failed)
            }
        )
    }

    // Fallback without Shizuku or root: one Android dialog per user app.
    private fun startPrompts(targets: List<AppInfo>) {
        val (system, user) = targets.partition { it.isSystem }
        if (system.isNotEmpty()) say(app.getString(R.string.system_needs_access))
        promptQueue.clear()
        promptQueue.addAll(user.map { it.packageName })
        nextPrompt()
    }

    private fun nextPrompt() {
        currentPrompt = promptQueue.removeFirstOrNull()
        currentPrompt?.let { eventChannel.trySend(UiEvent.PromptUninstall(it)) }
    }

    /** The UI calls this when Android's uninstall dialog for the current app has closed. */
    fun onPromptResult() {
        val pkg = currentPrompt ?: return
        if (!PackageUtils.isInstalled(app, pkg)) removeApp(pkg)
        nextPrompt()
        if (currentPrompt == null) say(app.getString(R.string.uninstall_complete))
    }

    private fun say(text: String) {
        eventChannel.trySend(UiEvent.Message(text))
    }

    override fun onCleared() {
        ShizukuUninstaller.release()
    }

    private companion object {
        /** How many of the newest apps are read first so the list can appear immediately. */
        const val FIRST_BATCH = 24
    }
}
