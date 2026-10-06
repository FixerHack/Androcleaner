package app.androcleaner.feature.storage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class DonutSegment(val value: Float, val color: Color)

/** Animated ring chart; [total] may exceed the sum of segments (the rest is drawn as track). */
@Composable
fun DonutChart(
    segments: List<DonutSegment>,
    total: Float,
    trackColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    thickness: Dp = 22.dp,
    center: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(segments) { progress.animateTo(1f, tween(900)) }

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = thickness.toPx()
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (total <= 0f) return@Canvas
            val gap = 2.5f
            var start = -90f
            for (segment in segments) {
                val sweep = 360f * (segment.value / total) * progress.value
                if (sweep > gap) {
                    drawArc(
                        segment.color, start + gap / 2, sweep - gap, false, topLeft, arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Butt),
                    )
                }
                start += sweep
            }
        }
        center()
    }
}
