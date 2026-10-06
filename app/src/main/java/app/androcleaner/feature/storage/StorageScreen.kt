package app.androcleaner.feature.storage

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.androcleaner.R
import app.androcleaner.core.format.formatBytes
import app.androcleaner.core.storage.FileCategory
import app.androcleaner.core.storage.IndexedFile
import app.androcleaner.ui.components.AppCard
import app.androcleaner.ui.components.EmptyState
import app.androcleaner.ui.components.GradientButton
import app.androcleaner.ui.components.IconBadge
import app.androcleaner.ui.components.LocalSnackbar
import app.androcleaner.ui.components.ScreenHeader
import app.androcleaner.ui.components.color
import app.androcleaner.ui.components.icon
import app.androcleaner.ui.components.label
import app.androcleaner.ui.theme.CategoryColors
import coil3.compose.AsyncImage
import java.io.File

@Composable
fun StorageScreen(viewModel: StorageViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.results.collect { result ->
            snackbar.showSnackbar(
                context.getString(R.string.freed_bytes, android.text.format.Formatter.formatShortFileSize(context, result.freedBytes)),
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.storage_title))
        when {
            state.scanning -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            !state.hasIndex -> EmptyState(
                icon = Icons.Rounded.Storage,
                title = stringResource(R.string.storage_title),
                description = stringResource(R.string.storage_need_scan),
                action = { GradientButton(stringResource(R.string.scan_now), viewModel::scan, Modifier.fillMaxWidth()) },
            )
            else -> StorageContent(state, viewModel, onConfirm = { confirm = true })
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            text = { Text(stringResource(R.string.move_to_trash_confirm, state.selected.size)) },
            confirmButton = {
                TextButton(onClick = { confirm = false; viewModel.moveSelectedToTrash() }) {
                    Text(stringResource(R.string.move_to_trash_action))
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun OverviewCard(state: StorageUiState) {
    val info = state.storage ?: return
    AppCard {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            DonutChart(
                segments = state.slices.map { DonutSegment(it.bytes.toFloat(), it.category?.color ?: CategoryColors.Other.copy(alpha = 0.5f)) },
                total = info.totalBytes.toFloat(),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatBytes(info.freeBytes), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.storage_free), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.size(16.dp))
            state.slices.forEach { slice ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(12.dp).clip(CircleShape)
                            .background(slice.category?.color ?: CategoryColors.Other.copy(alpha = 0.5f)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        slice.category?.let { stringResource(it.label) } ?: stringResource(R.string.storage_system_apps),
                        modifier = Modifier.weight(1f),
                    )
                    Text(formatBytes(slice.bytes), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CategoryFilter(selected: FileCategory?, onSelect: (FileCategory?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(stringResource(R.string.cat_all)) })
        }
        items(FileCategory.entries) { c ->
            FilterChip(selected = selected == c, onClick = { onSelect(c) }, label = { Text(stringResource(c.label)) })
        }
    }
}

@Composable
private fun FileRow(file: IndexedFile, checked: Boolean, onToggle: () -> Unit) {
    AppCard(Modifier.clickable(onClick = onToggle)) {
        Row(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (file.category == FileCategory.IMAGES) {
                AsyncImage(
                    model = File(file.path),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)),
                )
            } else {
                IconBadge(file.category.icon, file.category.color)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                Text(
                    formatBytes(file.size) + " · " +
                        DateUtils.getRelativeTimeSpanString(file.lastModified, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    file.parent.substringAfter("/emulated/0", file.parent).ifEmpty { "/" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun StorageContent(state: StorageUiState, viewModel: StorageViewModel, onConfirm: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { OverviewCard(state) }
            item {
                Text(
                    stringResource(R.string.largest_files),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                )
            }
            item { CategoryFilter(state.filter, viewModel::setFilter) }
            items(state.largest, key = { it.path }) { file ->
                FileRow(file, file.path in state.selected) { viewModel.toggle(file.path) }
            }
        }
        AnimatedVisibility(
            visible = state.selected.isNotEmpty(),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        ) {
            GradientButton(
                stringResource(R.string.move_to_trash, formatBytes(state.selectedBytes)),
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
