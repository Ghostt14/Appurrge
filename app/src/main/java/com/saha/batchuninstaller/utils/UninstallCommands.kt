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

/** Builds the shell command used by the root and Shizuku back-ends. Pure, so it is unit tested. */
object UninstallCommands {
    private val PACKAGE_NAME = Regex("[A-Za-z0-9_.]+")

    /**
     * User apps are removed completely. System apps cannot be deleted from /system without
     * remounting it, so they are removed for the current user only (`--user 0`, data kept with `-k`).
     * That is reversible (`cmd package install-existing <pkg>`) and, unlike deleting files under
     * /system, cannot leave the phone unable to boot.
     */
    fun shellCommand(packageName: String, systemApp: Boolean): String {
        require(PACKAGE_NAME.matches(packageName)) { "Invalid package name: $packageName" }
        return if (systemApp) "pm uninstall -k --user 0 $packageName" else "pm uninstall $packageName"
    }

    /** `pm uninstall` prints "Success" or "Failure [reason]". */
    fun isSuccess(exitCode: Int, output: String): Boolean =
        exitCode == 0 && output.contains("Success", ignoreCase = true)
}
