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

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.saha.batchuninstaller.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File

object PackageUtils {
    private const val WORKERS = 4

    /**
     * Loads every installed app with a single PackageManager query (the old code made four
     * separate queries per app, plus an icon bitmap, all on the main thread). Label lookups are
     * the slow part, so they are spread over a few threads. Icons are NOT loaded here.
     */
    suspend fun loadApps(context: Context): List<AppInfo> = withContext(Dispatchers.Default) {
        val pm = context.applicationContext.packageManager
        val packages = pm.getInstalledPackages(0)
        if (packages.isEmpty()) return@withContext emptyList<AppInfo>()
        val chunkSize = (packages.size + WORKERS - 1) / WORKERS
        packages.chunked(chunkSize)
            .map { chunk -> async { chunk.mapNotNull { toAppInfo(pm, it) } } }
            .awaitAll()
            .flatten()
    }

    private fun toAppInfo(pm: PackageManager, pi: PackageInfo): AppInfo? {
        val ai = pi.applicationInfo ?: return null
        val label = try {
            pm.getApplicationLabel(ai).toString()
        } catch (e: Exception) {
            ""
        }
        return AppInfo(
            packageName = pi.packageName,
            appName = label.ifBlank { pi.packageName },
            size = apkSize(ai),
            installTime = pi.firstInstallTime,
            isSystem = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0
        )
    }

    /** Base APK plus any split APKs. */
    private fun apkSize(ai: ApplicationInfo): Long {
        var total = ai.publicSourceDir?.let { File(it).length() } ?: 0L
        ai.splitPublicSourceDirs?.forEach { total += File(it).length() }
        return total
    }

    /** False once the app has been removed for the current user. */
    fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
