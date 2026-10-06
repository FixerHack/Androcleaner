package app.androcleaner.core.permissions

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Process
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

data class PermissionStatus(val allFilesAccess: Boolean, val usageAccess: Boolean)

object Permissions {
    fun status(context: Context) = PermissionStatus(
        allFilesAccess = Environment.isExternalStorageManager(),
        usageAccess = hasUsageAccess(context),
    )

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION") // Replacement needs API 37; this is still the documented check.
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun requestAllFilesAccess(context: Context) {
        context.startFirstResolvable(
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri(context)),
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
        )
    }

    fun requestUsageAccess(context: Context) {
        context.startFirstResolvable(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, packageUri(context)),
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
        )
    }

    private fun packageUri(context: Context) = Uri.parse("package:${context.packageName}")

    private fun Context.startFirstResolvable(vararg intents: Intent) {
        for (intent in intents) {
            if (runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        }
    }
}

/** Permission status that refreshes every time the screen resumes (e.g. back from Settings). */
@Composable
fun rememberPermissionStatus(): PermissionStatus {
    val context = LocalContext.current
    var status by remember { mutableStateOf(Permissions.status(context)) }
    LifecycleResumeEffect(Unit) {
        status = Permissions.status(context)
        onPauseOrDispose { }
    }
    return status
}
