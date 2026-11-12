// Appurrge, GPL-3.0-only. Based on Batch Uninstaller by Sarbajit Saha.
package com.appurrge

import android.content.Context
import rikka.shizuku.Shizuku

/**
 * Everything the screen shows and does, in one place that outlives the Activity (rotation, dark-mode
 * change): the app list, the selection, tab/sort/search and a running uninstall. One per process.
 *
 * The rules that keep it simple:
 *  - it is only ever touched from the main thread;
 *  - slow work runs on other threads (see [Work]) and comes back through [Work.ui];
 *  - the Activity learns about changes through [Listener].
 */
class AppModel private constructor(private val app: Context) {
    interface Listener {
        fun onState(state: UiState)
        fun onEvent(event: UiEvent)
    }

    var state = UiState()
        private set

    /** The screen while it is visible. Setting it sends the current state and anything queued meanwhile. */
    var listener: Listener? = null
        set(value) {
            field = value
            if (value != null) {
                value.onState(state)
                deliverEvents()
            }
        }

    val icons = IconLoader(app.packageManager, (40 * app.resources.displayMetrics.density).toInt())

    private var all: List<AppInfo> = emptyList()
    private val selected = LinkedHashSet<String>()
    private var shizukuReady = false
    private var rootReady = false

    private var loadRunning = false
    private var rootChecking = false
    private var uninstalling = false
    @Volatile
    private var cancelRequested = false
    private var uninstallAfterPermission = false

    private val events = ArrayDeque<UiEvent>()
    private val promptQueue = ArrayDeque<String>()
    private var currentPrompt: String? = null

    init {
        // Registered once for the whole process, so the Activity never has to add or remove them.
        try {
            Shizuku.addBinderReceivedListener { Work.ui { refreshShizuku() } }
            Shizuku.addRequestPermissionResultListener { requestCode, _ ->
                Work.ui { onShizukuPermissionResult(requestCode) }
            }
        } catch (e: Throwable) {
            // Shizuku's classes are not usable here; everything else still works.
        }
    }

    // ---- loading --------------------------------------------------------------------------------

    /** A new launch of the screen: forget the old selection, search and tab, show what is known, refresh. */
    fun start() {
        selected.clear()
        state = UiState(loading = all.isEmpty())
        publish()
        refreshShizuku()
        load(pulled = false)
    }

    /** Pull to refresh. */
    fun refresh() = load(pulled = true)

    /**
     * Fast start: (1) show last launch's list from the disk cache, if any; (2) otherwise show the newest
     * apps as soon as their labels are read; (3) then finish the rest and update the cache.
     */
    private fun load(pulled: Boolean) {
        if (loadRunning) return
        loadRunning = true
        val first = all.isEmpty()
        update { it.copy(loading = first, refreshing = pulled && !first) }
        Work.thread {
            try {
                if (first) {
                    val cached = PackageUtils.readCache(app)
                    if (cached.isNotEmpty()) showFirst(cached)
                }
                val raw = PackageUtils.installedPackages(app)
                val head = PackageUtils.toApps(app, raw.take(FIRST_BATCH))
                showFirst(head)
                val loaded = head + PackageUtils.toApps(app, raw.drop(FIRST_BATCH))
                Work.ui {
                    all = loaded
                    selected.retainAll(loaded.mapTo(HashSet()) { it.packageName }) // selection survives a refresh
                    publish { it.copy(loading = false, refreshing = false) }
                }
                PackageUtils.writeCache(app, loaded)
            } catch (e: Exception) {
                Work.ui {
                    say(app.getString(R.string.load_failed))
                    publish { it.copy(loading = false, refreshing = false) }
                }
            } finally {
                Work.ui { loadRunning = false }
            }
        }
    }

    /** Shows [apps] right away, but only if there is nothing on screen yet. */
    private fun showFirst(apps: List<AppInfo>) {
        Work.ui {
            if (all.isEmpty()) {
                all = apps
                publish { it.copy(loading = false) }
            }
        }
    }

    // ---- list controls --------------------------------------------------------------------------

    fun setTab(tab: AppTab) = change(state.tab != tab) { it.copy(tab = tab) }
    fun setSort(sort: SortMode) = change(state.sort != sort) { it.copy(sort = sort) }
    fun setQuery(query: String) = change(state.query != query) { it.copy(query = query) }

    /** Applies a change that should also scroll the list back to the top. */
    private fun change(changed: Boolean, transform: (UiState) -> UiState) {
        if (changed) publish { transform(it).let { s -> s.copy(scrollToTopToken = s.scrollToTopToken + 1) } }
    }

    fun toggle(packageName: String) {
        if (state.progress != null) return
        if (!selected.add(packageName)) selected.remove(packageName)
        publish()
    }

    fun clearSelection() {
        if (state.progress != null) return
        selected.clear()
        publish()
    }

    /** Recomputes the visible rows and counters from [all], [selected] and the controls, and tells the screen. */
    private fun publish(transform: (UiState) -> UiState = { it }) {
        val base = transform(state)
        val chosen = all.filter { it.packageName in selected }
        state = base.copy(
            rows = AppListLogic.visible(all, base.tab, base.query, base.sort)
                .map { Row(it, it.packageName in selected) },
            userCount = all.count { !it.isSystem },
            systemCount = all.count { it.isSystem },
            selectedCount = chosen.size,
            selectedSystemCount = chosen.count { it.isSystem },
            selectedBytes = chosen.sumOf { it.size },
            privileged = shizukuReady || rootReady
        )
        listener?.onState(state)
    }

