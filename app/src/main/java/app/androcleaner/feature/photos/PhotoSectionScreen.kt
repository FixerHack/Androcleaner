package app.androcleaner.feature.photos

import android.text.format.DateUtils
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.androcleaner.R
import app.androcleaner.core.format.formatBytes
import app.androcleaner.core.storage.DuplicateGroup
import app.androcleaner.ui.components.AppCard
import app.androcleaner.ui.components.BottomAction
import app.androcleaner.ui.components.EmptyState
import app.androcleaner.ui.components.LocalSnackbar
import app.androcleaner.ui.components.color
import app.androcleaner.ui.components.IconBadge
import app.androcleaner.ui.components.icon
import java.io.File

@Composable
fun PhotoSectionScreen(section: PhotoSection, onBack: () -> Unit, viewModel: PhotosViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val done = state as? PhotosState.Done
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    val resources = LocalResources.current
    var confirm by remember { mutableStateOf(false) }

    LaunchedEffect(done != null) { viewModel.preselect(section) }
    LaunchedEffect(Unit) {
        viewModel.results.collect {
            snackbar.showSnackbar(
                resources.getString(R.string.freed_bytes, android.text.format.Formatter.formatShortFileSize(context, it.freedBytes)),
            )
        }
    }

    // path -> size of everything shown in this section, for the selected total.
    val sizes: Map<String, Long> = when (section) {
        PhotoSection.SIMILAR -> done?.report?.similar?.flatMap { it.photos }?.associate { it.photo.path to it.photo.size }
        PhotoSection.BLURRY -> done?.report?.blurry?.associate { it.photo.path to it.photo.size }
        PhotoSection.SCREENSHOTS -> done?.report?.screenshots?.associate { it.path to it.size }
        PhotoSection.DUPLICATES -> done?.duplicates?.flatMap { it.files }?.associate { it.path to it.size }
    }.orEmpty()
    val selectedHere = selected intersect sizes.keys
    val selectedBytes = selectedHere.sumOf { sizes[it] ?: 0L }

    val (title, hint) = when (section) {
        PhotoSection.SIMILAR -> R.string.section_similar to R.string.similar_hint
        PhotoSection.BLURRY -> R.string.section_blurry to R.string.blurry_hint
        PhotoSection.SCREENSHOTS -> R.string.section_screenshots to R.string.screenshots_hint
        PhotoSection.DUPLICATES -> R.string.section_duplicates to R.string.duplicates_hint
    }

    Column(Modifier.fillMaxSize()) {
        DetailHeader(stringResource(title), stringResource(hint), onBack) {
            if (sizes.isNotEmpty()) {
                TextButton(onClick = {
                    viewModel.setSelected(if (selectedHere.size == sizes.size) selected - sizes.keys else selected + sizes.keys)
                }) {
                    Text(stringResource(if (selectedHere.size == sizes.size) R.string.select_none else R.string.select_all))
                }
            }
        }
        Box(Modifier.weight(1f)) {
            if (done == null || sizes.isEmpty()) {
                EmptyState(Icons.Rounded.CheckCircle, stringResource(R.string.nothing_found), stringResource(R.string.nothing_found_desc))
            } else {
                when (section) {
                    PhotoSection.SIMILAR -> SimilarList(done.report.similar, selected, viewModel::toggle)
                    PhotoSection.BLURRY -> PhotoGrid(done.report.blurry.map { it.photo }, selected, viewModel::toggle)
                    PhotoSection.SCREENSHOTS -> PhotoGrid(done.report.screenshots, selected, viewModel::toggle)
                    PhotoSection.DUPLICATES -> DuplicateList(done.duplicates, selected, viewModel::toggle)
                }
            }
            BottomAction(
                visible = selectedHere.isNotEmpty(),
                text = stringResource(R.string.move_to_trash, formatBytes(selectedBytes)),
                onClick = { confirm = true },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            text = { Text(stringResource(R.string.move_to_trash_confirm, selectedHere.size)) },
            confirmButton = {
                TextButton(onClick = { confirm = false; viewModel.trash(selectedHere) }) { Text(stringResource(R.string.move_to_trash_action)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private val BottomPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)

@Composable
private fun PhotoGrid(photos: List<Photo>, selected: Set<String>, onToggle: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(108.dp),
        contentPadding = BottomPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(photos, key = { it.id }) { photo ->
            PhotoTile(photo.uri, photo.path in selected, onClick = { onToggle(photo.path) })
        }
    }
}

@Composable
private fun SimilarList(groups: List<SimilarGroup>, selected: Set<String>, onToggle: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = BottomPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        groups.forEachIndexed { index, group ->
            item(key = "header-$index", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    pluralStringResource(R.plurals.photos_count, group.photos.size, group.photos.size) + " · " +
                        DateUtils.formatDateTime(LocalContext.current, group.photos.first().photo.takenAt, DateUtils.FORMAT_SHOW_DATE),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = if (index == 0) 0.dp else 12.dp, start = 4.dp),
                )
            }
            items(group.photos, key = { it.photo.id }) { photo ->
                PhotoTile(
                    model = photo.photo.uri,
                    selected = photo.photo.path in selected,
                    onClick = { onToggle(photo.photo.path) },
                    badge = if (photo == group.best) stringResource(R.string.best_badge) else null,
                )
            }
        }
    }
}

@Composable
private fun DuplicateList(groups: List<DuplicateGroup>, selected: Set<String>, onToggle: (String) -> Unit) {
    LazyColumn(contentPadding = BottomPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(groups, key = { it.hash + it.files.first().path }) { group ->
            val first = group.files.first()
            AppCard {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(first.category.icon, first.category.color)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(first.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                            Text(
                                pluralStringResource(R.plurals.copies_count, group.files.size, group.files.size) + " · " + formatBytes(group.fileSize),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    group.files.forEach { file ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onToggle(file.path) }.padding(start = 16.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                                Text(
                                    File(file.path).parent?.substringAfter("/emulated/0").orEmpty().ifEmpty { "/" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.MiddleEllipsis,
                                )
                                if (file == group.suggestedKeep) {
                                    Text(stringResource(R.string.keep_badge), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                            Checkbox(checked = file.path in selected, onCheckedChange = { onToggle(file.path) })
                        }
                    }
                }
            }
        }
    }
}
