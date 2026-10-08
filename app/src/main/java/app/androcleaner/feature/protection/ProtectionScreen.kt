package app.androcleaner.feature.protection

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.androcleaner.R
import app.androcleaner.core.db.ApkReputationEntity
import app.androcleaner.ui.components.AppCard
import app.androcleaner.ui.components.AppIcon
import app.androcleaner.ui.components.IconBadge
import app.androcleaner.ui.components.ScreenHeader
import app.androcleaner.ui.theme.BrandPink
import app.androcleaner.ui.theme.BrandTeal
import app.androcleaner.ui.theme.CategoryColors

private val Amber = CategoryColors.Audio

@Composable
fun ProtectionScreen(onOpenSettings: () -> Unit, viewModel: ProtectionViewModel = hiltViewModel()) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    val auditing by viewModel.auditing.collectAsStateWithLifecycle()
    val cloud by viewModel.cloud.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var details by remember { mutableStateOf<AppRisk?>(null) }
    var permission by remember { mutableStateOf<Finding?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.protection_title)) {
            IconButton(onClick = viewModel::audit, enabled = !auditing) { Icon(Icons.Rounded.Refresh, stringResource(R.string.scan_rescan)) }
        }
        val r = report
        if (r == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        val attention = r.apps.filter { it.level != RiskLevel.LOW || r.reputationOf(it.packageName)?.let { rep -> rep.isThreat || rep.isSuspicious } == true }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { StatusCard(r, attention.count { it.level == RiskLevel.HIGH }, auditing) }
            item {
                CloudCard(
                    state = cloud,
                    hasKeys = settings.hasVirusTotalKey || settings.hasMalwareBazaarKey,
                    hasVirusTotal = settings.hasVirusTotalKey,
                    sideloadedCount = r.apps.count { Finding.SIDELOADED in it.findings } + r.apkFiles.size,
                    allCount = r.apps.size + r.apkFiles.size,
                    onCheck = viewModel::cloudCheck,
                    onCancel = viewModel::cancelCloud,
                    onOpenSettings = onOpenSettings,
                )
            }
            if (attention.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.protection_attention)) }
                items(attention, key = { "a-" + it.packageName }) { app ->
                    AppRiskRow(app, r.reputationOf(app.packageName)) { details = app }
                }
            }
            item { SectionTitle(stringResource(R.string.protection_permissions)) }
            item { PermissionChips(r.apps) { permission = it } }
            if (r.apkFiles.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.protection_apk_files)) }
                items(r.apkFiles, key = { "f-" + it.key }) { file -> ApkFileRow(file, r.reputationOf(file.key)) }
            }
        }
    }

    permission?.let { finding ->
        AppListSheet(
            title = stringResource(finding.title),
            apps = report?.apps.orEmpty().filter { finding in it.findings },
            onDismiss = { permission = null },
            onOpen = { permission = null; details = it },
        )
    }
    details?.let { app -> AppDetailsSheet(app, report?.reputationOf(app.packageName), report?.hashes?.get(app.packageName)) { details = null } }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

