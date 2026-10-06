package app.androcleaner.feature.apps

import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import android.os.storage.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class AppInfo(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val isSystem: Boolean,
    val installedAt: Long,
    /** Null when usage access is not granted. */
    val sizes: AppSizes?,
    /** Null when unknown or never used during the last year. */
    val lastUsedAt: Long?,
)

data class AppSizes(val appBytes: Long, val dataBytes: Long, val cacheBytes: Long) {
    /** dataBytes already includes cache. */
    val totalBytes: Long get() = appBytes + dataBytes
}

@Singleton
class AppsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private val pm: PackageManager get() = context.packageManager

    suspend fun loadApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val storageStats = context.getSystemService(StorageStatsManager::class.java)
        val lastUsed = lastUsedTimes()
        val user = Process.myUserHandle()

        pm.getInstalledPackages(0).mapNotNull { pkg ->
            val appInfo = pkg.applicationInfo ?: return@mapNotNull null
            if (pkg.packageName == context.packageName) return@mapNotNull null
            val sizes = runCatching {
                val s = storageStats.queryStatsForPackage(StorageManager.UUID_DEFAULT, pkg.packageName, user)
                AppSizes(s.appBytes, s.dataBytes, s.cacheBytes)
            }.getOrNull()
            AppInfo(
                packageName = pkg.packageName,
                label = appInfo.loadLabel(pm).toString(),
                versionName = pkg.versionName,
                // Preinstalled apps (even updated ones like Chrome) can't be fully uninstalled.
                isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                installedAt = pkg.firstInstallTime,
                sizes = sizes,
                lastUsedAt = lastUsed[pkg.packageName]?.takeIf { it > 0 },
            )
        }
    }

    /** Current cache size of one app, or null without usage access. */
    fun cacheBytes(packageName: String): Long? = runCatching {
        context.getSystemService(StorageStatsManager::class.java)
            .queryStatsForPackage(StorageManager.UUID_DEFAULT, packageName, Process.myUserHandle())
            .cacheBytes
    }.getOrNull()

    /** Package name -> versionCode for every installed package. */
    fun installedVersions(): Map<String, Long> =
        pm.getInstalledPackages(0).associate { it.packageName to it.longVersionCode }

    private fun lastUsedTimes(): Map<String, Long> = runCatching {
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        usm.queryAndAggregateUsageStats(now - TimeUnit.DAYS.toMillis(365), now)
            .mapValues { it.value.lastTimeUsed }
    }.getOrDefault(emptyMap())
}