    /** Changes the state without recomputing the rows. */
    private fun update(transform: (UiState) -> UiState) {
        state = transform(state)
        listener?.onState(state)
    }

    private fun removeApp(packageName: String) {
        all = all.filter { it.packageName != packageName }
        selected.remove(packageName)
        publish()
    }

    // ---- Shizuku and root -----------------------------------------------------------------------

    fun refreshShizuku() = setShizukuReady(ShizukuStatus.ready())

    private fun setShizukuReady(ready: Boolean) {
        if (shizukuReady == ready) return
        shizukuReady = ready
        publish()
    }

    private fun onShizukuPermissionResult(requestCode: Int) {
        if (requestCode != ShizukuStatus.REQUEST_CODE) return
        refreshShizuku()
        if (uninstallAfterPermission) {
            uninstallAfterPermission = false
            uninstallSelected() // uses Shizuku if allowed, otherwise root or Android's dialogs
        }
    }

    /** "ROOT ACCESS" pressed: ask Shizuku for permission, else try root, else send the user to Shizuku. */
    fun connectPrivileged() {
        refreshShizuku()
        if (shizukuReady || rootReady) return
        if (ShizukuStatus.running()) {
            if (ShizukuStatus.canAsk()) ShizukuStatus.requestPermission() else say(app.getString(R.string.shizuku_denied))
        } else {
            tryRoot()
        }
    }

    private fun tryRoot() {
        if (rootChecking) return
        rootChecking = true
        Work.thread {
            val granted = RootUtils.isRootAvailable()
            Work.ui {
                rootChecking = false
                if (granted) {
                    rootReady = true
                    publish()
                } else {
                    post(UiEvent.OpenShizuku)
                }
            }
        }
    }

    // ---- uninstalling ---------------------------------------------------------------------------

    /** The Uninstall button was confirmed. Asks Shizuku once for silent uninstalling if it is running. */
    fun beginUninstall() {
        refreshShizuku()
        if (!(shizukuReady || rootReady) && ShizukuStatus.running() && ShizukuStatus.canAsk()) {
            uninstallAfterPermission = true
            ShizukuStatus.requestPermission()
        } else {
            uninstallSelected()
        }
    }

    /** Order of preference: Shizuku, root, then Android's own dialog per app. */
    private fun uninstallSelected() {
        if (uninstalling || selected.isEmpty()) return
        // This app goes last, or the batch would die half way through.
        val targets = all.filter { it.packageName in selected }.sortedBy { it.packageName == BuildConfig.APPLICATION_ID }
        uninstalling = true
        cancelRequested = false
        val useShizuku = shizukuReady
        val rootKnown = rootReady
        Work.thread {
            val root = !useShizuku && (rootKnown || RootUtils.isRootAvailable())
            when {
                useShizuku -> runBatch(ShizukuUninstaller, targets)
                root -> runBatch(RootUtils, targets)
                else -> Work.ui {
                    startPrompts(targets)
                    uninstalling = false
                }
            }
        }
    }

    fun cancelUninstall() {
        cancelRequested = true
        update { s -> s.copy(progress = s.progress?.copy(cancelling = true)) }
    }

    /** Runs on its own thread; each step reports back to the main thread. */
    private fun runBatch(backend: UninstallBackend, targets: List<AppInfo>) {
        var ok = 0
        var failed = 0
        try {
            for ((index, target) in targets.withIndex()) {
                if (cancelRequested) break
                Work.ui { update { it.copy(progress = UninstallProgress(index, targets.size, target.appName)) } }
                if (backend.uninstall(target)) {
                    ok++
                    Work.ui { removeApp(target.packageName) }
                } else {
                    failed++ // stays selected so it is easy to retry
                }
            }
        } finally {
            if (backend === ShizukuUninstaller) ShizukuUninstaller.release()
        }
        val removed = ok
        val didFail = failed
        Work.ui {
            update { it.copy(progress = null) }
            uninstalling = false
            say(
                when {
                    cancelRequested -> app.getString(R.string.uninstall_cancelled, removed)
                    didFail == 0 -> app.getString(R.string.uninstall_complete)
                    else -> app.getString(R.string.uninstall_summary, removed, didFail)
                }
            )
        }
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
        currentPrompt?.let { post(UiEvent.PromptUninstall(it)) }
    }

    /** The screen calls this when Android's uninstall dialog for the current app has closed. */
    fun onPromptResult() {
        val pkg = currentPrompt ?: return
        if (!PackageUtils.isInstalled(app, pkg)) removeApp(pkg)
        nextPrompt()
        if (currentPrompt == null) say(app.getString(R.string.uninstall_complete))
    }

    // ---- events for the screen ------------------------------------------------------------------

    private fun say(text: String) = post(UiEvent.Message(text))

    /** Queued until a screen is listening, so nothing is lost while the Activity is away or being recreated. */
    private fun post(event: UiEvent) {
        events.addLast(event)
        deliverEvents()
    }

    private fun deliverEvents() {
        val screen = listener ?: return
        while (true) screen.onEvent(events.removeFirstOrNull() ?: return)
    }

    companion object {
        /** How many of the newest apps are read first so the list can appear immediately. */
        private const val FIRST_BATCH = 24

        @Volatile
        private var instance: AppModel? = null

        fun get(context: Context): AppModel = instance ?: synchronized(this) {
            instance ?: AppModel(context.applicationContext).also { instance = it }
        }
    }
}
