package com.kirimly.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kirimly.app.R
import com.kirimly.app.data.model.AppPalette
import com.kirimly.app.data.model.ThemeMode
import com.kirimly.app.data.model.UserPreferences
import com.kirimly.app.data.prefs.UserPreferencesRepository
import com.kirimly.app.ui.theme.kirimlyColorScheme
import com.kirimly.app.util.tr

/** Settings kept to what people actually change: look, name, remembered devices. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PalettePickerSheet(
    preferences: UserPreferences,
    isDarkActive: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onPaletteChange: (AppPalette) -> Unit,
    onNicknameChange: (String) -> Unit,
    onRemoveTrusted: (String) -> Unit,
    onOpenPro: () -> Unit,
    onPortalNameChange: (String) -> Unit,
    onClipboardSyncChange: (Boolean) -> Unit,
    crashLogCount: Int,
    onShareCrashLogs: () -> Unit,
    onShowTutorial: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    var nickname by remember(preferences.deviceNickname) { mutableStateOf(preferences.deviceNickname) }
    val isPro = preferences.isPro
    var portalName by remember(preferences.portalName) { mutableStateOf(preferences.portalName) }
    val saveName = {
        if (nickname.isNotBlank() && nickname != preferences.deviceNickname) onNicknameChange(nickname)
        if (isPro && portalName.trim() != preferences.portalName) onPortalNameChange(portalName)
        focusManager.clearFocus()
    }

    ModalBottomSheet(
        onDismissRequest = {
            saveName()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Text(tr(R.string.u_settings), style = MaterialTheme.typography.titleLarge)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RectangleShape)
                    .clickable(onClick = onOpenPro)
                    .padding(horizontal = 14.dp, vertical = 14.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Kirimly", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.size(8.dp))
                        ProBadge()
                    }
                    Text(
                        tr(if (isPro) R.string.p_settings_active else R.string.p_settings_cta),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Section(tr(R.string.u_device_name)) {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it.take(UserPreferencesRepository.MAX_NICKNAME_LENGTH) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Smartphone, contentDescription = null) },
                    supportingText = { Text(tr(R.string.u_device_name_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { saveName() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Section(tr(R.string.u_appearance)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = preferences.themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                            icon = {}
                        ) { Text(mode.displayName) }
                    }
                }
                AppPalette.entries.forEach { palette ->
                    val locked = !isPro && palette != AppPalette.MIDNIGHT_MONO
                    PaletteRow(
                        palette = palette,
                        selected = (if (isPro) preferences.palette else AppPalette.MIDNIGHT_MONO) == palette,
                        dark = isDarkActive,
                        locked = locked,
                        onClick = { if (locked) onOpenPro() else onPaletteChange(palette) }
                    )
                }
            }

            Section(tr(R.string.p_portal_name)) {
                if (isPro) {
                    OutlinedTextField(
                        value = portalName,
                        onValueChange = { portalName = it.take(UserPreferencesRepository.MAX_NICKNAME_LENGTH) },
                        singleLine = true,
                        placeholder = { Text(preferences.deviceNickname) },
                        leadingIcon = { Icon(Icons.Rounded.Language, contentDescription = null) },
                        supportingText = { Text(tr(R.string.p_portal_name_hint)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { saveName() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LockedRow(text = tr(R.string.p_portal_name_hint), onClick = onOpenPro)
                }
            }

            Section(tr(R.string.p_clip_title)) {
                if (isPro) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onClipboardSyncChange(!preferences.clipboardSync) }
                    ) {
                        Text(
                            tr(R.string.p_clip_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        )
                        Switch(checked = preferences.clipboardSync, onCheckedChange = onClipboardSyncChange)
                    }
                } else {
                    LockedRow(text = tr(R.string.p_clip_body), onClick = onOpenPro)
                }
            }

            Section(tr(R.string.u_remembered)) {
                Text(
                    tr(if (isPro) R.string.p_remembered_pro else R.string.p_remembered_free),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (preferences.trustedDevices.isEmpty()) {
                    Text(
                        tr(R.string.u_remembered_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    preferences.trustedDevices.forEach { device ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(device.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onRemoveTrusted(device.id) }) { Text(tr(R.string.u_forget)) }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onShowTutorial, modifier = Modifier.fillMaxWidth()) { Text(tr(R.string.u_tutorial)) }
                if (crashLogCount > 0) {
                    TextButton(onClick = onShareCrashLogs, modifier = Modifier.fillMaxWidth()) {
                        Text(tr(R.string.u_share_crash, crashLogCount))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun LockedRow(text: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        )
        ProBadge(locked = true)
    }
}

@Composable
private fun PaletteRow(palette: AppPalette, selected: Boolean, dark: Boolean, locked: Boolean, onClick: () -> Unit) {
    val scheme = remember(palette, dark) { kirimlyColorScheme(palette, dark) }
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RectangleShape)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, borderColor), RectangleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(modifier = Modifier.size(width = 68.dp, height = 28.dp)) {
            listOf(scheme.primary, scheme.secondaryContainer, scheme.tertiary).forEachIndexed { index, color ->
                Box(
                    modifier = Modifier
                        .offset(x = (index * 20).dp)
                        .size(28.dp)
                        .border(2.dp, MaterialTheme.colorScheme.surfaceContainerLow, CircleShape)
                        .background(color, CircleShape)
                )
            }
        }
        Text(palette.displayName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        } else if (locked) {
            ProBadge(locked = true)
        }
    }
}
