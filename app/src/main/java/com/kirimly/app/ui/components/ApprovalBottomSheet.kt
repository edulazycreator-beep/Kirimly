package com.kirimly.app.ui.components

import com.kirimly.app.R
import com.kirimly.app.util.tr
import androidx.compose.ui.unit.sp
import com.kirimly.app.ui.theme.MonoDigits
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kirimly.app.data.model.PendingTransferRequest
import com.kirimly.app.network.NetworkUtils
import kotlinx.coroutines.delay

/** Accept / Decline sheet shown whenever a remote client asks to upload files. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalBottomSheet(
    request: PendingTransferRequest,
    onAccept: (trust: Boolean) -> Unit,
    onDecline: () -> Unit,
    autoAcceptTrusted: Boolean = true
) {
    var trust by remember(request.id) { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var now by remember(request.id) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(request.id) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= request.expiresAt) break
            delay(500L)
        }
    }
    val totalWindow = (request.expiresAt - request.createdAt).coerceAtLeast(1L)
    val remainingMs = (request.expiresAt - now).coerceAtLeast(0L)
    val remainingFraction = remainingMs.toFloat() / totalWindow.toFloat()

    ModalBottomSheet(
        onDismissRequest = onDecline,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(
                    shape = RectangleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Devices,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tr(R.string.s_ingin_mengirim, request.deviceName),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = tr(R.string.s_file, request.files.size, NetworkUtils.formatFileSize(request.totalSize), request.remoteAddress),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RectangleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    val visible = request.files.take(MAX_VISIBLE)
                    visible.forEachIndexed { index, file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = fileIconFor(file.mimeType, file.name),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)
                            )
                            Text(
                                text = NetworkUtils.formatFileSize(file.sizeBytes),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (index < visible.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                    if (request.files.size > MAX_VISIBLE) {
                        Text(
                            text = tr(R.string.s_dan_file_lainnya, request.files.size - MAX_VISIBLE),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            Text(
                text = (if (request.channel == "nearby") tr(R.string.s_lewat_nearby_bluetooth_wi_fi) else "Perangkat: ") +
                    request.userAgent.ifBlank { tr(R.string.s_tidak_diketahui) },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(
                    progress = { remainingFraction },
                    modifier = Modifier.fillMaxWidth(),
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
                Text(
                    text = tr(R.string.s_otomatis_ditolak_dalam_detik, remainingMs / 1000),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!request.deviceId.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = trust, role = Role.Checkbox, onValueChange = { trust = it })
                ) {
                    Checkbox(checked = trust, onCheckedChange = null)
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(tr(R.string.s_percayai_perangkat_ini), style = MaterialTheme.typography.titleSmall)
                        Text(
                            tr(if (autoAcceptTrusted) R.string.s_kiriman_berikutnya_dari_diterima_otomatis else R.string.p_trust_free_body, request.deviceName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDecline,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) { Text(tr(R.string.s_tolak)) }
                Button(
                    onClick = { onAccept(trust) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) { Text(tr(R.string.s_terima)) }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

private const val MAX_VISIBLE = 5
