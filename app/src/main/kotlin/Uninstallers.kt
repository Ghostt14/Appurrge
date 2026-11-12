// Appurrge, GPL-3.0-only. Based on Batch Uninstaller by Sarbajit Saha.
package com.appurrge

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.annotation.Keep
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/** A way of removing one app silently. */
interface UninstallBackend {
    /** True if the app is gone. Blocks, so call it from a background thread. Must not throw. */
    fun uninstall(app: AppInfo): Boolean
}

/** Root back-end: talks to su directly. Runs only "pm uninstall"; never deletes files under /system. */
object RootUtils : UninstallBackend {
    private const val TAG = "RootUtils"

    /** True if su exists and was granted. May block while the root manager asks. */
    fun isRootAvailable(): Boolean = try {
        val (code, output) = runSu("id")
        code == 0 && output.contains("uid=0")
    } catch (e: Exception) {
        false
    }

    override fun uninstall(app: AppInfo): Boolean = try {
        val (code, output) = runSu(UninstallCommands.shellCommand(app.packageName, app.isSystem))
        UninstallCommands.isSuccess(code, output).also {
            if (!it) Log.w(TAG, "${app.packageName}: ${output.trim()}")
        }
    } catch (e: Exception) {
        Log.e(TAG, app.packageName, e)
        false
    }

    private fun runSu(command: String): Pair<Int, String> {
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        return process.waitFor() to output
    }
}

/** Shizuku's state, without ever throwing: it may be missing, stopped or restarting. */
object ShizukuStatus {
    const val REQUEST_CODE = 1001

    fun running(): Boolean = try {
        Shizuku.pingBinder() && !Shizuku.isPreV11()
    } catch (e: Throwable) {
        false
    }

    fun permitted(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
        false
    }

    /** False once the user chose "deny and don't ask again". */
    fun canAsk(): Boolean = try {
        !Shizuku.shouldShowRequestPermissionRationale()
    } catch (e: Throwable) {
        false
    }

    fun ready() = running() && permitted()

    fun requestPermission() {
        try {
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (e: Throwable) {
            // Shizuku went away between the check and now; the user can simply tap again.
        }
    }
}

/** Shizuku back-end: runs the command in a helper process that Shizuku starts as shell or root. */
object ShizukuUninstaller : UninstallBackend {
    private const val BIND_TIMEOUT_SECONDS = 8L

    private val args by lazy {
        Shizuku.UserServiceArgs(ComponentName(BuildConfig.APPLICATION_ID, UserService::class.java.name))
            .daemon(false)
            .processNameSuffix("uninstall")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    @Volatile
    private var service: IUserService? = null
    private var connection: ServiceConnection? = null

    override fun uninstall(app: AppInfo): Boolean {
        val svc = connect() ?: return false
        return try {
            svc.uninstall(app.packageName, app.isSystem)
        } catch (e: Exception) {
            false
        }
    }

    /** Stops the helper process once a batch is done. */
    @Synchronized
    fun release() {
        val conn = connection ?: return
        try {
            Shizuku.unbindUserService(args, conn, true)
        } catch (e: Throwable) {
            // already gone
        }
        connection = null
        service = null
    }

    /** Starts the helper and waits for it (Shizuku answers on the main thread, so never call this from there). */
    @Synchronized
    private fun connect(): IUserService? {
        val existing = service
        if (existing != null && existing.asBinder().pingBinder()) return existing
        val connected = CountDownLatch(1)
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = if (binder != null && binder.pingBinder()) IUserService.Stub.asInterface(binder) else null
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }
        connection = conn
        try {
            Shizuku.bindUserService(args, conn)
        } catch (t: Throwable) {
            return null
        }
        if (!connected.await(BIND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            try {
                Shizuku.unbindUserService(args, conn, true)
            } catch (t: Throwable) {
                // nothing to clean up
            }
            return null
        }
        return service
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
