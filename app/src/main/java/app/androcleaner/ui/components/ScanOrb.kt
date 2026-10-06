package app.androcleaner.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.androcleaner.ui.theme.BrandPink
import app.androcleaner.ui.theme.BrandTeal
import app.androcleaner.ui.theme.BrandViolet

/**
 * The big gradient "orb" in the middle of the scan screen.
 * Breathes while idle, spins a comet ring while [active].
 */
@Composable
fun ScanOrb(
    active: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    size: Dp = 232.dp,
    content: @Composable () -> Unit,
) {
    // Animate only while scanning: an always-on 60 fps animation would waste battery.
    var rotation = -120f
    var breath = 1f
    if (active) {
        val transition = rememberInfiniteTransition(label = "orb")
        rotation = transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
            label = "rotation",
        ).value
        breath = transition.animateFloat(
            initialValue = 0.97f,
            targetValue = 1.03f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "breath",
        ).value
    }

    Box(modifier.size(size + 36.dp), contentAlignment = Alignment.Center) {
        // Soft glow
        Canvas(Modifier.size(size + 36.dp).scale(breath)) {
            drawCircle(
                Brush.radialGradient(
                    listOf(BrandViolet.copy(alpha = 0.35f), BrandTeal.copy(alpha = 0.12f), Color.Transparent),
                ),
            )
        }
        // Rotating ring
        Canvas(Modifier.size(size + 14.dp).rotate(rotation)) {
            val stroke = 5.dp.toPx()
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(Color.Transparent, BrandTeal.copy(alpha = if (active) 1f else 0.5f), BrandViolet, Color.Transparent),
                ),
                startAngle = 0f,
                sweepAngle = 300f,
                useCenter = false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = this.size.copy(this.size.width - stroke, this.size.height - stroke),
                style = Stroke(stroke),
            )
        }
        // Core
        Box(
            Modifier
                .size(size)
                
                .clip(CircleShape)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = Color.White),
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(Brush.linearGradient(listOf(BrandViolet, BrandPink.copy(alpha = 0.85f), BrandTeal), start = Offset.Zero, end = Offset(this.size.width, this.size.height)))
                // Glossy highlight
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.35f), Color.Transparent),
                        center = Offset(this.size.width * 0.32f, this.size.height * 0.25f),
                        radius = this.size.width * 0.55f,
                    ),
                )
            }
            content()
        }
    }
}
