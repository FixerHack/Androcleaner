package app.androcleaner.feature.scan

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.androcleaner.R
import app.androcleaner.core.format.formatBytes
import app.androcleaner.feature.cache.CacheTarget
import app.androcleaner.feature.cache.rememberCacheCleaner
import app.androcleaner.ui.components.AppCard
import app.androcleaner.ui.components.GradientBar
import app.androcleaner.ui.components.GradientButton
import app.androcleaner.ui.components.IconBadge
import app.androcleaner.ui.components.LocalSnackbar
import app.androcleaner.ui.components.ScanOrb
import app.androcleaner.ui.theme.BrandPink
import app.androcleaner.ui.theme.BrandTeal
import app.androcleaner.ui.theme.BrandViolet
import app.androcleaner.ui.theme.CategoryColors

private const val MAX_VISIBLE_ITEMS = 50

@Composable
fun ScanScreen(
    onOpenTrash: () -> Unit,
    onOpenAppCache: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    val resources = LocalResources.current
    var confirmClean by remember { mutableStateOf(false) }
    val cleanCache = rememberCacheCleaner(onFinished = viewModel::refreshStats)

    LaunchedEffect(Unit) {
        viewModel.results.collect { result ->
            val freed = android.text.format.Formatter.formatShortFileSize(context, result.freedBytes)
            var message = resources.getString(R.string.freed_bytes, freed)
            if (result.failedPaths.isNotEmpty()) {
                message += " · " + resources.getString(R.string.failed_count, result.failedPaths.size)
            }
            snackbar.showSnackbar(message)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onOpenTrash) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.trash))
                    }
                }
            }
            state.storage?.let { info ->
                item {
                    AppCard {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                stringResource(R.string.storage_used_of, formatBytes(info.usedBytes), formatBytes(info.totalBytes)),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            GradientBar(info.usedFraction)
                        }
                    }
                }
            }
            item { OrbSection(state.scan, extraBytes = state.appCacheBytes ?: 0, onScan = viewModel::scan) }

            val done = state.scan as? ScanState.Done
            if (done != null) {
                items(done.junk, key = { it.type }) { group ->
                    JunkGroupCard(
                        group = group,
                        selected = state.selected,
                        expanded = group.type in state.expanded,
                        onToggleGroup = { viewModel.toggleGroup(group) },
                        onToggleItem = viewModel::toggleItem,
                        onExpand = { viewModel.toggleExpanded(group.type) },
                        rootPath = done.index.root,
                    )
                }
            }
            state.appCacheBytes?.let { cache ->
                item { AppCacheCard(cache, onClean = { cleanCache(CacheTarget.AllApps) }, onOpen = onOpenAppCache) }
            }
        }

        AnimatedVisibility(
            visible = state.scan is ScanState.Done && state.selected.isNotEmpty(),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        ) {
            if (state.cleaning) {
                Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                GradientButton(
                    text = stringResource(R.string.clean_selected, formatBytes(state.selectedBytes)),
                    onClick = { confirmClean = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (confirmClean) {
        AlertDialog(
            onDismissRequest = { confirmClean = false },
            icon = { Icon(Icons.Rounded.CleaningServices, null) },
            title = { Text(stringResource(R.string.clean_confirm_title, formatBytes(state.selectedBytes))) },
            text = { Text(stringResource(R.string.clean_confirm_text)) },
            confirmButton = {
                TextButton(onClick = { confirmClean = false; viewModel.cleanSelected() }) {
                    Text(stringResource(R.string.clean_confirm_action))
                }
            },
            dismissButton = { TextButton(onClick = { confirmClean = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun OrbSection(scan: ScanState, extraBytes: Long, onScan: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScanOrb(active = scan is ScanState.Scanning, onClick = if (scan is ScanState.Scanning) null else onScan) {
            AnimatedContent(
                targetState = scan,
                contentKey = { it::class },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "orb-content",
            ) { s ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    when (s) {
                        ScanState.Idle -> {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.scan_now), color = Color.White, style = MaterialTheme.typography.headlineSmall)
                        }
                        is ScanState.Scanning -> {
                            Text(stringResource(R.string.scan_scanning), color = Color.White, style = MaterialTheme.typography.titleMedium)
                            Text(formatBytes(s.bytesScanned), color = Color.White, style = MaterialTheme.typography.headlineMedium)
                            Text(
                                pluralStringResource(R.plurals.files_count, s.filesScanned, s.filesScanned),
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        is ScanState.Done -> if (s.junkBytes + extraBytes == 0L && s.junk.isEmpty()) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = Color.White, modifier = Modifier.size(48.dp))
                            Text(stringResource(R.string.scan_all_clean), color = Color.White, style = MaterialTheme.typography.headlineSmall)
                        } else {
                            Text(
                                formatBytes(s.junkBytes + extraBytes),
                                color = Color.White,
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(stringResource(R.string.scan_can_free), color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                }
            }
        }
        when (scan) {
            ScanState.Idle -> Text(
                stringResource(R.string.scan_hint),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            is ScanState.Scanning -> Text(
                scan.currentDirectory.ifEmpty { "/" },
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            is ScanState.Done -> TextButton(onClick = onScan) { Text(stringResource(R.string.scan_rescan)) }
        }
    }
}

private data class JunkStyle(val icon: ImageVector, val color: Color, val title: Int, val description: Int)

private val JunkType.style: JunkStyle
    get() = when (this) {
        JunkType.THUMBNAILS -> JunkStyle(Icons.Rounded.PhotoLibrary, BrandViolet, R.string.junk_thumbnails, R.string.junk_thumbnails_desc)
        JunkType.TEMP_FILES -> JunkStyle(Icons.Rounded.Description, CategoryColors.Audio, R.string.junk_temp, R.string.junk_temp_desc)
        JunkType.APK_FILES -> JunkStyle(Icons.Rounded.Android, BrandTeal, R.string.junk_apks, R.string.junk_apks_desc)
        JunkType.LEFTOVERS -> JunkStyle(Icons.Rounded.Inventory2, BrandPink, R.string.junk_leftovers, R.string.junk_leftovers_desc)
        JunkType.EMPTY_FOLDERS -> JunkStyle(Icons.Rounded.FolderOff, CategoryColors.System, R.string.junk_empty, R.string.junk_empty_desc)
    }

@Composable
private fun JunkGroupCard(
    group: JunkGroup,
    selected: Set<String>,
    expanded: Boolean,
    onToggleGroup: () -> Unit,
    onToggleItem: (String) -> Unit,
    onExpand: () -> Unit,
    rootPath: String,
) {
    val style = group.type.style
    val selectedCount = group.items.count { it.path in selected }
    val toggleState = when (selectedCount) {
        0 -> ToggleableState.Off
        group.items.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    AppCard {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onExpand).padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(style.icon, style.color)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(style.title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (group.type == JunkType.EMPTY_FOLDERS) {
                            pluralStringResource(R.plurals.folders_count, group.items.size, group.items.size)
                        } else {
                            formatBytes(group.totalBytes) + " · " + if (group.type == JunkType.LEFTOVERS) {
                                pluralStringResource(R.plurals.folders_count, group.items.size, group.items.size)
                            } else {
                                pluralStringResource(R.plurals.files_count, group.items.size, group.items.size)
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TriStateCheckbox(state = toggleState, onClick = onToggleGroup)
            }
            AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(bottom = 8.dp)) {
                    Text(
                        stringResource(style.description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    group.items.take(MAX_VISIBLE_ITEMS).forEach { item ->
                        JunkItemRow(item, item.path in selected, rootPath) { onToggleItem(item.path) }
                    }
                    if (group.items.size > MAX_VISIBLE_ITEMS) {
                        Text(
                            stringResource(R.string.more_items, group.items.size - MAX_VISIBLE_ITEMS),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JunkItemRow(item: JunkItem, checked: Boolean, rootPath: String, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(
                item.path.removePrefix(rootPath).trimStart('/'),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            val note = item.note?.let {
                when (it) {
                    JunkNote.APK_INSTALLED -> R.string.note_apk_installed
                    JunkNote.APK_NEWER_INSTALLED -> R.string.note_apk_newer
                    JunkNote.APK_NOT_INSTALLED -> R.string.note_apk_not_installed
                    JunkNote.APP_UNINSTALLED -> R.string.note_app_uninstalled
                    JunkNote.APK_UNREADABLE -> R.string.note_apk_unreadable
                }
            }
            val sizeText = if (item.size > 0) formatBytes(item.size) else null
            val subtitle = listOfNotNull(sizeText, note?.let { stringResource(it) }).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun AppCacheCard(cacheBytes: Long, onClean: () -> Unit, onOpen: () -> Unit) {
    AppCard(Modifier.clickable(onClick = onOpen)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Speed, BrandTeal)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_cache_title) + " · " + formatBytes(cacheBytes), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.app_cache_auto_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onClean) { Text(stringResource(R.string.cache_clean_button)) }
        }
    }
}
