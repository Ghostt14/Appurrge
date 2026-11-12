// Appurrge, GPL-3.0-only. Based on Batch Uninstaller by Sarbajit Saha.
package com.appurrge

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import java.io.File

/**
 * Loading is split so something can be shown fast: [installedPackages] is one cheap query that already
 * carries install times, and [toApps] does the slow label lookups. The caller converts the newest few first
 * (what the default sort shows) and the rest afterwards. The finished list is also cached on disk, so the
 * next launch can show the list before PackageManager has answered.
 *
 * Everything here blocks, so call it from a background thread.
 */
object PackageUtils {
    private const val WORKERS = 4
    private const val CACHE_FILE = "apps.cache"

    /** All packages, newest install first. */
    fun installedPackages(context: Context): List<PackageInfo> =
        context.packageManager.getInstalledPackages(0).sortedByDescending { it.firstInstallTime }

    /** Builds [AppInfo]s with the work spread over a few threads; keeps the order. */
    fun toApps(context: Context, packages: List<PackageInfo>): List<AppInfo> {
        if (packages.isEmpty()) return emptyList()
        val pm = context.packageManager
        val chunkSize = (packages.size + WORKERS - 1) / WORKERS
        val tasks = packages.chunked(chunkSize).map { chunk -> { chunk.mapNotNull { toApp(pm, it) } } }
        return Work.parallel(tasks).flatten()
    }

    private fun toApp(pm: PackageManager, pi: PackageInfo): AppInfo? {
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

    /** Base APK plus split APKs. */
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

    // ---- disk cache: one tab-separated line per app --------------------------------------------

    fun readCache(context: Context): List<AppInfo> = try {
        File(context.filesDir, CACHE_FILE).useLines { lines -> lines.mapNotNull { parseLine(it) }.toList() }
    } catch (e: Exception) {
        emptyList()
    }

    fun writeCache(context: Context, apps: List<AppInfo>) {
        try {
            val tmp = File(context.filesDir, "$CACHE_FILE.tmp")
            tmp.writeText(apps.joinToString("\n") {
                val name = it.appName.replace('\t', ' ').replace('\n', ' ')
                "${it.packageName}\t$name\t${it.size}\t${it.installTime}\t${if (it.isSystem) 1 else 0}"
            })
            tmp.renameTo(File(context.filesDir, CACHE_FILE))
        } catch (e: Exception) {
            // The cache is only a speed-up; ignore failures.
        }
    }

    private fun parseLine(line: String): AppInfo? {
        val p = line.split('\t')
        if (p.size != 5) return null
        return try {
            AppInfo(p[0], p[1], p[2].toLong(), p[3].toLong(), p[4] == "1")
        } catch (e: NumberFormatException) {
            null
        }
    }
}

/** Loads icons only for rows on screen and keeps a small memory cache. */
class IconLoader(private val pm: PackageManager, private val sizePx: Int) {
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun cached(packageName: String): Bitmap? = cache.get(packageName)

    /**
     * Loads the icon on a worker thread and hands it to [onLoaded] on the main thread. [wanted] is asked
     * before the work starts and again before delivering, so a row that was reused for another app in the
     * meantime neither does the work nor shows the wrong icon.
     */
    fun load(packageName: String, wanted: () -> Boolean, onLoaded: (Bitmap) -> Unit) {
        Work.background {
            if (wanted()) {
                val bitmap = cache.get(packageName) ?: try {
                    pm.getApplicationIcon(packageName).toBitmap(sizePx, sizePx).also { cache.put(packageName, it) }
                } catch (e: Exception) {
                    null
                }
                if (bitmap != null) Work.ui { if (wanted()) onLoaded(bitmap) }
            }
        }
    }
}
