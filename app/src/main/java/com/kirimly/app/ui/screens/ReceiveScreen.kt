package com.kirimly.app.ui.screens

import com.kirimly.app.R
import com.kirimly.app.util.tr
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.RectangleShape
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kirimly.app.data.model.HotspotState
import com.kirimly.app.data.model.ServerState
import com.kirimly.app.data.model.TransferProgress
import com.kirimly.app.ui.MainViewModel
import com.kirimly.app.network.WifiJoiner
import com.kirimly.app.ui.components.QrCodeImage
import com.kirimly.app.ui.components.QrDisplayCard
import com.kirimly.app.ui.components.TransferProgressCard
import com.kirimly.app.ui.theme.MonoDigits

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val serverState by viewModel.serverState.collectAsStateWithLifecycle()
    val pin by viewModel.pin.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val qrMode by viewModel.qrMode.collectAsStateWithLifecycle()
    val hotspotState by viewModel.hotspotState.collectAsStateWithLifecycle()
    val wifiPayload by viewModel.wifiPayload.collectAsStateWithLifecycle()
    val transfers by viewModel.activeTransfers.collectAsStateWithLifecycle()
    val qrServerUrl by viewModel.qrServerUrl.collectAsStateWithLifecycle()
    val localHostUrl by viewModel.localHostUrl.collectAsStateWithLifecycle()

    val hotspotPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) viewModel.startHotspot()
        else viewModel.showMessage(tr(R.string.s_izin_ditolak_gunakan_hotspot_sistem))
    }

    val wifiJoinState by viewModel.wifiJoinState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshAddress()
        viewModel.autoHotspotIfOffline()
    }

    val running = serverState as? ServerState.Running
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(tr(R.string.s_terima), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            tr(R.string.s_dari_iphone_ipad_atau_android),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Palette, contentDescription = tr(R.string.s_tema_pengaturan))
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
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "status") {
                ServerStatusCard(
                    serverState = serverState,
                    pin = pin,
                    pinRequired = preferences.pinRequired,
                    onToggle = viewModel::toggleServer,
                    onCopy = { text -> viewModel.copyToClipboard(text) },
                    onRefreshPin = viewModel::refreshPin,
                    onPinRequiredChange = viewModel::setPinRequired
                )
            }

            item(key = "qr") {
                QrDisplayCard(
                    mode = qrMode,
                    onModeChange = viewModel::setQrMode,
                    serverUrl = qrServerUrl,
                    wifiPayload = wifiPayload,
                    serverCaption = tr(R.string.s_pindai_dengan_kamera_iphone_lalu),
                    wifiCaption = tr(R.string.s_langkah_1_pindai_dengan_kamera)
                ) {
                    HotspotControls(
                        state = hotspotState,
                        hasPayload = wifiPayload != null,
                        serverUrl = qrServerUrl,
                        joinState = wifiJoinState,
                        onJoinIphone = viewModel::joinIphoneHotspot,
                        onLeaveIphone = viewModel::leaveIphoneHotspot,
                        onStart = {
                            val permission = viewModel.hotspotPermission()
                            val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                viewModel.startHotspot()
                            } else {
                                val request = if (permission == android.Manifest.permission.ACCESS_FINE_LOCATION) {
                                    arrayOf(permission, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                                } else {
                                    arrayOf(permission)
                                }
                                hotspotPermissionLauncher.launch(request)
                            }
                        },
                        onStop = viewModel::stopHotspot,
                        onOpenSettings = { openHotspotSettings(context) },
                        onManual = viewModel::setManualHotspot,
                        onReset = viewModel::resetHotspot
                    )
                }
            }

            if (running != null && !running.hasNetwork) {
                item(key = "noNetwork") {
                    InfoBanner(tr(R.string.s_tidak_ada_jaringan_wi_fi))
                }
            }

            if (running != null && running.hasNetwork) {
                item(key = "connectionNotes") {
                    ConnectionNotes(localHostUrl = localHostUrl, onCopy = viewModel::copyToClipboard)
                }
            }

            item(key = "steps") { HowToCard() }

            if (transfers.isNotEmpty()) {
                item(key = "transfersHeader") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text(tr(R.string.s_transfer_aktif), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (transfers.any { it.isFinished }) {
                            TextButton(onClick = viewModel::clearFinishedTransfers) { Text(tr(R.string.s_bersihkan)) }
                        }
                    }
                }
                items(transfers, key = { it.id }) { progress: TransferProgress ->
                    TransferProgressCard(progress = progress, onDismiss = { viewModel.dismissTransfer(progress.id) })
                }
            }
        }
    }
}

