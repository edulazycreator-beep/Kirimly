@file:Suppress("DEPRECATION")

package com.kirimly.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kirimly.app.data.model.PeerDevice
import com.kirimly.app.data.model.ServerState
import com.kirimly.app.data.model.SharedFile
import com.kirimly.app.data.model.SharedItem
import com.kirimly.app.data.model.SharedText
import com.kirimly.app.network.NetworkUtils
import com.kirimly.app.ui.MainViewModel
import com.kirimly.app.ui.components.RadarPulseCanvas
import com.kirimly.app.ui.components.fileIconFor

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit
) {
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val isDiscovering by viewModel.isDiscovering.collectAsStateWithLifecycle()
    val queue by viewModel.sharedItems.collectAsStateWithLifecycle()
    val totalBytes by viewModel.queueTotalBytes.collectAsStateWithLifecycle()
    val serverState by viewModel.serverState.collectAsStateWithLifecycle()
    val sendingPeerId by viewModel.sendingPeerId.collectAsStateWithLifecycle()

    var textDraft by rememberSaveable { mutableStateOf("") }
    var pinTarget by remember { mutableStateOf<PeerDevice?>(null) }
    var confirmTarget by remember { mutableStateOf<PeerDevice?>(null) }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.addUris(uris)
    }

    DisposableEffect(Unit) {
        viewModel.startDiscovery()
        onDispose { viewModel.stopDiscovery() }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Kirim", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Bagikan ke perangkat terdekat",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Palette, contentDescription = "Tema & pengaturan")
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
            item(key = "radar") {
                RadarCard(
                    peers = peers,
                    isDiscovering = isDiscovering,
                    serverState = serverState,
                    sendingPeerId = sendingPeerId,
                    onPeerClick = { peer ->
                        if (queue.isEmpty()) {
                            viewModel.showMessage("Antrean kosong. Tambahkan file atau teks dulu.")
                        } else if (peer.pinRequired) {
                            pinTarget = peer
                        } else {
                            confirmTarget = peer
                        }
                    },
                    onRescan = viewModel::rescan,
                    onToggleServer = viewModel::toggleServer
                )
            }

            item(key = "actions") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { pickFiles.launch(arrayOf("*/*")) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                    ) {
                        Icon(Icons.Rounded.AttachFile, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Pilih File")
                    }
                    FilledTonalButton(
                        onClick = {
                            val clip = viewModel.readClipboardText()
                            if (clip == null) viewModel.showMessage("Clipboard kosong") else viewModel.addText(clip)
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                    ) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Clipboard")
                    }
                }
            }

            item(key = "composer") {
                TextComposerCard(
                    text = textDraft,
                    onTextChange = { textDraft = it },
                    onPaste = {
                        val clip = viewModel.readClipboardText()
                        if (clip == null) viewModel.showMessage("Clipboard kosong") else textDraft = clip
                    },
                    onAdd = { if (viewModel.addText(textDraft)) textDraft = "" }
                )
            }

            item(key = "queueHeader") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text("Antrean", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.size(8.dp))
                    if (queue.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${queue.size} item • ${NetworkUtils.formatFileSize(totalBytes)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (queue.isNotEmpty()) {
                        TextButton(onClick = viewModel::clearQueue) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Kosongkan")
                        }
                    }
                }
            }

            if (queue.isEmpty()) {
                item(key = "empty") { EmptyQueue() }
            } else {
                items(queue, key = { it.id }) { item ->
                    QueueItemRow(item = item, onRemove = { viewModel.removeItem(item.id) })
                }
            }
        }
    }

    pinTarget?.let { peer ->
        PinEntryDialog(
            peer = peer,
            onDismiss = { pinTarget = null },
            onConfirm = { pin ->
                pinTarget = null
                viewModel.sendToPeer(peer, pin)
            }
        )
    }

    confirmTarget?.let { peer ->
        AlertDialog(
            onDismissRequest = { confirmTarget = null },
            title = { Text("Kirim ke ${peer.name}?") },
            text = { Text("${queue.size} item (${NetworkUtils.formatFileSize(totalBytes)}) akan dikirim. Penerima perlu menyetujui di perangkatnya.") },
            confirmButton = {
                Button(onClick = {
                    confirmTarget = null
                    viewModel.sendToPeer(peer, null)
                }) { Text("Kirim") }
            },
            dismissButton = { TextButton(onClick = { confirmTarget = null }) { Text("Batal") } }
        )
    }
}

