package app.androcleaner.feature.protection

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import app.androcleaner.core.db.FileHashDao
import app.androcleaner.core.db.FileHashEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Something about an app that deserves the user's attention. */
enum class Finding(val sensitive: Boolean) {
    SIDELOADED(false),
    ACCESSIBILITY(true),
    DEVICE_ADMIN(true),
    NOTIFICATION_LISTENER(true),
    SMS(true),
    OVERLAY(false),
    INSTALLS_APPS(false),
    HIDDEN_ICON(false),
    CAMERA(false),
    MICROPHONE(false),
    LOCATION(false),
    CONTACTS(false),
    CALL_LOG(false),
}

enum class RiskLevel { HIGH, MEDIUM, LOW }

data class AppRisk(
    val packageName: String,
    val label: String,
    val installer: String?,
    val apkPath: String,
    val apkSize: Long,
    val lastUpdate: Long,
    val findings: Set<Finding>,
) {
    /** Sensitive access by an app that didn't come from a store is the classic malware pattern. */
    val level: RiskLevel
        get() = when {
            Finding.SIDELOADED in findings && findings.any { it.sensitive } -> RiskLevel.HIGH
            Finding.SIDELOADED in findings &&
                findings.any { it == Finding.OVERLAY || it == Finding.INSTALLS_APPS || it == Finding.HIDDEN_ICON } -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
}

@Singleton
class SecurityScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hashCache: FileHashDao,
) {
    private val pm: PackageManager get() = context.packageManager

    /** Local audit of every user-installed app; no network. */
    suspend fun auditApps(): List<AppRisk> = withContext(Dispatchers.IO) {
        val accessibility = context.getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .mapTo(HashSet()) { it.resolveInfo.serviceInfo.packageName }
        val admins = context.getSystemService(DevicePolicyManager::class.java).activeAdmins.orEmpty()
            .mapTo(HashSet()) { it.packageName }
        val listeners = NotificationManagerCompat.getEnabledListenerPackages(context)
        val appOps = context.getSystemService(AppOpsManager::class.java)

        pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            .filter { it.applicationInfo != null && it.packageName != context.packageName }
            .filter { it.applicationInfo!!.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .map { pkg ->
                val info = pkg.applicationInfo!!
                val installer = runCatching { pm.getInstallSourceInfo(pkg.packageName).installingPackageName }.getOrNull()
                val findings = buildSet {
                    if (installer !in TRUSTED_INSTALLERS) add(Finding.SIDELOADED)
                    if (pkg.packageName in accessibility) add(Finding.ACCESSIBILITY)
                    if (pkg.packageName in admins) add(Finding.DEVICE_ADMIN)
                    if (pkg.packageName in listeners) add(Finding.NOTIFICATION_LISTENER)
                    if (pm.getLaunchIntentForPackage(pkg.packageName) == null) add(Finding.HIDDEN_ICON)
                    if (appOpAllowed(appOps, OP_OVERLAY, info.uid, pkg.packageName)) add(Finding.OVERLAY)
                    if (appOpAllowed(appOps, OP_INSTALL, info.uid, pkg.packageName)) add(Finding.INSTALLS_APPS)
                    val granted = pkg.grantedPermissions()
                    if (granted.any { it in SMS }) add(Finding.SMS)
                    if (Manifest.permission.CAMERA in granted) add(Finding.CAMERA)
                    if (Manifest.permission.RECORD_AUDIO in granted) add(Finding.MICROPHONE)
                    if (granted.any { it in LOCATION }) add(Finding.LOCATION)
                    if (Manifest.permission.READ_CONTACTS in granted) add(Finding.CONTACTS)
                    if (Manifest.permission.READ_CALL_LOG in granted) add(Finding.CALL_LOG)
                }
                AppRisk(
                    packageName = pkg.packageName,
                    label = info.loadLabel(pm).toString(),
                    installer = installer,
                    apkPath = info.sourceDir,
                    apkSize = File(info.sourceDir).length(),
                    lastUpdate = pkg.lastUpdateTime,
                    findings = findings,
                )
            }
            .sortedWith(compareBy({ it.level }, { it.label.lowercase() }))
    }

    /** SHA-256 only if it is already cached for the current file version. */
    suspend fun sha256Cached(path: String): String? = withContext(Dispatchers.IO) {
        val file = File(path)
        hashCache.get(listOf("sha256:$path")).firstOrNull()
            ?.takeIf { it.size == file.length() && it.modified == file.lastModified() }
            ?.hash
    }

    /** SHA-256 of an APK (base APK for installed apps), cached by size and mtime. */
    suspend fun sha256(path: String): String? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.canRead()) return@withContext null
        val key = "sha256:$path"
        hashCache.get(listOf(key)).firstOrNull()
            ?.takeIf { it.size == file.length() && it.modified == file.lastModified() }
            ?.let { return@withContext it.hash }
        val hash = runCatching {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered(1 shl 18).use { input ->
                val buffer = ByteArray(1 shl 18)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    md.update(buffer, 0, read)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull() ?: return@withContext null
        hashCache.put(listOf(FileHashEntity(key, file.length(), file.lastModified(), hash)))
        hash
    }

    private fun PackageInfo.grantedPermissions(): Set<String> {
        val names = requestedPermissions ?: return emptySet()
        val flags = requestedPermissionsFlags ?: return emptySet()
        return names.indices
            .filter { flags[it] and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0 }
            .mapTo(HashSet()) { names[it] }
    }

    @Suppress("DEPRECATION")
    private fun appOpAllowed(appOps: AppOpsManager, op: String, uid: Int, pkg: String): Boolean =
        runCatching { appOps.unsafeCheckOpNoThrow(op, uid, pkg) == AppOpsManager.MODE_ALLOWED }.getOrDefault(false)

    companion object {
        private const val OP_OVERLAY = "android:system_alert_window"
        private const val OP_INSTALL = "android:request_install_packages"

        /** App stores; anything else (browser, file manager, adb) counts as sideloaded. */
        val TRUSTED_INSTALLERS = setOf(
            "com.android.vending", "org.fdroid.fdroid", "org.fdroid.basic", "com.aurora.store",
            "com.looker.droidify", "com.machiav3lli.fdroid", "dev.imranr.obtainium",
            "com.sec.android.app.samsungapps", "com.huawei.appmarket", "com.xiaomi.market",
            "com.xiaomi.mipicks", "com.amazon.venezia", "com.oppo.market", "com.heytap.market",
            "com.google.android.packageinstaller.store",
        )
        private val SMS = setOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS)
        private val LOCATION = setOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }
}
