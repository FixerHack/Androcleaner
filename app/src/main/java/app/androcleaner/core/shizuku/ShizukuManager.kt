package app.androcleaner.core.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import app.androcleaner.BuildConfig
import app.androcleaner.IShellService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

enum class ShizukuStatus { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, READY }

/**
 * Optional Shizuku integration: runs `pm` commands as the shell user, which may clear
 * other apps' caches without root and without an accessibility service.
 */
@Singleton
class ShizukuManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val _status = MutableStateFlow(ShizukuStatus.NOT_INSTALLED)
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    private var shell: IShellService? = null

    init {
        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener {
            shell = null
            refresh()
        }
        Shizuku.addRequestPermissionResultListener { _, _ -> refresh() }
        refresh()
    }

    val isReady: Boolean get() = status.value == ShizukuStatus.READY

    fun refresh() {
        _status.value = when {
            !isInstalled() -> ShizukuStatus.NOT_INSTALLED
            !runCatching { Shizuku.pingBinder() && !Shizuku.isPreV11() }.getOrDefault(false) -> ShizukuStatus.NOT_RUNNING
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuStatus.READY
            else -> ShizukuStatus.NO_PERMISSION
        }
    }

    fun requestPermission() {
        if (status.value == ShizukuStatus.NO_PERMISSION) runCatching { Shizuku.requestPermission(REQUEST_CODE) }
    }

    fun openShizukuApp() {
        context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
            ?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.let(context::startActivity)
    }

    /** Frees the cache of every app (internal and external). */
    suspend fun clearAllCaches(): Boolean = exec("pm trim-caches 999999999999999")?.contains("Error", ignoreCase = true) == false

    /**
     * Clears one app's cache. `pm clear --cache-only` isn't available on every Android build,
     * so callers fall back to [clearAllCaches] when this returns false.
     */
    suspend fun clearCache(packageName: String): Boolean =
        exec("pm clear --cache-only $packageName")?.contains("Success") == true

    suspend fun exec(command: String): String? = withContext(Dispatchers.IO) {
        if (!isReady) return@withContext null
        val service = shell ?: bind() ?: return@withContext null
        runCatching { service.exec(command) }.getOrNull()
    }

    private suspend fun bind(): IShellService? = withTimeoutOrNull(BIND_TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, ShellService::class.java.name))
                .daemon(false)
                .processNameSuffix("shell")
                .debuggable(BuildConfig.DEBUG)
                .version(BuildConfig.VERSION_CODE)
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    val service = binder?.takeIf { it.pingBinder() }?.let(IShellService.Stub::asInterface)
                    shell = service
                    if (cont.isActive) cont.resume(service)
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    shell = null
                }
            }
            runCatching { Shizuku.bindUserService(args, connection) }
                .onFailure { if (cont.isActive) cont.resume(null) }
        }
    }

    private fun isInstalled(): Boolean =
        runCatching { context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0) }.isSuccess

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val DOWNLOAD_URL = "https://shizuku.rikka.app/download/"
        private const val REQUEST_CODE = 4242
        private const val BIND_TIMEOUT_MS = 10_000L
    }
}
