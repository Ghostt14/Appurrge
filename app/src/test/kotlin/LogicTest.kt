// Appurrge, GPL-3.0-or-later. Based on Batch Uninstaller by Sarbajit Saha.
package com.appurrge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
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

    private fun names(tab: AppTab = AppTab.USER, query: String = "", sort: SortMode = SortMode.NAME_ASC) =
        AppListLogic.visible(apps, tab, query, sort).map { it.appName }

    @Test
    fun tabsSplitUserAndSystemApps() {
        assertEquals(listOf("alpha", "Middle", "Zeta"), names(AppTab.USER))
        assertEquals(listOf("Maps", "System UI"), names(AppTab.SYSTEM))
    }

    @Test
    fun sorting() {
        assertEquals(listOf("alpha", "Middle", "Zeta"), names(sort = SortMode.NAME_ASC))
        assertEquals(listOf("Zeta", "Middle", "alpha"), names(sort = SortMode.NAME_DESC))
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
    fun equalValuesFallBackToPackageNameSoOrderIsStable() {
        val twins = listOf(AppInfo("com.b", "Same", mb, now, false), AppInfo("com.a", "Same", mb, now, false))
        val sorted = AppListLogic.visible(twins, AppTab.USER, "", SortMode.SIZE_DESC)
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
    }

    @Test
    fun userAppIsFullyUninstalledSystemAppOnlyForCurrentUser() {
        assertEquals("pm uninstall com.example.app", UninstallCommands.shellCommand("com.example.app", false))
        assertEquals("pm uninstall -k --user 0 com.android.chrome", UninstallCommands.shellCommand("com.android.chrome", true))
        assertFalse(UninstallCommands.shellCommand("com.example.app", true).contains("rm "))
    }

    @Test
    fun rejectsPackageNamesThatCouldInjectShellCommands() {
        for (name in listOf("", "a b", "app; reboot", "app\$(id)", "app&&id", "../x", "app\nid")) {
            var rejected = false
            try {
                UninstallCommands.shellCommand(name, false)
            } catch (e: IllegalArgumentException) {
                rejected = true
            }
            assertTrue("should reject '$name'", rejected)
        }
    }

    @Test
    fun successNeedsExitCodeZeroAndSuccessOutput() {
        assertTrue(UninstallCommands.isSuccess(0, "Success\n"))
        assertFalse(UninstallCommands.isSuccess(1, "Failure [DELETE_FAILED_INTERNAL_ERROR]"))
        assertFalse(UninstallCommands.isSuccess(0, "Failure [DELETE_FAILED_DEVICE_POLICY_MANAGER]"))
        assertFalse(UninstallCommands.isSuccess(1, "Success"))
    }

    @Test
    fun scrollThumbFollowsScrollPositionAndHidesWhenListFits() {
        // 1000px of content, 400px visible, 400px track, 50px thumb
        assertEquals(0f, ScrollMath.thumbTop(0, 1000, 400, 400, 50))
        assertEquals(350f, ScrollMath.thumbTop(600, 1000, 400, 400, 50))
        assertEquals(175f, ScrollMath.thumbTop(300, 1000, 400, 400, 50))
        assertEquals(350f, ScrollMath.thumbTop(9999, 1000, 400, 400, 50)) // overscroll is clamped
        assertEquals(null, ScrollMath.thumbTop(0, 400, 400, 400, 50))      // everything fits: no thumb
    }

    @Test
    fun draggingMapsFingerToListPosition() {
        assertEquals(0f, ScrollMath.fraction(0f, 400, 50))      // above the track: clamped to the top
        assertEquals(1f, ScrollMath.fraction(900f, 400, 50))    // below it: clamped to the end
        assertEquals(0.5f, ScrollMath.fraction(200f, 400, 50))  // thumb centred on the finger
        assertEquals(0, ScrollMath.position(0f, 100))
        assertEquals(99, ScrollMath.position(1f, 100))
        assertEquals(0, ScrollMath.position(0.7f, 0))           // empty list
        assertEquals(175f, ScrollMath.thumbTopFor(0.5f, 400, 50))
    }

    @Test
    fun rowSizeLevelsAreClampedAndDefaultIsMedium() {
        assertEquals(1f, ListSize.scale(ListSize.DEFAULT))
        assertEquals(0.8f, ListSize.scale(-5))
        assertEquals(1.3f, ListSize.scale(99))
        assertEquals(4, ListSize.clamp(99))
        assertTrue(ListSize.SCALES.toList() == ListSize.SCALES.sorted())
    }
}
