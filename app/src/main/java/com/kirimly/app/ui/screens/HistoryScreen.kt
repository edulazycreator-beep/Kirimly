package com.kirimly.app.ui.screens

import com.kirimly.app.ui.components.FileThumbnail
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
import androidx.compose.material.icons.rounded.Palette
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
    onOpenSettings: () -> Unit
) {
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
                        Text("Riwayat", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Semua file & teks yang pernah ditransfer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Palette, contentDescription = "Tema & palet warna")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Menu lainnya")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Hapus semua riwayat") },
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
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setHistoryQuery,
                    placeholder = { Text("Cari nama file, teks, atau perangkat") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setHistoryQuery("") }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Hapus pencarian")
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
                        onDelete = { viewModel.deleteHistoryItem(item.id) }
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Hapus semua riwayat?") },
            text = { Text("Catatan transfer akan dihapus. File yang sudah tersimpan di Downloads/Kirimly tidak ikut terhapus.") },
            confirmButton = {
                Button(onClick = {
                    confirmClear = false
                    viewModel.clearHistory()
                }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Batal") } }
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
    onDelete: () -> Unit
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
                            contentDescription = if (received) "Diterima" else "Dikirim",
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
                            if (item.isText) "Teks" else NetworkUtils.formatFileSize(item.sizeBytes),
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
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Opsi")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Bagikan") },
                            leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onShare()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Hapus dari riwayat") },
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
                        Text("Salin Teks")
                    }
                } else {
                    FilledTonalButton(onClick = onOpen, enabled = item.uri != null) {
                        Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Buka File")
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
            if (isFiltered) "Tidak ada hasil" else "Belum ada riwayat",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            if (isFiltered) "Coba kata kunci atau filter lain." else "File dan teks yang kamu kirim atau terima akan muncul di sini.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale.forLanguageTag("id-ID"))
private val dateFormat = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.forLanguageTag("id-ID"))

private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    return if (DateUtils.isToday(timestamp)) "Hari ini " + timeFormat.format(date) else dateFormat.format(date)
}

private fun openItem(context: Context, item: TransferItem, viewModel: MainViewModel) {
    val uri = item.uri?.let(Uri::parse) ?: return
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, item.mimeType.ifBlank { "*/*" })
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(Intent.createChooser(intent, "Buka dengan").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        viewModel.showMessage("Tidak ada aplikasi untuk membuka file ini")
    } catch (e: SecurityException) {
        viewModel.showMessage("Akses ke file sudah tidak tersedia")
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
        context.startActivity(Intent.createChooser(intent, "Bagikan").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        viewModel.showMessage("Tidak dapat membagikan item ini")
    }
}