@Composable
private fun RadarCard(
    peers: List<PeerDevice>,
    isDiscovering: Boolean,
    serverState: ServerState,
    sendingPeerId: String?,
    onPeerClick: (PeerDevice) -> Unit,
    onRescan: () -> Unit,
    onToggleServer: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Radar perangkat", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = when {
                            sendingPeerId != null -> "Mengirim… tunggu persetujuan penerima"
                            peers.isNotEmpty() -> "${peers.size} perangkat Kirimly ditemukan — ketuk untuk mengirim"
                            isDiscovering -> "Mencari perangkat Kirimly di jaringan ini…"
                            else -> "Pemindaian berhenti"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (sendingPeerId != null) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                } else {
                    IconButton(onClick = onRescan) { Icon(Icons.Rounded.Refresh, contentDescription = "Pindai ulang") }
                }
            }

            RadarPulseCanvas(isScanning = isDiscovering, peers = peers, onPeerClick = onPeerClick)

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Berbagi cepat ke Safari", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = when (serverState) {
                                    is ServerState.Running -> "iPhone bisa mengunduh antrean di ${serverState.url}"
                                    ServerState.Starting -> "Menyalakan server…"
                                    is ServerState.Error -> serverState.message
                                    ServerState.Stopped -> "Nyalakan agar iOS dapat mengambil file lewat browser"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.size(12.dp))
                        Switch(
                            checked = serverState is ServerState.Running || serverState == ServerState.Starting,
                            onCheckedChange = { onToggleServer() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TextComposerCard(
    text: String,
    onTextChange: (String) -> Unit,
    onPaste: () -> Unit,
    onAdd: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.TextFields, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(10.dp))
                Text("Kirim teks / tautan", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                AssistChip(
                    onClick = onPaste,
                    label = { Text("Tempel") },
                    leadingIcon = { Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text("Ketik pesan, catatan, atau URL…") },
                minLines = 2,
                maxLines = 6,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth()
            )
            AnimatedVisibility(visible = text.isNotBlank()) {
                Button(
                    onClick = onAdd,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.PlaylistAdd, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Tambah ke antrean")
                }
            }
        }
    }
}

@Composable
private fun QueueItemRow(item: SharedItem, onRemove: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = fileIconFor(item.mimeType, item.displayName),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp)
            ) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = if (item is SharedText) 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when (item) {
                        is SharedFile -> NetworkUtils.formatFileSize(item.sizeBytes) + " • " + item.mimeType
                        is SharedText -> "Teks • ${item.text.length} karakter"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Close, contentDescription = "Hapus dari antrean")
            }
        }
    }
}

@Composable
private fun EmptyQueue() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Inbox,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text("Antrean masih kosong", style = MaterialTheme.typography.titleSmall)
        Text(
            "Pilih file, tempel clipboard, atau bagikan dari aplikasi lain ke Kirimly.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

@Composable
private fun PinEntryDialog(peer: PeerDevice, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PIN ${peer.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Masukkan PIN 4 digit yang tampil di layar Terima perangkat tujuan.")
                OutlinedTextField(
                    value = pin,
                    onValueChange = { value -> pin = value.filter { it.isDigit() }.take(4) },
                    singleLine = true,
                    placeholder = { Text("• • • •") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(pin) }, enabled = pin.length == 4) { Text("Kirim") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
