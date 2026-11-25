package com.kirimly.app.ui.screens

import com.kirimly.app.R
import com.kirimly.app.util.tr
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirimly.app.data.model.PeerDevice
import com.kirimly.app.ui.components.AnimatedKirimlyMark
import com.kirimly.app.ui.components.RadarPulseCanvas
import com.kirimly.app.ui.theme.MonoDigits
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val eyebrow: String,
    val title: String,
    val body: String,
    val illustration: @Composable () -> Unit
)

/** First-run tutorial: four pages, each with a live illustration of what the step does. */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pages = remember {
        listOf(
            OnboardingPage(
                eyebrow = tr(R.string.s_selamat_datang),
                title = tr(R.string.s_kirim_file_antar_android_dan),
                body = tr(R.string.s_kirimly_bekerja_di_jaringan_lokal),
                illustration = { AnimatedKirimlyMark(modifier = Modifier.size(220.dp)) }
            ),
            OnboardingPage(
                eyebrow = "iPhone & iPad",
                title = tr(R.string.s_iphone_cukup_pindai_qr_lalu),
                body = tr(R.string.s_nyalakan_server_di_tab_terima),
                illustration = { QrScanIllustration() }
            ),
            OnboardingPage(
                eyebrow = tr(R.string.s_keamanan),
                title = tr(R.string.s_setiap_kiriman_butuh_pin_dan),
                body = tr(R.string.s_perangkat_lain_memasukkan_pin_4),
                illustration = { PinIllustration() }
            ),
            OnboardingPage(
                eyebrow = tr(R.string.s_android_ke_android),
                title = tr(R.string.s_perangkat_kirimly_terdekat_muncul_di),
                body = tr(R.string.s_tambahkan_file_ke_antrean_di),
                illustration = { RadarIllustration() }
            )
        )
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == pages.lastIndex

    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 12.dp, top = 8.dp)
                .height(48.dp)
        ) {
            Text(
                text = "Kirimly",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            AnimatedVisibility(visible = !isLast, enter = fadeIn(), exit = fadeOut()) {
                TextButton(onClick = onFinish) { Text(tr(R.string.s_lewati)) }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { index ->
            val page = pages[index]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    page.illustration()
                }
                Text(
                    text = page.eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = page.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = page.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(32.dp))
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = tr(R.string.s_halaman_dari, pagerState.currentPage + 1, pages.size) }
            ) {
                pages.indices.forEach { index ->
                    val selected = index == pagerState.currentPage
                    val width by animateDpAsState(if (selected) 24.dp else 8.dp, label = "dotWidth")
                    val color by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHighest,
                        label = "dotColor"
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(width)
                            .background(color, CircleShape)
                    )
                }
            }
            Button(
                onClick = {
                    if (isLast) onFinish()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                modifier = Modifier.height(52.dp)
            ) {
                AnimatedContent(
                    targetState = isLast,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "cta"
                ) { last ->
                    Text(if (last) tr(R.string.s_mulai_sekarang) else tr(R.string.s_lanjut))
                }
            }
        }
    }
}

@Composable
private fun QrScanIllustration() {
    val transition = rememberInfiniteTransition(label = "scan")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sweep"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(
            modifier = Modifier
                .widthIn(max = 220.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RectangleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(20.dp)
        ) {
            val travel = maxHeight
            Icon(
                imageVector = Icons.Rounded.QrCode2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .offset(y = travel * sweep)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "http://192.168.1.12:8080",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), CircleShape)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun PinIllustration() {
    val digits = "4821"
    var shown by remember { mutableIntStateOf(0) }
    var approved by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            approved = false
            shown = 0
            delay(500)
            for (count in 1..digits.length) {
                shown = count
                delay(320)
            }
            delay(250)
            approved = true
            delay(1600)
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            digits.forEachIndexed { index, digit ->
                val filled = index < shown
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(width = 52.dp, height = 64.dp)
                        .border(
                            BorderStroke(
                                1.dp,
                                if (filled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
                            ),
                            RectangleShape
                        )
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    androidx.compose.animation.AnimatedVisibility(visible = filled, enter = fadeIn() + scaleIn(initialScale = 0.6f), exit = fadeOut()) {
                        Text(digit.toString(), style = MonoDigits.copy(fontSize = 28.sp, letterSpacing = 0.sp))
                    }
                }
            }
        }
        Box(modifier = Modifier.height(40.dp), contentAlignment = Alignment.Center) {
            androidx.compose.animation.AnimatedVisibility(visible = approved, enter = fadeIn() + scaleIn(initialScale = 0.8f), exit = fadeOut()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(tr(R.string.s_kiriman_disetujui), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun RadarIllustration() {
    val demoPeers = remember {
        listOf(
            PeerDevice(id = "demo-pixel", name = "Pixel Rina", host = "0.0.0.0", port = 0, pinRequired = true),
            PeerDevice(id = "demo-galaxy", name = "Galaxy Tab", host = "0.0.0.0", port = 0, pinRequired = false),
            PeerDevice(id = "demo-redmi", name = "Redmi Budi", host = "0.0.0.0", port = 0, pinRequired = true)
        )
    }
    var visibleCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            visibleCount = 0
            delay(700)
            for (count in 1..demoPeers.size) {
                visibleCount = count
                delay(900)
            }
            delay(2200)
        }
    }
    Box(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .fillMaxHeight()
            .semantics { contentDescription = tr(R.string.s_contoh_radar_dengan_perangkat_fiktif) },
        contentAlignment = Alignment.Center
    ) {
        RadarPulseCanvas(
            isScanning = true,
            peers = demoPeers.take(visibleCount),
            onPeerClick = {}
        )
    }
}
