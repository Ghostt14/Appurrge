// Appurrge, GPL-3.0-only. Based on Batch Uninstaller by Sarbajit Saha.
package com.appurrge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoDataTest {
    private val apps = DemoData.apps(now = 1_800_000_000_000L)

    @Test
    fun hasFortyAppsSplitBetweenUserAndSystem() {
        assertEquals(40, apps.size)
        assertEquals(30, apps.count { !it.isSystem })
        assertEquals(10, apps.count { it.isSystem })
    }

    @Test
    fun packageNamesAreUniqueAndSafeForTheUninstallCommand() {
        assertEquals(40, apps.map { it.packageName }.toSet().size)
        apps.forEach { UninstallCommands.shellCommand(it.packageName, it.isSystem) } // would throw on a bad name
    }

    @Test
    fun namesDatesAndSizesAreVaried() {
        assertEquals(40, apps.map { it.appName }.toSet().size)
        assertTrue(apps.filter { !it.isSystem }.map { it.appName.first() }.toSet().size >= 26)
        assertEquals(40, apps.map { it.installTime }.toSet().size) // every app has its own date
        assertTrue(apps.minOf { it.size } < 2L * 1024 * 1024)
        assertTrue(apps.maxOf { it.size } > 300L * 1024 * 1024)
        assertEquals("Aurora Notes", DemoData.nameOf("demo.aurora.notes"))
    }
}
