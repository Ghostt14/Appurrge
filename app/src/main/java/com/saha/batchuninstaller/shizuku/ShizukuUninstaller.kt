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

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import com.saha.batchuninstaller.AppInfo
import com.saha.batchuninstaller.BuildConfig
import com.saha.batchuninstaller.utils.UninstallBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

/** Uninstalls apps through a Shizuku "user service" (a process running as shell or root). */
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

    /** Stops the privileged process once a batch is finished. */
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
                            val svc = if (binder != null && binder.pingBinder()) {
                                IUserService.Stub.asInterface(binder)
                            } else {
                                null
                            }
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
