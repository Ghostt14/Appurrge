// Appurrge, GPL-3.0-only. Based on Batch Uninstaller by Sarbajit Saha.
package com.appurrge

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.annotation.Keep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume
import kotlin.system.exitProcess

/** A way of removing one app silently. */
interface UninstallBackend {
    /** True if the app is gone. Must not throw. */
    suspend fun uninstall(app: AppInfo): Boolean
}

/** Root back-end: talks to su directly. Runs only "pm uninstall"; never deletes files under /system. */
object RootUtils : UninstallBackend {
    private const val TAG = "RootUtils"

    /** True if su exists and was granted. May block while the root manager asks. */
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
            UninstallCommands.isSuccess(code, output).also {
                if (!it) Log.w(TAG, "${app.packageName}: ${output.trim()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, app.packageName, e)
            false
        }
    }

    private fun runSu(command: String): Pair<Int, String> {
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        return process.waitFor() to output
    }
}

/** Shizuku back-end: runs the command in a helper process that Shizuku starts as shell or root. */
object ShizukuUninstaller : UninstallBackend {
    private const val BIND_TIMEOUT_MS = 8_000L

    private val args by lazy {
        Shizuku.UserServiceArgs(ComponentName(BuildConfig.APPLICATION_ID, UserService::class.java.name))
            .daemon(false)
            .processNameSuffix("uninstall")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }
    private val lock = Mutex()

    @Volatile
    private var service: IUserService? = null
    private var connection: ServiceConnection? = null

    override suspend fun uninstall(app: AppInfo): Boolean {
        val svc = connect() ?: return false
        return try {
            withContext(Dispatchers.IO) { svc.uninstall(app.packageName, app.isSystem) }
        } catch (e: Exception) {
            false
        }
    }

    /** Stops the helper process once a batch is done. */
    fun release() {
        val conn = connection ?: return
        runCatching { Shizuku.unbindUserService(args, conn, true) }
        connection = null
        service = null
    }

    private suspend fun connect(): IUserService? = lock.withLock {
        val existing = service
        if (existing != null && existing.asBinder().pingBinder()) {
            existing
        } else {
            withTimeoutOrNull(BIND_TIMEOUT_MS) {
                suspendCancellableCoroutine<IUserService?> { cont ->
                    val conn = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                            val svc = if (binder != null && binder.pingBinder()) IUserService.Stub.asInterface(binder) else null
                            service = svc
                            if (cont.isActive) cont.resume(svc)
                        }

                        override fun onServiceDisconnected(name: ComponentName?) {
                            service = null
                        }
                    }
                    connection = conn
                    try {
                        Shizuku.bindUserService(args, conn)
                    } catch (t: Throwable) {
                        if (cont.isActive) cont.resume(null)
                    }
                    cont.invokeOnCancellation { runCatching { Shizuku.unbindUserService(args, conn, true) } }
                }
            }
        }
    }
}

/**
 * Created by Shizuku, by name, inside its own privileged process, so everything here runs with that
 * privilege. Both constructors are needed (Shizuku picks one) and are kept by ProGuard.
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
        // pm is a script that needs /system/bin on PATH, which this process may not have.
        val process = ProcessBuilder("sh", "-c", "export PATH=/system/bin:/system/xbin:\$PATH; $command")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        UninstallCommands.isSuccess(process.waitFor(), output).also {
            if (!it) Log.w("UserService", "$packageName: ${output.trim()}")
        }
    } catch (e: Exception) {
        Log.e("UserService", packageName, e)
        false
    }
}
