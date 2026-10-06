package app.androcleaner.feature.trash

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.androcleaner.R
import app.androcleaner.core.format.formatBytes
import app.androcleaner.core.storage.FileCategory
import app.androcleaner.core.trash.TrashEntry
import app.androcleaner.ui.components.AppCard
import app.androcleaner.ui.components.EmptyState
import app.androcleaner.ui.components.GradientButton
import app.androcleaner.ui.components.IconBadge
import app.androcleaner.ui.components.color
import app.androcleaner.ui.components.icon
import app.androcleaner.ui.theme.CategoryColors
import java.util.concurrent.TimeUnit

@Composable
fun TrashScreen(onBack: () -> Unit, viewModel: TrashViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.trash_title), style = MaterialTheme.typography.headlineMedium)
                if (state.entries.isNotEmpty()) {
                    Text(formatBytes(state.totalBytes), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.entries.isNotEmpty()) {
                TextButton(onClick = viewModel::selectAll) { Text(stringResource(R.string.select_all)) }
            }
        }

        if (state.entries.isEmpty()) {
            EmptyState(Icons.Rounded.DeleteOutline, stringResource(R.string.trash_empty), stringResource(R.string.trash_empty_desc))
            return@Column
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.entries, key = { it.id }) { entry ->
                TrashRow(entry, entry.id in state.selected) { viewModel.toggle(entry.id) }
            }
        }
        if (state.selected.isNotEmpty()) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Text(stringResource(R.string.trash_delete_forever), color = MaterialTheme.colorScheme.error)
                }
                GradientButton(stringResource(R.string.trash_restore), viewModel::restoreSelected, Modifier.weight(1f))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.trash_delete_confirm, state.selected.size)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.deleteSelected() }) {
                    Text(stringResource(R.string.trash_delete_forever), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun TrashRow(entry: TrashEntry, checked: Boolean, onToggle: () -> Unit) {
    val category = FileCategory.fromFileName(entry.name)
    val msLeft = (entry.expiresAt - System.currentTimeMillis()).coerceAtLeast(0)
    val daysLeft = ((msLeft + TimeUnit.DAYS.toMillis(1) - 1) / TimeUnit.DAYS.toMillis(1)).toInt()
    AppCard(Modifier.clickable(onClick = onToggle)) {
        Row(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (entry.isDirectory) {
                IconBadge(Icons.Rounded.Folder, CategoryColors.System)
            } else {
                IconBadge(category.icon, category.color)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.name, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                Text(
                    formatBytes(entry.size) + " · " + pluralStringResource(R.plurals.trash_days_left, daysLeft, daysLeft),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    entry.originalPath.substringBeforeLast('/').substringAfter("/emulated/0").ifEmpty { "/" },
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
