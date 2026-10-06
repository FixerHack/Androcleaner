package app.androcleaner.feature.apps

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.androcleaner.R
import app.androcleaner.core.format.formatBytes
import app.androcleaner.core.permissions.Permissions
import app.androcleaner.ui.components.AppCard
import app.androcleaner.ui.components.AppIcon
import app.androcleaner.ui.components.EmptyState
import app.androcleaner.ui.components.IconBadge
import app.androcleaner.ui.components.ScreenHeader

@Composable
fun AppsScreen(viewModel: AppsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var details by remember { mutableStateOf<AppInfo?>(null) }

    // Reload when returning from Settings / uninstall dialog.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.apps_title))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SortChip(state.sort, AppSort.SIZE, R.string.apps_sort_size, viewModel::setSort) }
            item { SortChip(state.sort, AppSort.CACHE, R.string.apps_sort_cache, viewModel::setSort) }
            item { SortChip(state.sort, AppSort.UNUSED, R.string.apps_sort_unused, viewModel::setSort) }
            item {
                FilterChip(
                    selected = state.showSystem,
                    onClick = viewModel::toggleSystem,
                    label = { Text(stringResource(R.string.apps_show_system)) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!state.hasSizes) {
                    item { UsageAccessBanner { Permissions.requestUsageAccess(context) } }
                }
                if (state.apps.isEmpty() && state.sort == AppSort.UNUSED) {
                    item {
                        EmptyState(Icons.Rounded.Apps, stringResource(R.string.apps_sort_unused), stringResource(R.string.apps_empty_unused))
                    }
                }
                items(state.apps, key = { it.packageName }) { app ->
                    AppRow(app, state.sort) { details = app }
                }
            }
        }
    }

    details?.let { app -> AppDetailsSheet(app, onDismiss = { details = null }) }
}

@Composable
private fun SortChip(current: AppSort, value: AppSort, label: Int, onSelect: (AppSort) -> Unit) {
    FilterChip(selected = current == value, onClick = { onSelect(value) }, label = { Text(stringResource(label)) })
}

@Composable
private fun UsageAccessBanner(onGrant: () -> Unit) {
    AppCard {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.BarChart, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.apps_usage_banner), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onGrant) { Text(stringResource(R.string.grant)) }
        }
    }
}

@Composable
private fun lastUsedText(app: AppInfo): String = app.lastUsedAt?.let {
    stringResource(
        R.string.apps_last_used,
        DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString().lowercase(),
    )
} ?: stringResource(R.string.apps_never_used)

@Composable
private fun AppRow(app: AppInfo, sort: AppSort, onClick: () -> Unit) {
    AppCard(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.packageName)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val subtitle = when {
                    sort == AppSort.UNUSED -> lastUsedText(app)
                    app.sizes != null -> stringResource(R.string.apps_cache_size, formatBytes(app.sizes.cacheBytes))
                    else -> app.packageName
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            app.sizes?.let {
                Text(
                    formatBytes(if (sort == AppSort.CACHE) it.cacheBytes else it.totalBytes),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDetailsSheet(app: AppInfo, onDismiss: () -> Unit) {
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
                    Text(
                        listOfNotNull(app.versionName, app.packageName).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            app.sizes?.let { sizes ->
                AppCard {
                    Row(Modifier.padding(16.dp)) {
                        SizeCell(R.string.apps_size_app, sizes.appBytes, Modifier.weight(1f))
                        SizeCell(R.string.apps_size_data, sizes.dataBytes - sizes.cacheBytes, Modifier.weight(1f))
                        SizeCell(R.string.apps_size_cache, sizes.cacheBytes, Modifier.weight(1f))
                    }
                }
            }
            Text(lastUsedText(app), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(
                    R.string.apps_installed,
                    DateUtils.formatDateTime(context, app.installedAt, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    Toast.makeText(context, R.string.apps_clear_cache_hint, Toast.LENGTH_LONG).show()
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.apps_clear_cache)) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                context.packageManager.getLaunchIntentForPackage(app.packageName)?.let { launch ->
                    OutlinedButton(onClick = { context.startActivity(launch) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.apps_open))
                    }
                }
                if (!app.isSystem) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.apps_uninstall), color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
private fun SizeCell(label: Int, bytes: Long, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(formatBytes(bytes), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
