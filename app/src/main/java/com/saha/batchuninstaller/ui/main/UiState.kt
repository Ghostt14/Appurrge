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

import com.saha.batchuninstaller.AppInfo
import com.saha.batchuninstaller.utils.AppFilter
import com.saha.batchuninstaller.utils.AppTab
import com.saha.batchuninstaller.utils.SortMode

/** One visible list row. Selection lives here (not in [AppInfo]) so it survives refreshes. */
data class Row(val app: AppInfo, val selected: Boolean)

data class UninstallProgress(
    val done: Int,
    val total: Int,
    val current: String,
    val cancelling: Boolean = false
)

data class UiState(
    val rows: List<Row> = emptyList(),
    /** True only for the very first load, when there is nothing to show yet. */
    val loading: Boolean = true,
    /** True while a pull-to-refresh is running. */
    val refreshing: Boolean = false,
    val tab: AppTab = AppTab.USER,
    val sort: SortMode = SortMode.DATE_DESC,
    val filter: AppFilter = AppFilter.ANY,
    val query: String = "",
    val userCount: Int = 0,
    val systemCount: Int = 0,
    val selectedCount: Int = 0,
    val selectedSystemCount: Int = 0,
    val selectedBytes: Long = 0,
    /** Bumped whenever the list should jump back to the top (tab, sort, filter or query changed). */
    val scrollToTopToken: Int = 0,
    val progress: UninstallProgress? = null
)

/** One-shot things the UI must do exactly once. */
sealed class UiEvent {
    data class Message(val text: String) : UiEvent()

    /** Show Android's own uninstall dialog for this package (used when there is no Shizuku/root). */
    data class PromptUninstall(val packageName: String) : UiEvent()
}
