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

import android.util.Log
import com.saha.batchuninstaller.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Root back-end. Talks to `su` directly instead of the old RootTools/RootShell AARs. System apps
 * are removed with `pm uninstall --user 0` only; the old code also ran `rm -rf` on the app's
 * /system and data directories, which could leave a phone unable to boot.
 */
object RootUtils : UninstallBackend {
    private const val TAG = "RootUtils"

    /** True if `su` exists and the user granted it. May block while the root manager asks. */
    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val (code, output) = runSu("id")
            code == 0 && output.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun uninstall(app: AppInfo): Boolean = withContext(Dispatchers.IO) {
        try {
            val (code, output) = runSu(UninstallCommands.shellCommand(app.packageName, app.isSystem))
            val ok = UninstallCommands.isSuccess(code, output)
            if (!ok) Log.w(TAG, "uninstall ${app.packageName} failed: ${output.trim()}")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "uninstall ${app.packageName} crashed", e)
            false
        }
    }

    private fun runSu(command: String): Pair<Int, String> {
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        return process.waitFor() to output
    }
}
