package com.kirimly.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kirimly.app.data.model.PeerDevice
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Animated radar: concentric rings, an expanding pulse and a rotating sweep while scanning.
 * Discovered peers are laid out on the rings at stable angles derived from their id.
 */
@Composable
fun RadarPulseCanvas(
    isScanning: Boolean,
    peers: List<PeerDevice>,
    onPeerClick: (PeerDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val ringColor = MaterialTheme.colorScheme.outlineVariant
    val transition = rememberInfiniteTransition(label = "radar")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep"
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.15f),
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val radiusPx = minOf(widthPx, heightPx) / 2f * 0.92f

        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = minOf(size.width, size.height) / 2f * 0.92f
            for (i in 1..3) {
                drawCircle(
                    color = ringColor.copy(alpha = 0.7f),
                    radius = radius * i / 3f,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            drawCircle(
                color = primary.copy(alpha = 0.06f),
                radius = radius,
                center = center
            )
            if (isScanning) {
                for (offset in listOf(0f, 0.5f)) {
                    val p = (pulse + offset) % 1f
                    drawCircle(
                        color = primary.copy(alpha = (1f - p) * 0.35f),
                        radius = radius * p,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
                rotate(degrees = sweep, pivot = center) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(Color.Transparent, primary.copy(alpha = 0.05f), primary.copy(alpha = 0.32f)),
                            center = center
                        ),
                        startAngle = -60f,
                        sweepAngle = 60f,
                        useCenter = true,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)
                    )
                }
            }
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 6.dp,
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.PhoneAndroid,
                    contentDescription = "Perangkat ini",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        peers.forEachIndexed { index, peer ->
            key(peer.id) {
                val seed = abs(peer.id.hashCode())
                val angleDeg = ((seed % 360) + index * 137) % 360
                val ring = 0.55f + ((seed / 360) % 3) * 0.15f
                val angle = Math.toRadians(angleDeg.toDouble())
                val x = (cos(angle) * radiusPx * ring).toFloat()
                val y = (sin(angle) * radiusPx * ring).toFloat()
                val appearState = remember(peer.id) { MutableTransitionState(false).apply { targetState = true } }
                AnimatedVisibility(
                    visibleState = appearState,
                    enter = fadeIn() + scaleIn(),
                    modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                ) {
                    PeerBubble(peer = peer, onClick = { onPeerClick(peer) })
                }
            }
        }
    }
}

@Composable
private fun PeerBubble(peer: PeerDevice, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shadowElevation = 3.dp,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = peer.initials,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            if (peer.pinRequired) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .size(18.dp)
                        .align(Alignment.BottomEnd)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Memerlukan PIN",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(3.dp)
                    )
                }
            }
        }
        Text(
            text = peer.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 4.dp)
                .widthIn(max = 86.dp)
        )
    }
}
