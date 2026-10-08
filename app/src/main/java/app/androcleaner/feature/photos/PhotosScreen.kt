package app.androcleaner.feature.photos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.BurstMode
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.androcleaner.R
import app.androcleaner.core.format.formatBytes
import app.androcleaner.ui.components.AppCard
import app.androcleaner.ui.components.EmptyState
import app.androcleaner.ui.components.GradientButton
import app.androcleaner.ui.components.IconBadge
import app.androcleaner.ui.components.ScreenHeader
import app.androcleaner.ui.theme.BrandPink
import app.androcleaner.ui.theme.BrandTeal
import app.androcleaner.ui.theme.BrandViolet
import app.androcleaner.ui.theme.CategoryColors

@Composable
fun PhotosScreen(
    onOpenSection: (PhotoSection) -> Unit,
    onOpenSwipe: () -> Unit,
    viewModel: PhotosViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.photos_title))
        when (val s = state) {
            PhotosState.Idle -> EmptyState(
                icon = Icons.Rounded.PhotoLibrary,
                title = stringResource(R.string.photos_desc),
                description = stringResource(R.string.photos_intro),
                action = { GradientButton(stringResource(R.string.photos_analyze), viewModel::analyze, Modifier.fillMaxWidth()) },
            )
            is PhotosState.Analyzing -> AnalyzingCard(s)
            is PhotosState.Done -> Hub(s, onOpenSection, onOpenSwipe, viewModel::analyze)
        }
    }
}

@Composable
private fun AnalyzingCard(state: PhotosState.Analyzing) {
    AppCard(Modifier.padding(16.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                when (state.stage) {
                    PhotosState.Stage.SCANNING_FILES -> stringResource(R.string.photos_stage_files)
                    PhotosState.Stage.PHOTOS -> stringResource(R.string.photos_stage_photos, state.done, state.total)
                    PhotosState.Stage.DUPLICATES -> stringResource(R.string.photos_stage_duplicates, state.done, state.total)
                },
                style = MaterialTheme.typography.titleMedium,
            )
            if (state.total > 0) {
                LinearProgressIndicator(progress = { state.done.toFloat() / state.total }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Text(stringResource(R.string.photos_offline_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Hub(
    state: PhotosState.Done,
    onOpenSection: (PhotoSection) -> Unit,
    onOpenSwipe: () -> Unit,
    onReanalyze: () -> Unit,
) {
    val report = state.report
    val similarBytes = report.similar.sumOf { it.extraBytes }
    val duplicateBytes = state.duplicates.sumOf { it.wastedBytes }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                stringResource(R.string.photos_can_free, formatBytes(similarBytes + duplicateBytes)),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        item {
            SectionCard(
                Icons.Rounded.BurstMode, BrandViolet, stringResource(R.string.section_similar),
                pluralStringResource(R.plurals.groups_count, report.similar.size, report.similar.size) + " · " + formatBytes(similarBytes),
            ) { onOpenSection(PhotoSection.SIMILAR) }
        }
        item {
            SectionCard(
                Icons.Rounded.ContentCopy, BrandTeal, stringResource(R.string.section_duplicates),
                pluralStringResource(R.plurals.groups_count, state.duplicates.size, state.duplicates.size) + " · " + formatBytes(duplicateBytes),
            ) { onOpenSection(PhotoSection.DUPLICATES) }
        }
        item {
            SectionCard(
                Icons.Rounded.BlurOn, CategoryColors.Audio, stringResource(R.string.section_blurry),
                pluralStringResource(R.plurals.photos_count, report.blurry.size, report.blurry.size) + " · " +
                    formatBytes(report.blurry.sumOf { it.photo.size }),
            ) { onOpenSection(PhotoSection.BLURRY) }
        }
        item {
            SectionCard(
                Icons.Rounded.Screenshot, BrandPink, stringResource(R.string.section_screenshots),
                pluralStringResource(R.plurals.photos_count, report.screenshots.size, report.screenshots.size) + " · " +
                    formatBytes(report.screenshots.sumOf { it.size }),
            ) { onOpenSection(PhotoSection.SCREENSHOTS) }
        }
        item {
            SectionCard(
                Icons.Rounded.Swipe, CategoryColors.Documents, stringResource(R.string.section_swipe),
                stringResource(R.string.section_swipe_sub),
            ) { onOpenSwipe() }
        }
        item {
            TextButton(onClick = onReanalyze, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.photos_reanalyze)) }
        }
    }
}

@Composable
private fun SectionCard(icon: ImageVector, color: Color, title: String, subtitle: String, onClick: () -> Unit) {
    AppCard(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, color)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(0.dp))
}
