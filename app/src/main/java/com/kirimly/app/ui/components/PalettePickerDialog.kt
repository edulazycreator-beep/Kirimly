package com.kirimly.app.ui.components

import com.kirimly.app.R
import com.kirimly.app.util.tr
import androidx.compose.material3.Button
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.ui.graphics.RectangleShape
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
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kirimly.app.data.model.AppPalette
import com.kirimly.app.data.model.ThemeMode
import com.kirimly.app.data.model.UserPreferences
import com.kirimly.app.data.prefs.UserPreferencesRepository
import com.kirimly.app.ui.theme.kirimlyColorScheme

/** Theme & colour palette customiser sheet (also hosts device nickname and PIN settings). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PalettePickerSheet(
    preferences: UserPreferences,
    isDarkActive: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onPaletteChange: (AppPalette) -> Unit,
    onNicknameChange: (String) -> Unit,
    onPinRequiredChange: (Boolean) -> Unit,
    onAutoStartChange: (Boolean) -> Unit,
    onPinInQrChange: (Boolean) -> Unit,
    onConvertHeicChange: (Boolean) -> Unit,
    onRemoveTrusted: (String) -> Unit,
    onIdleStopChange: (Int) -> Unit,
    crashLogCount: Int,
    onShareCrashLogs: () -> Unit,
    onClearCrashLogs: () -> Unit,
    onShowTutorial: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    var nickname by remember(preferences.deviceNickname) { mutableStateOf(preferences.deviceNickname) }

    ModalBottomSheet(
        onDismissRequest = {
            if (nickname.isNotBlank() && nickname != preferences.deviceNickname) onNicknameChange(nickname)
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(tr(R.string.s_tampilan_pengaturan), style = MaterialTheme.typography.titleLarge)

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(tr(R.string.s_mode_tema))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = preferences.themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                            icon = {
                                Icon(
                                    imageVector = when (mode) {
                                        ThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
                                        ThemeMode.LIGHT -> Icons.Rounded.LightMode
                                        ThemeMode.DARK -> Icons.Rounded.DarkMode
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        ) { Text(mode.displayName) }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(tr(R.string.s_palet_warna))
                AppPalette.entries.forEach { palette ->
                    PaletteRow(
                        palette = palette,
                        selected = preferences.palette == palette,
                        dark = isDarkActive,
                        onClick = { onPaletteChange(palette) }
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(tr(R.string.s_nama_perangkat))
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it.take(UserPreferencesRepository.MAX_NICKNAME_LENGTH) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Smartphone, contentDescription = null) },
                    supportingText = { Text(tr(R.string.s_terlihat_oleh_perangkat_lain_di)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (nickname.isNotBlank()) onNicknameChange(nickname)
                        focusManager.clearFocus()
                    }),
                    shape = RectangleShape,
                    modifier = Modifier.fillMaxWidth()
                )
                if (nickname != preferences.deviceNickname && nickname.isNotBlank()) {
                    TextButton(onClick = {
                        onNicknameChange(nickname)
                        focusManager.clearFocus()
                    }) { Text(tr(R.string.s_simpan_nama)) }
                }
            }

            Surface(
                shape = RectangleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tr(R.string.s_wajibkan_pin), style = MaterialTheme.typography.titleSmall)
                        Text(
                            tr(R.string.s_perangkat_lain_harus_memasukkan_pin),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Switch(checked = preferences.pinRequired, onCheckedChange = onPinRequiredChange)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Transfer")
                SettingSwitch(
                    title = tr(R.string.s_server_otomatis_aktif),
                    body = tr(R.string.s_nyalakan_server_setiap_kali_kirimly),
                    checked = preferences.autoStartServer,
                    onCheckedChange = onAutoStartChange
                )
                SettingSwitch(
                    title = tr(R.string.s_sertakan_pin_di_qr),
                    body = tr(R.string.s_pemindai_qr_langsung_masuk_tanpa),
                    checked = preferences.pinInQr,
                    onCheckedChange = onPinInQrChange
                )
                SettingSwitch(
                    title = tr(R.string.s_ubah_foto_heic_ke_jpg),
                    body = tr(R.string.s_foto_iphone_disimpan_sebagai_jpg),
                    checked = preferences.convertHeic,
                    onCheckedChange = onConvertHeicChange
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(tr(R.string.s_matikan_server_saat_menganggur))
                Text(
                    tr(R.string.s_saat_kirimly_di_latar_belakang),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val options = listOf(0 to tr(R.string.s_tidak), 5 to "5 m", 15 to "15 m", 30 to "30 m", 60 to "60 m")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, (minutes, label) ->
                        SegmentedButton(
                            selected = preferences.idleStopMinutes == minutes,
                            onClick = { onIdleStopChange(minutes) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                            icon = {}
                        ) { Text(label) }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(tr(R.string.s_perangkat_tepercaya))
                if (preferences.trustedDevices.isEmpty()) {
                    Text(
                        tr(R.string.s_belum_ada_centang_percayai_perangkat),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    preferences.trustedDevices.forEach { device ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RectangleShape)
                                .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            Icon(Icons.Rounded.VerifiedUser, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(
                                device.name,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)
                            )
                            TextButton(onClick = { onRemoveTrusted(device.id) }) { Text(tr(R.string.s_hapus)) }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(tr(R.string.s_log_crash))
                Text(
                    if (crashLogCount == 0) tr(R.string.s_tidak_ada_crash_tercatat_log)
                    else tr(R.string.s_crash_tercatat_bagikan_ke_pengembang, crashLogCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (crashLogCount > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onClearCrashLogs, modifier = Modifier.weight(1f)) { Text(tr(R.string.s_hapus)) }
                        Button(onClick = onShareCrashLogs, modifier = Modifier.weight(1f)) { Text(tr(R.string.s_bagikan_log)) }
                    }
                }
            }

            OutlinedButton(onClick = onShowTutorial, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.School, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(tr(R.string.s_lihat_tutorial_lagi))
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PaletteRow(palette: AppPalette, selected: Boolean, dark: Boolean, onClick: () -> Unit) {
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
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(26.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = tr(R.string.s_dipilih),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            Box(modifier = Modifier.size(26.dp).background(Color.Transparent))
        }
    }
}

@Composable
private fun SettingSwitch(title: String, body: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.size(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}
