package com.kirimly.app.ui.screens

import com.kirimly.app.R
import com.kirimly.app.util.tr
import com.kirimly.app.ui.components.FileThumbnail
import com.kirimly.app.ui.components.ProBadge
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kirimly.app.data.model.HistoryFilter
import com.kirimly.app.data.model.TransferDirection
import com.kirimly.app.data.model.TransferItem
import com.kirimly.app.network.NetworkUtils
import com.kirimly.app.ui.MainViewModel
import com.kirimly.app.ui.components.fileIconFor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
    onOpenPro: () -> Unit
) {
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val hiddenCount by viewModel.historyHiddenCount.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val history by viewModel.history.collectAsStateWithLifecycle()
    val query by viewModel.historyQuery.collectAsStateWithLifecycle()
    val filter by viewModel.historyFilter.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(tr(R.string.s_riwayat), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            tr(R.string.s_semua_file_teks_yang_pernah),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = tr(R.string.u_settings))
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = tr(R.string.s_menu_lainnya))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(tr(R.string.s_hapus_semua_riwayat)) },
                                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    confirmClear = true
                                }
                            )
                        }
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
            item(key = "search") {
                if (!isPro) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RectangleShape)
                            .clickable(onClick = onOpenPro)
                            .padding(horizontal = 14.dp, vertical = 16.dp)
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            tr(R.string.s_cari_nama_file_teks_atau),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        )
                        ProBadge(locked = true)
                    }
                    return@item
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setHistoryQuery,
                    placeholder = { Text(tr(R.string.s_cari_nama_file_teks_atau)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setHistoryQuery("") }) {
                                Icon(Icons.Rounded.Clear, contentDescription = tr(R.string.s_hapus_pencarian))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RectangleShape,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item(key = "filters") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HistoryFilter.entries.forEach { option ->
                        val chipIcon: ImageVector? = when (option) {
                            HistoryFilter.ALL -> null
                            HistoryFilter.RECEIVED -> Icons.Rounded.ArrowDownward
                            HistoryFilter.SENT -> Icons.Rounded.ArrowUpward
                        }
                        FilterChip(
                            selected = filter == option,
                            onClick = { viewModel.setHistoryFilter(option) },
                            label = { Text(option.displayName) },
                            leadingIcon = chipIcon?.let { vector ->
                                @Composable { Icon(vector, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            }
                        )
                    }
                }
            }

            if (history.isEmpty()) {
                item(key = "empty") {
                    EmptyHistory(isFiltered = query.isNotBlank() || filter != HistoryFilter.ALL)
                }
            } else {
                items(history, key = { it.id }) { item ->
                    HistoryRow(
                        item = item,
                        onOpen = { openItem(context, item, viewModel) },
                        onCopy = { item.textContent?.let(viewModel::copyToClipboard) },
                        onShare = { shareItem(context, item, viewModel) },
                        onDelete = { viewModel.deleteHistoryItem(item.id) },
                        onSendAgain = { viewModel.resendFromHistory(item) }
                    )
                }
                if (hiddenCount > 0) {
                    item(key = "hidden") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RectangleShape)
                                .clickable(onClick = onOpenPro)
                                .padding(16.dp)
                        ) {
                            Text(
                                tr(R.string.p_history_hidden, hiddenCount),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp)
                            )
                            ProBadge(locked = true)
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(tr(R.string.s_hapus_semua_riwayat_2)) },
            text = { Text(tr(R.string.s_catatan_transfer_akan_dihapus_file)) },
            confirmButton = {
                Button(onClick = {
                    confirmClear = false
                    viewModel.clearHistory()
                }) { Text(tr(R.string.s_hapus)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(tr(R.string.s_batal)) } }
        )
    }
}

@Suppress("DEPRECATION")
@Composable
private fun HistoryRow(
    item: TransferItem,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onSendAgain: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val received = item.direction == TransferDirection.RECEIVED
    Card(
        shape = RectangleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    FileThumbnail(
                        uri = item.uri?.takeIf { !item.isText }?.let(Uri::parse),
                        mimeType = item.mimeType,
                        fileName = item.fileName,
                        modifier = Modifier.size(46.dp)
                    )
                    Surface(
                        shape = CircleShape,
                        color = if (received) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .size(18.dp)
                            .align(Alignment.BottomEnd)
                    ) {
                        Icon(
                            imageVector = if (received) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                            contentDescription = if (received) tr(R.string.s_diterima) else tr(R.string.s_dikirim),
                            tint = if (received) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(2.dp)
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp)
                ) {
                    Text(
                        text = if (item.isText) item.textContent.orEmpty() else item.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = if (item.isText) 2 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOf(
                            if (item.isText) tr(R.string.s_teks) else NetworkUtils.formatFileSize(item.sizeBytes),
                            (if (received) "dari " else "ke ") + item.peerName,
                            formatTimestamp(item.timestamp)
                        ).joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = tr(R.string.s_opsi))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (!received && item.peerName.isNotBlank()) {
                            DropdownMenuItem(
                                text = { Text(tr(R.string.t_send_again, item.peerName)) },
                                leadingIcon = { Icon(Icons.Rounded.Replay, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onSendAgain()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(tr(R.string.s_bagikan)) },
                            leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onShare()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(tr(R.string.s_hapus_dari_riwayat)) },
                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.padding(end = 12.dp)) {
                Spacer(Modifier.weight(1f))
                if (item.isText) {
                    FilledTonalButton(onClick = onCopy) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(tr(R.string.s_salin_teks))
                    }
                } else {
                    FilledTonalButton(onClick = onOpen, enabled = item.uri != null) {
                        Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(tr(R.string.s_buka_file))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHistory(isFiltered: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (isFiltered) tr(R.string.s_tidak_ada_hasil) else tr(R.string.s_belum_ada_riwayat),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            if (isFiltered) tr(R.string.s_coba_kata_kunci_atau_filter) else tr(R.string.s_file_dan_teks_yang_kamu),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dateFormat = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())

private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    return if (DateUtils.isToday(timestamp)) tr(R.string.s_hari_ini) + timeFormat.format(date) else dateFormat.format(date)
}

private fun openItem(context: Context, item: TransferItem, viewModel: MainViewModel) {
    val uri = item.uri?.let(Uri::parse) ?: return
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, item.mimeType.ifBlank { "*/*" })
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(Intent.createChooser(intent, tr(R.string.s_buka_dengan)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        viewModel.showMessage(tr(R.string.s_tidak_ada_aplikasi_untuk_membuka))
    } catch (e: SecurityException) {
        viewModel.showMessage(tr(R.string.s_akses_ke_file_sudah_tidak))
    }
}

private fun shareItem(context: Context, item: TransferItem, viewModel: MainViewModel) {
    val intent = Intent(Intent.ACTION_SEND)
    if (item.isText) {
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, item.textContent)
    } else {
        val uri = item.uri?.let(Uri::parse) ?: return
        intent.type = item.mimeType.ifBlank { "*/*" }
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, tr(R.string.s_bagikan)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        viewModel.showMessage(tr(R.string.s_tidak_dapat_membagikan_item_ini))
    }
}
