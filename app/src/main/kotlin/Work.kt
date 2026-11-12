// Appurrge, GPL-3.0-only. Based on Batch Uninstaller by Sarbajit Saha.
package com.appurrge

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Callable
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * All the threading the app needs, in one place and without coroutines:
 *  - [ui]: get back to the main thread. Everything that touches the screen or [AppModel] runs there.
 *  - [background] and [parallel]: a small pool for short jobs (app labels, icons).
 *  - [thread]: a thread of its own for long, rare jobs (loading the list, a batch uninstall).
 */
object Work {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pool = ThreadPoolExecutor(4, 4, 10, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>())
        .apply { allowCoreThreadTimeOut(true) }

    /** Runs [block] on the main thread: right away if already there, otherwise as soon as it is free. */
    fun ui(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post { block() }
    }

    fun background(block: () -> Unit) {
        pool.execute { block() }
    }

    /** Runs [tasks] on the pool at the same time and returns their results in order. Not for the main thread. */
    fun <T> parallel(tasks: List<() -> T>): List<T> =
        pool.invokeAll(tasks.map { task -> Callable { task() } }).map { it.get() }

    fun thread(block: () -> Unit) {
        Thread { block() }.start()
    }
}
