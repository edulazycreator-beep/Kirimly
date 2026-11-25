package com.kirimly.app.ui.components

import com.kirimly.app.R
import com.kirimly.app.util.tr
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * The Kirimly paper-plane mark, drawn on a 108-unit grid (same geometry as the launcher and
 * splash icons). It glides in once, then floats gently while its trail streams behind it.
 */
@Composable
fun AnimatedKirimlyMark(
    modifier: Modifier = Modifier,
    floating: Boolean = true
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val fold = MaterialTheme.colorScheme.outline
    val trailColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)

    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
    }
    val transition = rememberInfiniteTransition(label = "mark")
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob"
    )
    val dash by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "dash"
    )

    Canvas(modifier = modifier.semantics { contentDescription = tr(R.string.s_logo_kirimly) }) {
        val unit = size.minDimension / GRID
        val originX = (size.width - GRID * unit) / 2f
        val originY = (size.height - GRID * unit) / 2f
        val e = entrance.value
        val float = if (floating) (bob - 0.5f) else 0f
        val pivot = Offset(54f * unit, 56f * unit)

        val body = Path().apply {
            moveTo(34f * unit, 52.5f * unit)
            lineTo(74f * unit, 36f * unit)
            lineTo(60.5f * unit, 76f * unit)
            lineTo(52.5f * unit, 59.5f * unit)
            close()
        }
        val crease = Path().apply {
            moveTo(52.5f * unit, 59.5f * unit)
            lineTo(74f * unit, 36f * unit)
            lineTo(56.5f * unit, 63.5f * unit)
            close()
        }
        val trail = Path().apply {
            moveTo(30f * unit, 66f * unit)
            cubicTo(35f * unit, 70f * unit, 41f * unit, 71f * unit, 46f * unit, 69f * unit)
        }

        translate(
            left = originX + (1f - e) * -24f * unit,
            top = originY + (1f - e) * 20f * unit + float * 6f * unit
        ) {
            drawPath(
                path = trail,
                color = trailColor.copy(alpha = trailColor.alpha * e.coerceIn(0f, 1f)),
                style = Stroke(
                    width = 2.5f * unit,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * unit, 4f * unit), -dash * 7f * unit)
                )
            )
            rotate(degrees = (1f - e) * -14f + float * 4f, pivot = pivot) {
                scale(scale = 0.55f + 0.45f * e, pivot = pivot) {
                    drawPath(body, color = ink)
                    drawPath(crease, color = fold)
                }
            }
        }
    }
}

private const val GRID = 108f
