package com.kirimly.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kirimly.app.data.model.TransferDirection
import com.kirimly.app.data.model.TransferProgress
import com.kirimly.app.data.model.TransferStatus
import com.kirimly.app.network.NetworkUtils

@Composable
fun TransferProgressCard(
    progress: TransferProgress,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animated by animateFloatAsState(targetValue = progress.fraction, label = "transferProgress")
    val failed = progress.status == TransferStatus.FAILED || progress.status == TransferStatus.DECLINED
    val done = progress.status == TransferStatus.COMPLETED
    val accent = when {
        failed -> MaterialTheme.colorScheme.error
        done -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accent.copy(alpha = 0.14f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when {
                                failed -> Icons.Rounded.ErrorOutline
                                done -> Icons.Rounded.CheckCircle
                                progress.direction == TransferDirection.RECEIVED -> Icons.Rounded.ArrowDownward
                                else -> Icons.Rounded.ArrowUpward
                            },
                            contentDescription = null,
                            tint = accent
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = progress.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = (if (progress.direction == TransferDirection.RECEIVED) "Dari " else "Ke ") + progress.peerName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (progress.isFinished) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Tutup")
                    }
                } else {
                    Text(
                        text = "${(animated * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        color = accent
                    )
                }
            }
            LinearProgressIndicator(
                progress = { if (done) 1f else animated },
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val sizeText = if (progress.totalBytes > 0L) {
                    NetworkUtils.formatFileSize(progress.transferredBytes) + " / " + NetworkUtils.formatFileSize(progress.totalBytes)
                } else {
                    NetworkUtils.formatFileSize(progress.transferredBytes)
                }
                Text(sizeText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = when (progress.status) {
                        TransferStatus.COMPLETED -> "Selesai • " + NetworkUtils.formatSpeed(progress.bytesPerSecond)
                        TransferStatus.FAILED -> progress.errorMessage ?: "Gagal"
                        TransferStatus.DECLINED -> "Ditolak"
                        TransferStatus.AWAITING_APPROVAL -> "Menunggu persetujuan"
                        TransferStatus.QUEUED -> "Dalam antrean"
                        TransferStatus.IN_PROGRESS -> NetworkUtils.formatSpeed(progress.bytesPerSecond)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    }
}
