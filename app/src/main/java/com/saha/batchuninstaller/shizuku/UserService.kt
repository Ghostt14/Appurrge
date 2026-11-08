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
package com.saha.batchuninstaller.shizuku

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import com.saha.batchuninstaller.utils.UninstallCommands
import kotlin.system.exitProcess

/**
 * Started by Shizuku in its own privileged process (shell or root). Everything in here therefore
 * runs with that privilege, not with this app's. Shizuku instantiates it reflectively, which is
 * why both constructors exist and are kept by ProGuard.
 */
class UserService() : IUserService.Stub() {

    @Keep
    constructor(@Suppress("UNUSED_PARAMETER") context: Context) : this()

    override fun destroy() {
        exitProcess(0)
    }

    override fun exit() {
        destroy()
    }

    override fun uninstall(packageName: String, systemApp: Boolean): Boolean = try {
        val command = UninstallCommands.shellCommand(packageName, systemApp)
        // pm is a shell script that needs /system/bin on PATH, which is not guaranteed here.
        val process = ProcessBuilder("sh", "-c", "export PATH=/system/bin:/system/xbin:\$PATH; $command")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val ok = UninstallCommands.isSuccess(process.waitFor(), output)
        if (!ok) Log.w(TAG, "uninstall $packageName failed: ${output.trim()}")
        ok
    } catch (e: Exception) {
        Log.e(TAG, "uninstall $packageName crashed", e)
        false
    }

    private companion object {
        const val TAG = "UserService"
    }
}
