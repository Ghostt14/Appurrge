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
package com.saha.batchuninstaller.utils

import com.saha.batchuninstaller.AppInfo
import java.text.Collator
import java.util.Comparator

enum class AppTab { USER, SYSTEM }

/** Order must match the R.array.sort string array. */
enum class SortMode { NAME_ASC, NAME_DESC, SIZE_ASC, SIZE_DESC, DATE_ASC, DATE_DESC }

/** Order must match the R.array.filter string array. */
enum class AppFilter { ANY, LARGER_50_MB, LARGER_100_MB, INSTALLED_LAST_30_DAYS, INSTALLED_OVER_1_YEAR }

/** Pure functions (no Android dependencies) that decide what the list shows. */
object AppListLogic {
    private const val MB = 1024L * 1024L
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    /** Tab, then search query, then filter, then sort. */
    fun visible(
        all: List<AppInfo>,
        tab: AppTab,
        query: String,
        filter: AppFilter,
        sort: SortMode,
        nowMillis: Long
    ): List<AppInfo> {
        val q = query.trim()
        return all.asSequence()
            .filter { (tab == AppTab.SYSTEM) == it.isSystem }
            .filter { q.isEmpty() || matches(it, q) }
            .filter { passes(it, filter, nowMillis) }
            .toList()
            .sortedWith(comparator(sort))
    }

    fun matches(app: AppInfo, query: String): Boolean =
        app.appName.contains(query, ignoreCase = true) || app.packageName.contains(query, ignoreCase = true)

    fun passes(app: AppInfo, filter: AppFilter, nowMillis: Long): Boolean = when (filter) {
        AppFilter.ANY -> true
        AppFilter.LARGER_50_MB -> app.size >= 50 * MB
        AppFilter.LARGER_100_MB -> app.size >= 100 * MB
        AppFilter.INSTALLED_LAST_30_DAYS -> app.installTime >= nowMillis - 30 * DAY_MS
        AppFilter.INSTALLED_OVER_1_YEAR -> app.installTime <= nowMillis - 365 * DAY_MS
    }

    fun comparator(sort: SortMode): Comparator<AppInfo> {
        // Collator gives sensible ordering for accented / non-Latin names. Not thread-safe, so one
        // instance per comparator (the list is only ever sorted on one thread at a time).
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        // Written out by hand on purpose: Comparator.reversed()/thenComparing() are API 24+ and the
        // minSdk is 23.
        return Comparator { a, b ->
            val primary = when (sort) {
                SortMode.NAME_ASC -> collator.compare(a.appName, b.appName)
                SortMode.NAME_DESC -> collator.compare(b.appName, a.appName)
                SortMode.SIZE_ASC -> a.size.compareTo(b.size)
                SortMode.SIZE_DESC -> b.size.compareTo(a.size)
                SortMode.DATE_ASC -> a.installTime.compareTo(b.installTime)
                SortMode.DATE_DESC -> b.installTime.compareTo(a.installTime)
            }
            // Package name as the final tie-breaker keeps the order stable between refreshes.
            if (primary != 0) primary else a.packageName.compareTo(b.packageName)
        }
    }

    /**
     * Text shown next to the fast-scroll thumb: the first letter when sorting by name, the month
     * when sorting by date and the size when sorting by size.
     */
    fun popupLabel(
        app: AppInfo,
        sort: SortMode,
        monthLabel: (Long) -> String,
        sizeLabel: (Long) -> String
    ): String = when (sort) {
        SortMode.NAME_ASC, SortMode.NAME_DESC -> firstLetter(app.appName)
        SortMode.DATE_ASC, SortMode.DATE_DESC -> monthLabel(app.installTime)
        SortMode.SIZE_ASC, SortMode.SIZE_DESC -> sizeLabel(app.size)
    }

    fun firstLetter(name: String): String {
        val c = name.trim().firstOrNull() ?: return "#"
        return if (c.isLetter()) c.uppercaseChar().toString() else "#"
    }
}
