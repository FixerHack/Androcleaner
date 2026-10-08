package app.androcleaner.feature.settings

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.androcleaner.BuildConfig
import app.androcleaner.R
import app.androcleaner.core.settings.AppSettings
import app.androcleaner.core.settings.SettingsRepository
import app.androcleaner.core.settings.ThemeMode
import app.androcleaner.core.shizuku.ShizukuManager
import app.androcleaner.core.shizuku.ShizukuStatus
import app.androcleaner.feature.cache.AppCacheCleaner
import app.androcleaner.feature.photos.DetailHeader
import app.androcleaner.ui.components.AppCard
import app.androcleaner.ui.theme.BrandTeal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val shizuku: ShizukuManager,
    private val cleaner: AppCacheCleaner,
) : ViewModel() {
    val settings = repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val shizukuStatus = shizuku.status
    val autoCleanAvailable = cleaner.isAutoCleanAvailable

    private val _accessibilityEnabled = MutableStateFlow(cleaner.isServiceEnabled)
    val accessibilityEnabled = _accessibilityEnabled.asStateFlow()

    fun refresh() {
        shizuku.refresh()
        _accessibilityEnabled.value = cleaner.isServiceEnabled
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { repository.setTheme(mode) }
    fun setVirusTotalKey(key: String?) = viewModelScope.launch { repository.setVirusTotalKey(key) }
    fun setMalwareBazaarKey(key: String?) = viewModelScope.launch { repository.setMalwareBazaarKey(key) }
    fun requestShizuku() = shizuku.requestPermission()
    fun openShizuku() = shizuku.openShizukuApp()
}

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val shizuku by viewModel.shizukuStatus.collectAsStateWithLifecycle()
    val accessibility by viewModel.accessibilityEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Column(Modifier.fillMaxSize()) {
        DetailHeader(stringResource(R.string.settings_title), null, onBack)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section(stringResource(R.string.settings_theme)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = settings.theme == mode,
                            onClick = { viewModel.setTheme(mode) },
                            shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        ) {
                            Text(
                                stringResource(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> R.string.theme_system
                                        ThemeMode.LIGHT -> R.string.theme_light
                                        ThemeMode.DARK -> R.string.theme_dark
                                    },
                                ),
                            )
                        }
                    }
                }
            }

            Section(stringResource(R.string.settings_cache)) {
                StatusRow(
                    title = "Shizuku",
                    description = stringResource(
                        when (shizuku) {
                            ShizukuStatus.READY -> R.string.shizuku_ready
                            ShizukuStatus.NO_PERMISSION -> R.string.shizuku_no_permission
                            ShizukuStatus.NOT_RUNNING -> R.string.shizuku_not_running
                            ShizukuStatus.NOT_INSTALLED -> R.string.shizuku_not_installed
                        },
                    ),
                    ok = shizuku == ShizukuStatus.READY,
                ) {
                    when (shizuku) {
                        ShizukuStatus.NOT_INSTALLED -> FilledTonalButton({ open(ShizukuManager.DOWNLOAD_URL) }) { Text(stringResource(R.string.shizuku_install)) }
                        ShizukuStatus.NOT_RUNNING -> FilledTonalButton(viewModel::openShizuku) { Text(stringResource(R.string.shizuku_open)) }
                        ShizukuStatus.NO_PERMISSION -> FilledTonalButton(viewModel::requestShizuku) { Text(stringResource(R.string.shizuku_grant)) }
                        ShizukuStatus.READY -> Unit
                    }
                }
                if (viewModel.autoCleanAvailable) {
                    StatusRow(
                        title = stringResource(R.string.cache_service_label),
                        description = stringResource(if (accessibility) R.string.accessibility_on else R.string.accessibility_off),
                        ok = accessibility,
                    ) {
                        if (!accessibility) {
                            FilledTonalButton({
                                val component = ComponentName(context.packageName, "app.androcleaner.feature.cache.CacheCleanerService").flattenToString()
                                val intents = listOfNotNull(
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").putExtra(Intent.EXTRA_COMPONENT_NAME, component)
                                    } else {
                                        null
                                    },
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                                )
                                intents.firstOrNull { runCatching { context.startActivity(it) }.isSuccess }
                            }) { Text(stringResource(R.string.cache_setup_enable)) }
                        }
                    }
                }
            }

            Section(stringResource(R.string.settings_protection)) {
                Text(stringResource(R.string.settings_keys_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                KeyField("VirusTotal", settings.hasVirusTotalKey, viewModel::setVirusTotalKey) { open("https://www.virustotal.com/gui/join-us") }
                KeyField("MalwareBazaar", settings.hasMalwareBazaarKey, viewModel::setMalwareBazaarKey) { open("https://auth.abuse.ch/") }
            }

            Section(stringResource(R.string.settings_about)) {
                Text("Androcleaner ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_about_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { open("https://github.com/FixerHack/Androcleaner") }) { Text(stringResource(R.string.settings_source)) }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun StatusRow(title: String, description: String, ok: Boolean, action: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        if (ok) Icon(Icons.Rounded.CheckCircle, null, tint = BrandTeal) else action()
    }
}

@Composable
private fun KeyField(name: String, saved: Boolean, onSave: (String?) -> Unit, onGetKey: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf("") }
    if (saved && !editing) {
        StatusRow(name, stringResource(R.string.key_saved), ok = false) {
            Row {
                TextButton(onClick = { editing = true }) { Text(stringResource(R.string.key_change)) }
                TextButton(onClick = { onSave(null) }) { Text(stringResource(R.string.key_remove)) }
            }
        }
        return
    }
    OutlinedTextField(
        value = value,
        onValueChange = { value = it.trim() },
        label = { Text(stringResource(R.string.key_label, name)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    Row {
        TextButton(onClick = onGetKey) { Text(stringResource(R.string.key_get)) }
        Spacer(Modifier.weight(1f))
        FilledTonalButton(onClick = { onSave(value); value = ""; editing = false }, enabled = value.length >= 16) {
            Text(stringResource(R.string.key_save))
        }
    }
}
