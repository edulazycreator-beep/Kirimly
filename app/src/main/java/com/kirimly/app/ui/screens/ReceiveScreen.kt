package com.kirimly.app.ui.screens

import com.kirimly.app.ui.components.TransferSection
import com.kirimly.app.ui.theme.MonoDigits
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.Surface
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kirimly.app.R
import com.kirimly.app.data.model.ServerState
import com.kirimly.app.data.model.TransferProgress
import com.kirimly.app.ui.MainViewModel
import com.kirimly.app.ui.components.QrCodeImage
import com.kirimly.app.ui.components.TransferProgressCard
import com.kirimly.app.util.tr

/** Receive = always on. One QR for Apple devices; Android devices find this phone by themselves. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val serverState by viewModel.serverState.collectAsStateWithLifecycle()
    val qrUrl by viewModel.qrUrl.collectAsStateWithLifecycle()
    val pin by viewModel.pin.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val transfers by viewModel.receivingTransfers.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.ensureServerRunning()
        viewModel.refreshAddress()
    }

    val running = serverState as? ServerState.Running
    val online = running?.hasNetwork == true
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(tr(R.string.u_receive_title), style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = tr(R.string.u_settings))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "status") { StatusLine(serverState = serverState, deviceName = preferences.deviceNickname) }

            if (transfers.isNotEmpty()) {
                item(key = "receiving") {
                    TransferSection(
                        title = tr(R.string.u_receiving_now),
                        transfers = transfers,
                        onDismiss = viewModel::dismissTransfer,
                        onClearFinished = viewModel::clearFinishedTransfers
                    )
                }
            }

            if (!online) {
                item(key = "offline") {
                    StepCard(number = null, title = tr(R.string.u_offline_title)) {
                        if (serverState is ServerState.Starting) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                        Text(tr(R.string.u_offline_body), style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { openSettings(context, Settings.ACTION_WIFI_SETTINGS) }, modifier = Modifier.weight(1f)) {
                                Text(tr(R.string.u_open_wifi))
                            }
                            OutlinedButton(onClick = { openHotspotSettings(context) }, modifier = Modifier.weight(1f)) {
                                Text(tr(R.string.u_open_hotspot))
                            }
                        }
                    }
                }
            } else {
                item(key = "apple") {
                    StepCard(number = null, title = tr(R.string.u_from_apple)) {
                        val url = qrUrl
                        if (url != null) {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                QrCodeImage(content = url, maxSize = 200.dp)
                            }
                        }
                        Text(
                            tr(R.string.u_from_apple_steps),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ManualEntry(
                            address = running?.let { "${it.ip}:${it.port}" }.orEmpty(),
                            pin = if (preferences.pinRequired) pin else null,
                            onCopy = { running?.let { viewModel.copyToClipboard(it.url) } },
                            onNewPin = viewModel::refreshPin
                        )
                    }
                }
                item(key = "android") {
                    StepCard(number = null, title = tr(R.string.u_from_android)) {
                        Text(
                            tr(R.string.u_from_android_body, preferences.deviceNickname),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item(key = "security") {
                Text(
                    tr(R.string.u_security_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusLine(serverState: ServerState, deviceName: String) {
    val (color, text) = when (serverState) {
        is ServerState.Running ->
            if (serverState.hasNetwork) MaterialTheme.colorScheme.tertiary to tr(R.string.u_status_ready, deviceName)
            else MaterialTheme.colorScheme.error to tr(R.string.u_status_no_wifi)
        ServerState.Starting -> MaterialTheme.colorScheme.outline to tr(R.string.u_status_starting)
        is ServerState.Error -> MaterialTheme.colorScheme.error to tr(R.string.u_status_error, serverState.message)
        ServerState.Stopped -> MaterialTheme.colorScheme.outline to tr(R.string.u_status_starting)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.size(10.dp))
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun ManualEntry(address: String, pin: String?, onCopy: () -> Unit, onNewPin: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            tr(R.string.u_type_in_safari),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Address: high-contrast inverse plate so it reads at a glance from another device.
        Surface(
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.inverseSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp, end = 0.dp)) {
                // Safari adds http:// itself, so the shorter form always fits on one line.
                Text(
                    address,
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    softWrap = false,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 10.dp)
                )
                IconButton(onClick = onCopy) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = tr(R.string.u_copy_address),
                        tint = MaterialTheme.colorScheme.inverseOnSurface
                    )
                }
            }
        }
        if (pin != null) {
            Text(
                tr(R.string.u_then_enter_code),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pin.forEach { digit ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(width = 40.dp, height = 48.dp)
                            .border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary), RectangleShape)
                    ) {
                        Text(digit.toString(), style = MonoDigits.copy(fontSize = 22.sp, letterSpacing = 0.sp))
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onNewPin) { Icon(Icons.Rounded.Refresh, contentDescription = tr(R.string.u_new_code)) }
            }
        }
    }
}

private fun openSettings(context: Context, action: String) {
    try {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun openHotspotSettings(context: Context) {
    val candidates = listOf(
        Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings"),
        Intent("android.settings.TETHER_SETTINGS"),
        Intent(Settings.ACTION_WIRELESS_SETTINGS)
    )
    for (intent in candidates) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
    }
}