@Composable
private fun StatusCard(report: ProtectionReport, highRisk: Int, auditing: Boolean) {
    val threats = report.threats.size
    val (icon, color, title) = when {
        threats > 0 -> Triple(Icons.Rounded.GppBad, BrandPink, stringResource(R.string.protection_threats, threats))
        highRisk > 0 -> Triple(Icons.Rounded.GppMaybe, Amber, stringResource(R.string.protection_high_risk, highRisk))
        else -> Triple(Icons.Rounded.GppGood, BrandTeal, stringResource(R.string.protection_all_good))
    }
    AppCard {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).background(color.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                if (auditing) CircularProgressIndicator(Modifier.size(32.dp), color = color) else Icon(icon, null, tint = color, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(if (auditing) stringResource(R.string.protection_auditing) else title, style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.protection_checked, report.apps.size, report.apkFiles.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun CloudCard(
    state: CloudState,
    hasKeys: Boolean,
    hasVirusTotal: Boolean,
    sideloadedCount: Int,
    allCount: Int,
    onCheck: (CloudScope) -> Unit,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.cloud_title), style = MaterialTheme.typography.titleMedium)
            when {
                !hasKeys || state == CloudState.NoKeys -> {
                    Text(stringResource(R.string.cloud_no_keys), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(onClick = onOpenSettings) { Text(stringResource(R.string.cloud_add_key)) }
                }
                state is CloudState.Running -> {
                    Text(stringResource(R.string.cloud_running, state.current, state.done + 1, state.total), style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(progress = { state.done.toFloat() / state.total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
                    if (hasVirusTotal) {
                        Text(stringResource(R.string.cloud_rate_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                }
                else -> {
                    when (state) {
                        is CloudState.Finished -> Text(stringResource(R.string.cloud_finished, state.checked, state.threats), style = MaterialTheme.typography.bodyMedium)
                        CloudState.InvalidKey -> Text(stringResource(R.string.cloud_invalid_key), color = BrandPink)
                        is CloudState.Failed -> Text(stringResource(R.string.cloud_failed, state.message), color = BrandPink)
                        else -> Text(stringResource(R.string.cloud_ready), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { onCheck(CloudScope.SIDELOADED) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.cloud_check_sideloaded, sideloadedCount))
                    }
                    OutlinedButton(onClick = { onCheck(CloudScope.ALL) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.cloud_check_all, allCount, if (hasVirusTotal) (allCount + 3) / 4 else 1))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReputationText(rep: ApkReputationEntity?) {
    val (text, color) = when {
        rep == null -> return
        rep.isThreat -> (rep.mbSignature?.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.rep_malware, it) }
            ?: stringResource(R.string.rep_detections, rep.vtMalicious ?: 0, rep.vtTotal ?: 0)) to BrandPink
        rep.isSuspicious -> stringResource(R.string.rep_detections, (rep.vtMalicious ?: 0) + (rep.vtSuspicious ?: 0), rep.vtTotal ?: 0) to Amber
        rep.vtMalicious == -1 -> stringResource(R.string.rep_unknown) to MaterialTheme.colorScheme.onSurfaceVariant
        rep.vtTotal != null -> stringResource(R.string.rep_clean, rep.vtTotal) to BrandTeal
        rep.mbSignature == "" -> stringResource(R.string.rep_mb_clean) to BrandTeal
        else -> return
    }
    Text(text, color = color, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun AppRiskRow(app: AppRisk, rep: ApkReputationEntity?, onClick: () -> Unit) {
    AppCard(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.packageName)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    app.findings.filter { it.sensitive || it == Finding.SIDELOADED }.map { stringResource(it.title) }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
                ReputationText(rep)
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(10.dp).background(if (app.level == RiskLevel.HIGH) BrandPink else Amber, CircleShape))
        }
    }
}

@Composable
private fun ApkFileRow(file: ApkTarget, rep: ApkReputationEntity?) {
    AppCard {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Android, BrandTeal)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(file.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                if (rep != null) ReputationText(rep) else {
                    Text(stringResource(R.string.rep_not_checked), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private val AUDITED = listOf(
    Finding.ACCESSIBILITY, Finding.DEVICE_ADMIN, Finding.NOTIFICATION_LISTENER, Finding.SMS, Finding.OVERLAY,
    Finding.INSTALLS_APPS, Finding.CAMERA, Finding.MICROPHONE, Finding.LOCATION, Finding.CONTACTS, Finding.CALL_LOG, Finding.SIDELOADED,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PermissionChips(apps: List<AppRisk>, onSelect: (Finding) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AUDITED.forEach { finding ->
            val count = apps.count { finding in it.findings }
            AssistChip(onClick = { onSelect(finding) }, enabled = count > 0, label = { Text("${stringResource(finding.title)} · $count") })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppListSheet(title: String, apps: List<AppRisk>, onDismiss: () -> Unit, onOpen: (AppRisk) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
        LazyColumn(Modifier.navigationBarsPadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(apps, key = { it.packageName }) { app ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(app) }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(app.packageName, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(app.label, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDetailsSheet(app: AppRisk, rep: ApkReputationEntity?, sha256: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app.packageName, size = 56.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(app.label, style = MaterialTheme.typography.titleLarge)
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(R.string.protection_installer, app.installer ?: stringResource(R.string.protection_installer_unknown)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ReputationText(rep)
            app.findings.sortedBy { !it.sensitive }.forEach { finding ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.padding(top = 6.dp).size(8.dp)
                            .background(if (finding.sensitive) BrandPink else MaterialTheme.colorScheme.outline, CircleShape),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(finding.title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(finding.explanation), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.protection_app_settings)) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (sha256 != null) {
                    OutlinedButton(
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.virustotal.com/gui/file/$sha256"))) },
                        modifier = Modifier.weight(1f),
                    ) { Text("VirusTotal") }
                }
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.apps_uninstall), color = Color(0xFFE5484D)) }
            }
        }
    }
}

private val Finding.title: Int
    get() = when (this) {
        Finding.SIDELOADED -> R.string.finding_sideloaded
        Finding.ACCESSIBILITY -> R.string.finding_accessibility
        Finding.DEVICE_ADMIN -> R.string.finding_admin
        Finding.NOTIFICATION_LISTENER -> R.string.finding_notifications
        Finding.SMS -> R.string.finding_sms
        Finding.OVERLAY -> R.string.finding_overlay
        Finding.INSTALLS_APPS -> R.string.finding_installs
        Finding.HIDDEN_ICON -> R.string.finding_hidden
        Finding.CAMERA -> R.string.finding_camera
        Finding.MICROPHONE -> R.string.finding_microphone
        Finding.LOCATION -> R.string.finding_location
        Finding.CONTACTS -> R.string.finding_contacts
        Finding.CALL_LOG -> R.string.finding_call_log
    }

private val Finding.explanation: Int
    get() = when (this) {
        Finding.SIDELOADED -> R.string.finding_sideloaded_desc
        Finding.ACCESSIBILITY -> R.string.finding_accessibility_desc
        Finding.DEVICE_ADMIN -> R.string.finding_admin_desc
        Finding.NOTIFICATION_LISTENER -> R.string.finding_notifications_desc
        Finding.SMS -> R.string.finding_sms_desc
        Finding.OVERLAY -> R.string.finding_overlay_desc
        Finding.INSTALLS_APPS -> R.string.finding_installs_desc
        Finding.HIDDEN_ICON -> R.string.finding_hidden_desc
        Finding.CAMERA -> R.string.finding_camera_desc
        Finding.MICROPHONE -> R.string.finding_microphone_desc
        Finding.LOCATION -> R.string.finding_location_desc
        Finding.CONTACTS -> R.string.finding_contacts_desc
        Finding.CALL_LOG -> R.string.finding_call_log_desc
    }
