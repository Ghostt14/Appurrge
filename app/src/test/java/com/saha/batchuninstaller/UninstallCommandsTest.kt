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

import com.saha.batchuninstaller.utils.UninstallCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UninstallCommandsTest {
    @Test
    fun userAppIsFullyUninstalled() {
        assertEquals("pm uninstall com.example.app", UninstallCommands.shellCommand("com.example.app", false))
    }

    @Test
    fun systemAppIsOnlyRemovedForCurrentUser() {
        assertEquals(
            "pm uninstall -k --user 0 com.android.chrome",
            UninstallCommands.shellCommand("com.android.chrome", true)
        )
    }

    @Test
    fun commandNeverTouchesSystemFiles() {
        val cmd = UninstallCommands.shellCommand("com.example.app", true)
        assertFalse(cmd.contains("rm "))
        assertFalse(cmd.contains("/system"))
    }

    @Test
    fun rejectsPackageNamesThatCouldInjectShellCommands() {
        val bad = listOf("", "a b", "app; reboot", "app$(id)", "app&&id", "../x", "app\nid")
        for (name in bad) {
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
        assertTrue(UninstallCommands.isSuccess(0, "Success"))
        assertTrue(UninstallCommands.isSuccess(0, "Success\n"))
        assertFalse(UninstallCommands.isSuccess(1, "Failure [DELETE_FAILED_INTERNAL_ERROR]"))
        assertFalse(UninstallCommands.isSuccess(0, "Failure [DELETE_FAILED_DEVICE_POLICY_MANAGER]"))
        assertFalse(UninstallCommands.isSuccess(1, "Success"))
    }
}
