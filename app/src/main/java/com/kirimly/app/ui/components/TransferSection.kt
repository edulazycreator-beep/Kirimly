package com.kirimly.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kirimly.app.R
import com.kirimly.app.data.model.TransferProgress
import com.kirimly.app.util.tr

/** Live transfers shown at the top of their own tab, so they are the first thing people see. */
@Composable
fun TransferSection(
    title: String,
    transfers: List<TransferProgress>,
    onDismiss: (String) -> Unit,
    onClearFinished: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (transfers.any { it.isFinished }) {
                TextButton(onClick = onClearFinished) { Text(tr(R.string.u_clear)) }
            }
        }
        transfers.forEach { progress ->
            TransferProgressCard(progress = progress, onDismiss = { onDismiss(progress.id) })
        }
    }
}
