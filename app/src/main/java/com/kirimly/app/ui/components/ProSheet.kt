package com.kirimly.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ManageSearch
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kirimly.app.BuildConfig
import com.kirimly.app.R
import com.kirimly.app.billing.ProManager
import com.kirimly.app.util.tr

private data class ProFeature(val icon: ImageVector, val title: Int, val body: Int)

@Suppress("DEPRECATION")
private val proFeatures = listOf(
    ProFeature(Icons.Rounded.DoneAll, R.string.p_f_auto, R.string.p_f_auto_body),
    ProFeature(Icons.Rounded.Devices, R.string.p_f_multi, R.string.p_f_multi_body),
    ProFeature(Icons.Rounded.Folder, R.string.p_f_folder, R.string.p_f_folder_body),
    ProFeature(Icons.Rounded.ContentPaste, R.string.p_f_clip, R.string.p_f_clip_body),
    ProFeature(Icons.Rounded.ManageSearch, R.string.p_f_history, R.string.p_f_history_body),
    ProFeature(Icons.Rounded.Palette, R.string.p_f_look, R.string.p_f_look_body)
)

/** Kirimly Pro: what it unlocks, one purchase button, restore. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProSheet(
    isPro: Boolean,
    trialRemainingMs: Long?,
    price: String?,
    status: ProManager.Status,
    debugPro: Boolean,
    onBuy: () -> Unit,
    onRestore: () -> Unit,
    onDebugPro: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Kirimly", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.size(10.dp))
                    ProBadge()
                }
                Text(
                    tr(if (isPro) R.string.p_active_body else R.string.p_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column {
                proFeatures.forEach { feature ->
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(feature.icon, contentDescription = null, modifier = Modifier.size(22.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 14.dp)
                        ) {
                            Text(tr(feature.title), style = MaterialTheme.typography.titleSmall)
                            Text(
                                tr(feature.body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isPro) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            Text(
                tr(R.string.p_free_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isPro) {
                Text(
                    tr(R.string.p_active),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (trialRemainingMs != null) {
                        Text(
                            tr(R.string.t_trial_left, formatRemaining(trialRemainingMs)),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Button(
                        onClick = onBuy,
                        enabled = status == ProManager.Status.READY && price != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        when {
                            status == ProManager.Status.CONNECTING || status == ProManager.Status.PURCHASING ->
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            price != null -> Text(tr(R.string.p_buy, price))
                            else -> Text(tr(R.string.p_unavailable))
                        }
                    }
                    Text(
                        tr(
                            when (status) {
                                ProManager.Status.PENDING -> R.string.p_pending
                                ProManager.Status.UNAVAILABLE -> R.string.p_unavailable_body
                                else -> R.string.p_once
                            }
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            TextButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) { Text(tr(R.string.p_restore)) }

            if (BuildConfig.DEBUG) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tr(R.string.p_debug), style = MaterialTheme.typography.titleSmall)
                        Text(
                            tr(R.string.p_debug_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = debugPro, onCheckedChange = onDebugPro)
                }
            }
        }
    }
}

/** Small "PRO" pill used next to locked features. */
@Composable
fun ProBadge(modifier: Modifier = Modifier, locked: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        if (locked) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                modifier = Modifier.size(11.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(3.dp))
        }
        Text("PRO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "2 hari" / "5 jam" / "<1 jam" for the trial countdown. */
fun formatRemaining(ms: Long): String {
    val hours = ms / 3_600_000L
    return when {
        hours >= 24 -> tr(R.string.t_days, ((hours + 12) / 24).toInt())
        hours >= 1 -> tr(R.string.t_hours, hours.toInt())
        else -> tr(R.string.t_under_hour)
    }
}
