package app.androcleaner.feature.photos

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.androcleaner.R
import app.androcleaner.core.format.formatBytes
import app.androcleaner.ui.components.EmptyState
import app.androcleaner.ui.components.GradientButton
import app.androcleaner.ui.theme.BrandPink
import app.androcleaner.ui.theme.BrandTeal
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Tinder-style review: swipe left to mark for deletion, right to keep. */
@Composable
fun SwipeScreen(onBack: () -> Unit, viewModel: PhotosViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val photos = (state as? PhotosState.Done)?.report?.photos?.map { it.photo }.orEmpty()
    var index by remember { mutableIntStateOf(0) }
    val marked = remember { mutableStateListOf<Photo>() }
    val history = remember { mutableStateListOf<Boolean>() } // true = marked for deletion

    Column(Modifier.fillMaxSize()) {
        DetailHeader(
            stringResource(R.string.section_swipe),
            if (photos.isNotEmpty() && index < photos.size) stringResource(R.string.swipe_progress, index + 1, photos.size) else null,
            onBack,
        )
        if (photos.isEmpty()) {
            EmptyState(Icons.Rounded.TaskAlt, stringResource(R.string.nothing_found), stringResource(R.string.nothing_found_desc))
            return@Column
        }
        if (index >= photos.size) {
            EmptyState(
                icon = Icons.Rounded.TaskAlt,
                title = stringResource(R.string.swipe_done_title),
                description = pluralStringResource(R.plurals.swipe_done_text, marked.size, marked.size, formatBytes(marked.sumOf { it.size })),
                action = if (marked.isEmpty()) null else {
                    {
                        GradientButton(
                            stringResource(R.string.move_to_trash, formatBytes(marked.sumOf { it.size })),
                            onClick = {
                                viewModel.trash(marked.map { it.path }.toSet())
                                marked.clear()
                                onBack()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
            )
            return@Column
        }

        val photo = photos[index]
        fun decide(delete: Boolean) {
            if (delete) marked += photo
            history += delete
            index++
        }

        SwipeCard(
            photo = photo,
            onDecide = ::decide,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(
                onClick = { decide(true) },
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = BrandPink),
            ) { Icon(Icons.Rounded.Close, stringResource(R.string.swipe_delete), tint = Color.White) }
            IconButton(
                onClick = {
                    if (history.isNotEmpty()) {
                        index--
                        if (history.removeAt(history.lastIndex)) marked.removeAt(marked.lastIndex)
                    }
                },
                enabled = history.isNotEmpty(),
            ) { Icon(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.swipe_undo)) }
            FilledIconButton(
                onClick = { decide(false) },
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = BrandTeal),
            ) { Icon(Icons.Rounded.Favorite, stringResource(R.string.swipe_keep), tint = Color.White) }
        }
    }
}

@Composable
private fun SwipeCard(photo: Photo, onDecide: (delete: Boolean) -> Unit, modifier: Modifier = Modifier) {
    val offset = remember(photo.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val threshold = with(LocalDensity.current) { 120.dp.toPx() }

    Box(
        modifier
            .graphicsLayer {
                translationX = offset.value
                rotationZ = offset.value / 40f
            }
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(photo.id) {
                detectDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (abs(offset.value) > threshold) {
                                val delete = offset.value < 0
                                offset.animateTo(if (delete) -size.width * 1.5f else size.width * 1.5f, tween(180))
                                onDecide(delete)
                            } else {
                                offset.animateTo(0f)
                            }
                        }
                    },
                ) { change, drag ->
                    change.consume()
                    scope.launch { offset.snapTo(offset.value + drag.x) }
                }
            },
    ) {
        AsyncImage(photo.uri, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        val progress = (offset.value / threshold).coerceIn(-1f, 1f)
        if (progress != 0f) {
            val delete = progress < 0
            Text(
                stringResource(if (delete) R.string.swipe_delete else R.string.swipe_keep),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .align(if (delete) Alignment.TopEnd else Alignment.TopStart)
                    .padding(20.dp)
                    .graphicsLayer { alpha = abs(progress) }
                    .background(if (delete) BrandPink else BrandTeal, CircleShape)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}