@Composable
private fun ServerStatusCard(
    serverState: ServerState,
    pin: String,
    pinRequired: Boolean,
    onToggle: () -> Unit,
    onCopy: (String) -> Unit,
    onRefreshPin: () -> Unit,
    onPinRequiredChange: (Boolean) -> Unit
) {
    val running = serverState as? ServerState.Running
    val dotColor by animateColorAsState(
        targetValue = when (serverState) {
            is ServerState.Running -> MaterialTheme.colorScheme.tertiary
            ServerState.Starting -> MaterialTheme.colorScheme.secondary
            is ServerState.Error -> MaterialTheme.colorScheme.error
            ServerState.Stopped -> MaterialTheme.colorScheme.outline
        },
        label = "statusDot"
    )
    Card(
        shape = RectangleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = if (running != null) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(dotColor, CircleShape)
                )
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (serverState) {
                            is ServerState.Running -> tr(R.string.s_server_aktif)
                            ServerState.Starting -> tr(R.string.s_menyalakan)
                            is ServerState.Error -> tr(R.string.s_server_bermasalah)
                            ServerState.Stopped -> tr(R.string.s_server_nonaktif)
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = when (serverState) {
                            is ServerState.Running -> tr(R.string.s_siap_menerima_file_teks)
                            ServerState.Starting -> tr(R.string.s_mencari_port_kosong)
                            is ServerState.Error -> serverState.message
                            ServerState.Stopped -> tr(R.string.s_nyalakan_untuk_mulai_menerima)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (serverState == ServerState.Starting) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                } else {
                    FilledTonalIconButton(onClick = onToggle, modifier = Modifier.size(52.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.PowerSettingsNew,
                            contentDescription = if (running != null) tr(R.string.s_matikan_server) else tr(R.string.s_nyalakan_server)
                        )
                    }
                }
            }

            if (running != null) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 18.dp, end = 4.dp)
                    ) {
                        Text(
                            text = "${running.ip}:${running.port}",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onCopy(running.url) }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = tr(R.string.s_salin_alamat))
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        shape = RectangleShape,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (pinRequired) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.size(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tr(R.string.s_pin_sesi), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = if (pinRequired) pin else tr(R.string.s_nonaktif),
                                    style = if (pinRequired) MonoDigits else MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (pinRequired) {
                                IconButton(onClick = onRefreshPin) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = tr(R.string.s_pin_baru))
                                }
                            }
                        }
                    }
                    Switch(checked = pinRequired, onCheckedChange = onPinRequiredChange)
                }
            } else {
                Button(
                    onClick = onToggle,
                    enabled = serverState != ServerState.Starting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(tr(R.string.s_mulai_server))
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.HotspotControls(
    state: HotspotState,
    hasPayload: Boolean,
    serverUrl: String?,
    joinState: WifiJoiner.State,
    onJoinIphone: (String, String) -> Unit,
    onLeaveIphone: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit,
    onManual: (String, String) -> Unit,
    onReset: () -> Unit
) {
    var ssid by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    when (state) {
        HotspotState.Idle -> {
            Text(
                tr(R.string.s_tanpa_wi_fi_buat_hotspot),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Rounded.WifiTethering, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(tr(R.string.s_aktifkan_hotspot_lokal))
            }
            TextButton(onClick = onOpenSettings) { Text(tr(R.string.s_pakai_hotspot_sistem_isi_manual)) }
            ManualHotspotForm(ssid, { ssid = it }, password, { password = it }, onManual)
        }
        HotspotState.Starting -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                Spacer(Modifier.size(12.dp))
                Text(tr(R.string.s_menyiapkan_hotspot), style = MaterialTheme.typography.bodyMedium)
            }
        }
        is HotspotState.Active -> {
            CredentialsBox(ssid = state.ssid, password = state.password)
            StepTwo(serverUrl)
            OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                Text(tr(R.string.s_matikan_hotspot))
            }
        }
        is HotspotState.Manual -> {
            CredentialsBox(ssid = state.ssid, password = state.password)
            StepTwo(serverUrl)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onOpenSettings, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(tr(R.string.s_pengaturan))
                }
                FilledTonalButton(onClick = onStop, modifier = Modifier.weight(1f)) {
                    Text(tr(R.string.s_ubah))
                }
            }
        }
        is HotspotState.Failed -> {
            InfoBanner(state.reason)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                    Text(tr(R.string.s_coba_lagi))
                }
                FilledTonalButton(onClick = onOpenSettings, modifier = Modifier.weight(1f)) {
                    Text(tr(R.string.s_hotspot_sistem))
                }
            }
            ManualHotspotForm(ssid, { ssid = it }, password, { password = it }, onManual)
        }
    }
    if (!hasPayload && state is HotspotState.Active) {
        Text(tr(R.string.s_ssid_tidak_terbaca), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
    }
    if (state is HotspotState.Idle || state is HotspotState.Failed) {
        IphoneHotspotJoin(joinState = joinState, onJoin = onJoinIphone, onLeave = onLeaveIphone)
    }
}

