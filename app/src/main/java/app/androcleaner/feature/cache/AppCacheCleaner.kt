package app.androcleaner.feature.cache

import android.content.Context
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
import app.androcleaner.feature.apps.AppInfo
import app.androcleaner.feature.apps.AppsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface CacheCleanState {
    data object Idle : CacheCleanState
    data class Running(val appLabel: String, val index: Int, val total: Int) : CacheCleanState
    data class Finished(val freedBytes: Long, val cleanedApps: Int, val failedApps: Int) : CacheCleanState
}

/**
 * Clears app caches by driving the system Settings screens through [CacheCleanerService]:
 * App info → Storage & cache → Clear cache, for each app in turn.
 */
@Singleton
class AppCacheCleaner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apps: AppsRepository,
) {
    // Node lookups are blocking IPC calls, so the whole loop runs off the main thread.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _state = MutableStateFlow<CacheCleanState>(CacheCleanState.Idle)
    val state: StateFlow<CacheCleanState> = _state.asStateFlow()

    /** Set by the service while it is connected. */
    internal var service: CacheCleanerService? = null

    val isServiceEnabled: Boolean
        get() = service != null || isEnabledInSettings()

    fun start(targets: List<AppInfo>) {
        val svc = service
        if (svc == null || targets.isEmpty() || job?.isActive == true) return
        job = scope.launch {
            val labels = SettingsLabels.resolve(context)
            var cleaned = 0
            var failed = 0
            withContext(Dispatchers.Main) { svc.showOverlay(onStop = ::cancel) }
            try {
                for ((i, app) in targets.withIndex()) {
                    if (!isActive) break
                    _state.value = CacheCleanState.Running(app.label, i + 1, targets.size)
                    withContext(Dispatchers.Main) { svc.updateOverlay(app.label, i + 1, targets.size) }
                    if (svc.clearCacheOf(app.packageName, labels)) cleaned++ else failed++
                }
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    svc.hideOverlay()
                    svc.returnToApp()
                }
            }
            delay(SIZE_SETTLE_MS)
            val freed = targets.sumOf { app ->
                val before = app.sizes?.cacheBytes ?: 0L
                val after = apps.cacheBytes(app.packageName) ?: before
                (before - after).coerceAtLeast(0)
            }
            _state.value = CacheCleanState.Finished(freed, cleaned, failed)
        }
    }

    fun cancel() {
        job?.cancel()
    }

    fun consumeResult() {
        if (_state.value is CacheCleanState.Finished) _state.value = CacheCleanState.Idle
    }

    private fun isEnabledInSettings(): Boolean =
        context.getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == context.packageName }

    private companion object {
        const val SIZE_SETTLE_MS = 600L
    }
}
