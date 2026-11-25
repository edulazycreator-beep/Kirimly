@file:Suppress("DEPRECATION")

package com.kirimly.app.ui.screens

import com.kirimly.app.ui.components.TransferSection
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Devices
import com.kirimly.app.ui.components.ProBadge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kirimly.app.R
import com.kirimly.app.data.model.ConnectedBrowser
import com.kirimly.app.data.model.PeerDevice
import com.kirimly.app.data.model.SharedFile
import com.kirimly.app.data.model.SharedItem
import com.kirimly.app.data.model.SharedText
import com.kirimly.app.network.NetworkUtils
import com.kirimly.app.ui.MainViewModel
import com.kirimly.app.ui.components.FileThumbnail
import com.kirimly.app.util.tr

/** Send = two plain steps: 1) what to send, 2) who gets it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
    onOpenReceive: () -> Unit,
    onOpenPro: () -> Unit
) {
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val browsers by viewModel.browsers.collectAsStateWithLifecycle()
    val queue by viewModel.sharedItems.collectAsStateWithLifecycle()
    val totalBytes by viewModel.queueTotalBytes.collectAsStateWithLifecycle()
    val sendingPeerIds by viewModel.sendingPeerIds.collectAsStateWithLifecycle()
    var multiMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val unreachable by viewModel.unreachablePeers.collectAsStateWithLifecycle()
    val sending by viewModel.sendingTransfers.collectAsStateWithLifecycle()

    var pinTarget by remember { mutableStateOf<PeerDevice?>(null) }
    var showTextDialog by remember { mutableStateOf(false) }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.addUris(uris)
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::addFolder)
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
                title = { Text(tr(R.string.u_send_title), style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Palette, contentDescription = tr(R.string.u_settings))
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
            if (sending.isNotEmpty()) {
                item(key = "sending") {
                    TransferSection(
                        title = tr(R.string.u_sending_now),
                        transfers = sending,
                        onDismiss = viewModel::dismissTransfer,
                        onClearFinished = viewModel::clearFinishedTransfers
                    )
                }
            }

            // ---------------- Step 1: what
            item(key = "step1") {
                StepCard(number = 1, title = tr(R.string.u_step_what)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { pickFiles.launch(arrayOf("*/*")) },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        ) {
                            Icon(Icons.Rounded.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(tr(R.string.u_pick_files))
                        }
                        OutlinedButton(
                            onClick = { showTextDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        ) {
                            Icon(Icons.Rounded.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(tr(R.string.u_add_text))
                        }
                    }
                    TextButton(
                        onClick = { if (isPro) pickFolder.launch(null) else onOpenPro() },
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(tr(R.string.p_pick_folder))
                        if (!isPro) {
                            Spacer(Modifier.size(8.dp))
                            ProBadge(locked = true)
                        }
                    }
                    if (queue.isEmpty()) {
                        Text(
                            tr(R.string.u_queue_empty_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column {
                            queue.take(MAX_QUEUE_ROWS).forEach { item ->
                                QueueRow(item = item, onRemove = { viewModel.removeItem(item.id) })
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            if (queue.size > MAX_QUEUE_ROWS) {
                                Text(
                                    tr(R.string.p_more_items, queue.size - MAX_QUEUE_ROWS),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                tr(R.string.u_queue_summary, queue.size, NetworkUtils.formatFileSize(totalBytes)),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = viewModel::clearQueue) { Text(tr(R.string.u_clear)) }
                        }
                    }
                }
            }

            // ---------------- Step 2: who
            item(key = "step2") {
                StepCard(number = 2, title = tr(R.string.u_step_who)) {
                    if (unreachable.isNotEmpty()) {
                        Notice(tr(R.string.u_isolation, unreachable.joinToString(", ")))
                    }
                    val destinationCount = peers.size + browsers.size
                    val multi = multiMode && isPro && destinationCount >= 2
                    val toggle = { key: String -> selected = if (key in selected) selected - key else selected + key }
                    if (destinationCount >= 2) {
                        TextButton(
                            onClick = {
                                if (!isPro) onOpenPro() else {
                                    multiMode = !multiMode
                                    selected = emptySet()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Rounded.Devices, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(tr(if (multi) R.string.p_multi_off else R.string.p_multi_on))
                            if (!isPro) {
                                Spacer(Modifier.size(8.dp))
                                ProBadge(locked = true)
                            }
                        }
                    }
                    peers.forEach { peer ->
                        DestinationRow(
                            icon = Icons.Rounded.PhoneAndroid,
                            title = peer.name,
                            subtitle = tr(R.string.u_dest_android),
                            busy = peer.id in sendingPeerIds,
                            enabled = queue.isNotEmpty() && sendingPeerIds.isEmpty(),
                            checked = if (multi) "p:" + peer.id in selected else null,
                            onCheck = { toggle("p:" + peer.id) },
                            onSend = { if (peer.pinRequired) pinTarget = peer else viewModel.sendToPeer(peer, null) }
                        )
                    }
                    browsers.forEach { browser ->
                        DestinationRow(
                            icon = Icons.Rounded.Language,
                            title = browser.name,
                            subtitle = tr(R.string.u_dest_safari),
                            busy = false,
                            enabled = queue.isNotEmpty(),
                            checked = if (multi) "b:" + browser.id in selected else null,
                            onCheck = { toggle("b:" + browser.id) },
                            onSend = { viewModel.offerToBrowser(browser) }
                        )
                    }
                    if (multi) {
                        val chosenPeers = peers.filter { "p:" + it.id in selected }
                        val chosenBrowsers = browsers.filter { "b:" + it.id in selected }
                        val count = chosenPeers.size + chosenBrowsers.size
                        Button(
                            onClick = {
                                viewModel.sendToMany(chosenPeers, chosenBrowsers)
                                multiMode = false
                                selected = emptySet()
                            },
                            enabled = count > 0 && queue.isNotEmpty() && sendingPeerIds.isEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) { Text(tr(R.string.p_multi_send, count)) }
                        Text(
                            tr(R.string.p_multi_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (peers.isEmpty() && browsers.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(12.dp))
                            Text(tr(R.string.u_searching), style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            tr(R.string.u_searching_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PhoneIphone, contentDescription = null, modifier = Modifier.size(22.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        ) {
                            Text(tr(R.string.u_dest_iphone_title), style = MaterialTheme.typography.titleSmall)
                            Text(
                                tr(R.string.u_dest_iphone_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = onOpenReceive) { Text(tr(R.string.u_show_qr)) }
                    }
                }
            }
        }
    }

    if (showTextDialog) {
        TextDialog(
            initial = "",
            onPaste = { viewModel.readClipboardText() },
            onDismiss = { showTextDialog = false },
            onAdd = { text ->
                if (viewModel.addText(text)) showTextDialog = false
            }
        )
    }

    pinTarget?.let { peer ->
        PinDialog(
            peer = peer,
            onDismiss = { pinTarget = null },
            onConfirm = { pin ->
                pinTarget = null
                viewModel.sendToPeer(peer, pin)
            }
        )
    }
}

private const val MAX_QUEUE_ROWS = 40

@Composable
internal fun StepCard(number: Int?, title: String, content: @Composable () -> Unit) {
    Card(
        shape = RectangleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (number != null) {
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Text(
                            number.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
internal fun Notice(text: String) {
    Surface(shape = RectangleShape, color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun QueueRow(item: SharedItem, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        FileThumbnail(
            uri = (item as? SharedFile)?.uri,
            mimeType = item.mimeType,
            fileName = item.displayName,
            modifier = Modifier.size(40.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                (item as? SharedFile)?.relativePath ?: item.displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                when (item) {
                    is SharedFile -> NetworkUtils.formatFileSize(item.sizeBytes)
                    is SharedText -> tr(R.string.u_text_item)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, contentDescription = tr(R.string.u_remove)) }
    }
}

@Composable
private fun DestinationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    busy: Boolean,
    enabled: Boolean,
    checked: Boolean? = null,
    onCheck: () -> Unit = {},
    onSend: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            busy -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            checked != null -> Checkbox(checked = checked, onCheckedChange = { onCheck() })
            else -> FilledTonalButton(onClick = onSend, enabled = enabled) { Text(tr(R.string.u_send)) }
        }
    }
}

@Composable
private fun TextDialog(initial: String, onPaste: () -> String?, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(R.string.u_add_text)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(tr(R.string.u_text_label)) },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { onPaste()?.let { text = it } }) { Text(tr(R.string.u_paste_clipboard)) }
            }
        },
        confirmButton = { Button(onClick = { onAdd(text) }, enabled = text.isNotBlank()) { Text(tr(R.string.u_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(R.string.u_cancel)) } }
    )
}

@Composable
private fun PinDialog(peer: PeerDevice, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(R.string.u_pin_title, peer.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr(R.string.u_pin_body))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { value -> pin = value.filter { it.isDigit() }.take(4) },
                    singleLine = true,
                    label = { Text(tr(R.string.u_pin_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { Button(onClick = { onConfirm(pin) }, enabled = pin.length == 4) { Text(tr(R.string.u_send)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(R.string.u_cancel)) } }
    )
}