/** Second scan of the two-scan iPhone flow: once on the hotspot, open Kirimly in Safari. */
@Composable
private fun StepTwo(serverUrl: String?) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text(
        tr(R.string.s_langkah_2_setelah_iphone_tersambung),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    if (serverUrl != null) {
        QrCodeImage(content = serverUrl)
    } else {
        Text(tr(R.string.s_menunggu_server_aktif_di_hotspot), style = MaterialTheme.typography.labelMedium)
    }
}

/** Reverse direction: join the iPhone's Personal Hotspot so the iPhone keeps its mobile data. */
@Composable
private fun IphoneHotspotJoin(
    joinState: WifiJoiner.State,
    onJoin: (String, String) -> Unit,
    onLeave: () -> Unit
) {
    var ssid by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    Surface(
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr(R.string.s_atau_gabung_ke_personal_hotspot), style = MaterialTheme.typography.titleSmall)
            Text(
                tr(R.string.s_nyalakan_personal_hotspot_di_iphone),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when (joinState) {
                is WifiJoiner.State.Connected -> {
                    Text(tr(R.string.s_tersambung_ke_pindai_qr_url, joinState.ssid), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) { Text(tr(R.string.s_putuskan)) }
                }
                is WifiJoiner.State.Connecting -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                        Text(tr(R.string.s_menyambung_ke_setujui_dialog_android, joinState.ssid), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                else -> {
                    if (joinState is WifiJoiner.State.Failed) {
                        Text(joinState.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedTextField(
                        value = ssid,
                        onValueChange = { ssid = it },
                        label = { Text(tr(R.string.s_nama_hotspot_iphone)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(tr(R.string.s_kata_sandi)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onJoin(ssid, password) },
                        enabled = ssid.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(tr(R.string.s_gabung)) }
                }
            }
        }
    }
}

@Composable
private fun ManualHotspotForm(
    ssid: String,
    onSsidChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    onSubmit: (String, String) -> Unit
) {
    Surface(
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr(R.string.s_qr_wi_fi_manual), style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = ssid,
                onValueChange = onSsidChange,
                label = { Text(tr(R.string.s_nama_hotspot_ssid)) },
                singleLine = true,
                shape = RectangleShape,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text(tr(R.string.s_kata_sandi)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RectangleShape,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { onSubmit(ssid, password) },
                enabled = ssid.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(tr(R.string.s_buat_qr_wi_fi)) }
        }
    }
}

@Composable
private fun CredentialsBox(ssid: String, password: String) {
    Surface(
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LabeledValue("SSID", ssid)
            LabeledValue(tr(R.string.s_kata_sandi), password.ifEmpty { tr(R.string.s_tanpa_sandi) })
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.65f)
        )
    }
}

@Composable
private fun InfoBanner(text: String) {
    Surface(
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(14.dp)
        )
    }
}

@Composable
private fun HowToCard() {
    Card(
        shape = RectangleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr(R.string.s_cara_menerima_dari_iphone), style = MaterialTheme.typography.titleMedium)
            listOf(
                tr(R.string.s_nyalakan_server_dan_pastikan_kedua),
                tr(R.string.s_pindai_qr_url_safari_dengan),
                tr(R.string.s_pilih_file_di_safari_dan)
            ).forEachIndexed { index, step ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(26.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Text(
                        step,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private fun openHotspotSettings(context: Context) {
    val candidates = listOf(
        Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings"),
        Intent("android.settings.TETHER_SETTINGS"),
        Intent(Settings.ACTION_WIRELESS_SETTINGS),
        Intent(Settings.ACTION_SETTINGS)
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

@Composable
private fun ConnectionNotes(localHostUrl: String?, onCopy: (String) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RectangleShape)
    ) {
        if (localHostUrl != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(tr(R.string.s_alamat_untuk_iphone_ipad_mac), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(localHostUrl, style = MaterialTheme.typography.titleSmall)
                }
                IconButton(onClick = { onCopy(localHostUrl) }) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = tr(R.string.s_salin_alamat_kirimly_local))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.size(12.dp))
            Text(
                tr(R.string.s_android_ke_android_terenkripsi_end) +
                    tr(R.string.s_gunakan_di_jaringan_yang_kamu),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
