// Appurrge, GPL-3.0-or-later. Based on Batch Uninstaller by Sarbajit Saha.
package com.appurrge

/** One installed app. No Context or Bitmap in here, so it is cheap to build, cache and test. */
data class AppInfo(
    val packageName: String,
    val appName: String,
    /** APK size in bytes (base + splits). */
    val size: Long,
    /** First install time, epoch millis. */
    val installTime: Long,
    val isSystem: Boolean
)

enum class AppTab { USER, SYSTEM }

/** Order must match the "sort" array in strings.xml. */
enum class SortMode { NAME_ASC, NAME_DESC, SIZE_ASC, SIZE_DESC, DATE_ASC, DATE_DESC }

/** A visible list row. Selection lives here, keyed by package, so it survives refreshes. */
data class Row(val app: AppInfo, val selected: Boolean)

data class UninstallProgress(val done: Int, val total: Int, val current: String, val cancelling: Boolean = false)

data class UiState(
    val rows: List<Row> = emptyList(),
    /** True until the first rows (cached or freshly loaded) can be shown. */
    val loading: Boolean = true,
    /** True while a pull-to-refresh is running. */
    val refreshing: Boolean = false,
    val tab: AppTab = AppTab.USER,
    val sort: SortMode = SortMode.DATE_DESC,
    val query: String = "",
    val userCount: Int = 0,
    val systemCount: Int = 0,
    val selectedCount: Int = 0,
    val selectedSystemCount: Int = 0,
    val selectedBytes: Long = 0,
    /** Shizuku is permitted or root was granted, so apps (incl. system apps) can be removed silently. */
    val privileged: Boolean = false,
    /** Bumped when the list should jump to the top (tab, sort or query changed). */
    val scrollToTopToken: Int = 0,
    val progress: UninstallProgress? = null
)

/** One-shot things the UI must do exactly once. */
sealed class UiEvent {
    data class Message(val text: String) : UiEvent()
    /** Show Android's own uninstall dialog (used when there is neither Shizuku nor root). */
    data class PromptUninstall(val packageName: String) : UiEvent()
    /** Neither Shizuku nor root is usable: send the user to Shizuku. */
    object OpenShizuku : UiEvent()
}
