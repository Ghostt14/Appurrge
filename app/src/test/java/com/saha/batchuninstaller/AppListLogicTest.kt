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
package com.saha.batchuninstaller

import com.saha.batchuninstaller.utils.AppFilter
import com.saha.batchuninstaller.utils.AppListLogic
import com.saha.batchuninstaller.utils.AppTab
import com.saha.batchuninstaller.utils.SortMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppListLogicTest {
    private val mb = 1024L * 1024L
    private val day = 24L * 60L * 60L * 1000L
    private val now = 1_800_000_000_000L

    private val apps = listOf(
        AppInfo("com.zeta", "Zeta", 10 * mb, now - 2 * day, false),
        AppInfo("com.alpha", "alpha", 120 * mb, now - 400 * day, false),
        AppInfo("com.mid", "Middle", 60 * mb, now - 40 * day, false),
        AppInfo("com.android.sys", "System UI", 30 * mb, now - 900 * day, true),
        AppInfo("com.google.maps", "Maps", 80 * mb, now - 5 * day, true)
    )

    private fun names(
        tab: AppTab = AppTab.USER,
        query: String = "",
        filter: AppFilter = AppFilter.ANY,
        sort: SortMode = SortMode.NAME_ASC
    ) = AppListLogic.visible(apps, tab, query, filter, sort, now).map { it.appName }

    @Test
    fun tabsSplitUserAndSystemApps() {
        assertEquals(listOf("alpha", "Middle", "Zeta"), names(AppTab.USER))
        assertEquals(listOf("Maps", "System UI"), names(AppTab.SYSTEM))
    }

    @Test
    fun nameSortIsCaseInsensitive() {
        assertEquals(listOf("alpha", "Middle", "Zeta"), names(sort = SortMode.NAME_ASC))
        assertEquals(listOf("Zeta", "Middle", "alpha"), names(sort = SortMode.NAME_DESC))
    }

    @Test
    fun sizeAndDateSorts() {
        assertEquals(listOf("Zeta", "Middle", "alpha"), names(sort = SortMode.SIZE_ASC))
        assertEquals(listOf("alpha", "Middle", "Zeta"), names(sort = SortMode.SIZE_DESC))
        assertEquals(listOf("alpha", "Middle", "Zeta"), names(sort = SortMode.DATE_ASC))
        assertEquals(listOf("Zeta", "Middle", "alpha"), names(sort = SortMode.DATE_DESC))
    }

    @Test
    fun searchMatchesNameOrPackageIgnoringCase() {
        assertEquals(listOf("Middle"), names(query = "MID"))
        assertEquals(listOf("Zeta"), names(query = "com.zeta"))
        assertEquals(listOf("Maps"), names(tab = AppTab.SYSTEM, query = "google"))
        assertEquals(listOf("alpha", "Middle", "Zeta"), names(query = "   "))
        assertTrue(names(query = "nothing like this").isEmpty())
    }

    @Test
    fun filters() {
        assertEquals(listOf("alpha", "Middle"), names(filter = AppFilter.LARGER_50_MB))
        assertEquals(listOf("alpha"), names(filter = AppFilter.LARGER_100_MB))
        assertEquals(listOf("Zeta"), names(filter = AppFilter.INSTALLED_LAST_30_DAYS))
        assertEquals(listOf("alpha"), names(filter = AppFilter.INSTALLED_OVER_1_YEAR))
    }

    @Test
    fun equalValuesFallBackToPackageNameSoOrderIsStable() {
        val twins = listOf(
            AppInfo("com.b", "Same", mb, now, false),
            AppInfo("com.a", "Same", mb, now, false)
        )
        val sorted = AppListLogic.visible(twins, AppTab.USER, "", AppFilter.ANY, SortMode.SIZE_DESC, now)
        assertEquals(listOf("com.a", "com.b"), sorted.map { it.packageName })
    }

    @Test
    fun popupLabelDependsOnSortMode() {
        val app = apps[0]
        val month = { _: Long -> "Nov 2026" }
        val size = { bytes: Long -> "${bytes / mb} MB" }
        assertEquals("Z", AppListLogic.popupLabel(app, SortMode.NAME_ASC, month, size))
        assertEquals("Nov 2026", AppListLogic.popupLabel(app, SortMode.DATE_DESC, month, size))
        assertEquals("10 MB", AppListLogic.popupLabel(app, SortMode.SIZE_ASC, month, size))
    }

    @Test
    fun firstLetterHandlesDigitsSymbolsAndBlank() {
        assertEquals("A", AppListLogic.firstLetter("  android"))
        assertEquals("#", AppListLogic.firstLetter("1Password"))
        assertEquals("#", AppListLogic.firstLetter(""))
        assertFalse(AppListLogic.firstLetter("é").isEmpty())
    }
}
