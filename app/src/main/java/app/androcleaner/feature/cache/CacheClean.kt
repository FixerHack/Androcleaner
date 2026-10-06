package app.androcleaner.feature.cache

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.androcleaner.R
import app.androcleaner.feature.apps.AppInfo
import app.androcleaner.feature.apps.AppsRepository
import app.androcleaner.ui.components.LocalSnackbar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CacheTarget {
    data object AllApps : CacheTarget
    data class Apps(val apps: List<AppInfo>) : CacheTarget
}

@HiltViewModel
class CacheCleanViewModel @Inject constructor(
    private val cleaner: AppCacheCleaner,
    private val apps: AppsRepository,
) : ViewModel() {
    val state = cleaner.state

    val isServiceEnabled: Boolean get() = cleaner.isServiceEnabled
    val isAutoCleanAvailable: Boolean get() = cleaner.isAutoCleanAvailable

    fun clean(target: CacheTarget) {
        viewModelScope.launch {
            val targets = when (target) {
                is CacheTarget.Apps -> target.apps
                CacheTarget.AllApps -> apps.loadApps()
                    .filter { (it.sizes?.cacheBytes ?: 0) >= MIN_CACHE_BYTES }
                    .sortedByDescending { it.sizes?.cacheBytes ?: 0 }
            }
            cleaner.start(targets)
        }
    }

    fun consumeResult() = cleaner.consumeResult()

    private companion object {
        /** Skip apps whose cache is too small to be worth a Settings round trip. */
        const val MIN_CACHE_BYTES = 1L shl 20
    }
}

/**
 * Returns a function that clears app cache. With the accessibility service on, it presses
 * "Clear cache" for every app automatically; otherwise it explains the options first.
 */
@Composable
fun rememberCacheCleaner(onFinished: () -> Unit): (CacheTarget) -> Unit {
    val viewModel: CacheCleanViewModel = hiltViewModel()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<CacheTarget?>(null) }

    val quickClean = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onFinished()
    }

    LaunchedEffect(state) {
        val finished = state as? CacheCleanState.Finished ?: return@LaunchedEffect
        viewModel.consumeResult()
        onFinished()
        val freed = android.text.format.Formatter.formatShortFileSize(context, finished.freedBytes)
        var message = resources.getString(R.string.cache_cleaned, freed)
        if (finished.failedApps > 0) {
            message += " · " + resources.getString(R.string.failed_count, finished.failedApps)
        }
        snackbar.showSnackbar(message)
    }

    pending?.let { target ->
        val launchQuickClean = {
            pending = null
            // System dialog that clears the external cache of all apps at once.
            runCatching { quickClean.launch(Intent(StorageManager.ACTION_CLEAR_APP_CACHE)) }
            Unit
        }
        if (!viewModel.isAutoCleanAvailable) {
            AlertDialog(
                onDismissRequest = { pending = null },
                icon = { Icon(Icons.Rounded.CleaningServices, null) },
                title = { Text(stringResource(R.string.cache_quick_title)) },
                text = { Text(stringResource(R.string.cache_quick_text)) },
                confirmButton = { TextButton(onClick = launchQuickClean) { Text(stringResource(R.string.cache_clean_button)) } },
                dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.cancel)) } },
            )
            return@let
        }
        AlertDialog(
            onDismissRequest = { pending = null },
            icon = { Icon(Icons.Rounded.CleaningServices, null) },
            title = { Text(stringResource(R.string.cache_setup_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.cache_setup_text))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.cache_setup_restricted),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    openAccessibilitySettings(context)
                }) { Text(stringResource(R.string.cache_setup_enable)) }
            },
            dismissButton = {
                if (target !is CacheTarget.Apps || target.apps.size > 1) {
                    TextButton(onClick = launchQuickClean) { Text(stringResource(R.string.cache_setup_quick)) }
                } else {
                    TextButton(onClick = { pending = null }) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }

    return { target -> if (viewModel.isServiceEnabled) viewModel.clean(target) else pending = target }
}

private fun openAccessibilitySettings(context: android.content.Context) {
    val component = ComponentName(context.packageName, "app.androcleaner.feature.cache.CacheCleanerService").flattenToString()
    val details = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").putExtra(Intent.EXTRA_COMPONENT_NAME, component)
    } else {
        null
    }
    for (intent in listOfNotNull(details, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))) {
        if (runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }
}
