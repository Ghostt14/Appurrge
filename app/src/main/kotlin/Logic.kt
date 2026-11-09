// Appurrge, GPL-3.0-or-later. Based on Batch Uninstaller by Sarbajit Saha.
package com.appurrge

import java.text.Collator
import java.util.Comparator
import kotlin.math.roundToInt

/** What the list shows. Pure Kotlin, unit tested. */
object AppListLogic {
    private val collator by lazy { Collator.getInstance().apply { strength = Collator.PRIMARY } }

    /** Loads collation data. Call off the main thread at startup so the first list build is not slowed. */
    fun warmUp() {
        collator.compare("a", "b")
    }

    /** Tab, then search, then sort. */
    fun visible(all: List<AppInfo>, tab: AppTab, query: String, sort: SortMode): List<AppInfo> {
        val q = query.trim()
        return all
            .filter { (tab == AppTab.SYSTEM) == it.isSystem && (q.isEmpty() || matches(it, q)) }
            .sortedWith(comparator(sort))
    }

    fun matches(app: AppInfo, query: String): Boolean =
        app.appName.contains(query, ignoreCase = true) || app.packageName.contains(query, ignoreCase = true)

    // Written by hand: Comparator.reversed()/thenComparing() are API 24+ and minSdk is 21.
    fun comparator(sort: SortMode): Comparator<AppInfo> = Comparator { a, b ->
        val primary = when (sort) {
            SortMode.NAME_ASC -> collator.compare(a.appName, b.appName)
            SortMode.NAME_DESC -> collator.compare(b.appName, a.appName)
            SortMode.SIZE_ASC -> a.size.compareTo(b.size)
            SortMode.SIZE_DESC -> b.size.compareTo(a.size)
            SortMode.DATE_ASC -> a.installTime.compareTo(b.installTime)
            SortMode.DATE_DESC -> b.installTime.compareTo(a.installTime)
        }
        // Package name last keeps the order stable between refreshes.
        if (primary != 0) primary else a.packageName.compareTo(b.packageName)
    }

    /** Fast-scroll popup text: a letter (by name), a month (by date) or a size (by size). */
    fun popupLabel(app: AppInfo, sort: SortMode, monthLabel: (Long) -> String, sizeLabel: (Long) -> String): String =
        when (sort) {
            SortMode.NAME_ASC, SortMode.NAME_DESC -> firstLetter(app.appName)
            SortMode.DATE_ASC, SortMode.DATE_DESC -> monthLabel(app.installTime)
            SortMode.SIZE_ASC, SortMode.SIZE_DESC -> sizeLabel(app.size)
        }

    fun firstLetter(name: String): String {
        val c = name.trim().firstOrNull() ?: return "#"
        return if (c.isLetter()) c.uppercaseChar().toString() else "#"
    }
}

/** The shell command for the root and Shizuku back-ends. */
object UninstallCommands {
    private val PACKAGE_NAME = Regex("[A-Za-z0-9_.]+")

    /**
     * User apps are removed completely. System apps are removed for the current user only
     * (--user 0, data kept with -k): reversible with "cmd package install-existing <pkg>" and, unlike
     * deleting files under /system, cannot leave the phone unable to boot.
     */
    fun shellCommand(packageName: String, systemApp: Boolean): String {
        require(PACKAGE_NAME.matches(packageName)) { "Invalid package name: $packageName" }
        return if (systemApp) "pm uninstall -k --user 0 $packageName" else "pm uninstall $packageName"
    }

    /** pm prints "Success" or "Failure [reason]". */
    fun isSuccess(exitCode: Int, output: String): Boolean =
        exitCode == 0 && output.contains("Success", ignoreCase = true)
}

/** Geometry of the scroll thumb. Pure so it can be unit tested. */
object ScrollMath {
    /** Thumb top (relative to the track) for the current scroll position; null if everything fits. */
    fun thumbTop(offset: Int, range: Int, extent: Int, trackHeight: Int, thumbHeight: Int): Float? {
        val scrollable = range - extent
        if (scrollable <= 0 || trackHeight <= thumbHeight) return null
        return offset.coerceIn(0, scrollable).toFloat() / scrollable * (trackHeight - thumbHeight)
    }

    /** 0..1 along the track for a finger at [y] (relative to the track), thumb centred on the finger. */
    fun fraction(y: Float, trackHeight: Int, thumbHeight: Int): Float {
        val room = trackHeight - thumbHeight
        return if (room <= 0) 0f else ((y - thumbHeight / 2f) / room).coerceIn(0f, 1f)
    }

    fun thumbTopFor(fraction: Float, trackHeight: Int, thumbHeight: Int): Float =
        fraction * (trackHeight - thumbHeight)

    fun position(fraction: Float, itemCount: Int): Int =
        if (itemCount <= 0) 0 else (fraction * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
}
