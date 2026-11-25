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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
            Text("Tampilan & Pengaturan", style = MaterialTheme.typography.titleLarge)

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Mode tema")
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
                SectionLabel("Palet warna")
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
                SectionLabel("Nama perangkat")
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it.take(UserPreferencesRepository.MAX_NICKNAME_LENGTH) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Smartphone, contentDescription = null) },
                    supportingText = { Text("Terlihat oleh perangkat lain di radar dan di Safari") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (nickname.isNotBlank()) onNicknameChange(nickname)
                        focusManager.clearFocus()
                    }),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (nickname != preferences.deviceNickname && nickname.isNotBlank()) {
                    TextButton(onClick = {
                        onNicknameChange(nickname)
                        focusManager.clearFocus()
                    }) { Text("Simpan nama") }
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Wajibkan PIN", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Perangkat lain harus memasukkan PIN 4 digit sebelum mengirim atau mengunduh.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Switch(checked = preferences.pinRequired, onCheckedChange = onPinRequiredChange)
                }
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
            .clip(RoundedCornerShape(16.dp))
            .border(BorderStroke(if (selected) 2.dp else 1.dp, borderColor), RoundedCornerShape(16.dp))
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
                    contentDescription = "Dipilih",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            Box(modifier = Modifier.size(26.dp).background(Color.Transparent))
        }
    }
}
