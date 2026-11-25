package com.kirimly.app.ui

import com.kirimly.app.util.tr
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import android.content.Intent
import android.Manifest
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import com.kirimly.app.data.model.ServerState
import com.kirimly.app.ui.components.AnimatedKirimlyMark
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoveToInbox
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kirimly.app.R
import com.kirimly.app.data.model.ThemeMode
import com.kirimly.app.ui.components.ApprovalBottomSheet
import com.kirimly.app.ui.components.PalettePickerSheet
import com.kirimly.app.ui.screens.HistoryScreen
import com.kirimly.app.ui.screens.ReceiveScreen
import com.kirimly.app.ui.screens.SendScreen

private data class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Suppress("DEPRECATION")
private val destinations = listOf(
    TopLevelDestination(MainViewModel.ROUTE_SEND, R.string.tab_send, Icons.Rounded.Send, Icons.Outlined.Send),
    TopLevelDestination(MainViewModel.ROUTE_RECEIVE, R.string.tab_receive, Icons.Rounded.MoveToInbox, Icons.Outlined.MoveToInbox),
    TopLevelDestination(MainViewModel.ROUTE_HISTORY, R.string.tab_history, Icons.Rounded.History, Icons.Outlined.History)
)

@Composable
fun KirimlyApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val pendingRequests by viewModel.pendingRequests.collectAsStateWithLifecycle()
    val activeTransfers by viewModel.activeTransfers.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val crashLogCount by viewModel.crashLogCount.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var permissionsAsked by rememberSaveable { mutableStateOf(false) }

    // ---- Runtime permissions (notifications on 13+, legacy storage on 8–9) ----
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.POST_NOTIFICATIONS] == false) {
            viewModel.showMessage(tr(R.string.s_notifikasi_dimatikan_permintaan_transfer_hanya))
        }
        if (results[Manifest.permission.WRITE_EXTERNAL_STORAGE] == false) {
            viewModel.showMessage(tr(R.string.s_izin_penyimpanan_diperlukan_untuk_menyimpan))
        }
    }
    LaunchedEffect(Unit) {
        if (permissionsAsked) return@LaunchedEffect
        permissionsAsked = true
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }

    // ---- Server on by default while the app is open (can be turned off in settings) ----
    LaunchedEffect(Unit) { viewModel.ensureServerRunning() }

    // ---- One-shot UI events ----
    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.navigation.collect { route ->
            if (destinations.any { it.route == route } && navController.currentDestination?.route != route) {
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    val runningTransfers = activeTransfers.count { !it.isFinished }
    val serverState by viewModel.serverState.collectAsStateWithLifecycle()
    var showExitDialog by rememberSaveable { mutableStateOf(false) }

    // Back on the start tab asks before leaving; other tabs pop back to Kirim first.
    BackHandler(enabled = currentRoute == MainViewModel.ROUTE_SEND && !showSettings) {
        showExitDialog = true
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                destinations.forEach { destination ->
                    val selected = currentRoute == destination.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            val showBadge = destination.route == MainViewModel.ROUTE_RECEIVE &&
                                (runningTransfers > 0 || pendingRequests.isNotEmpty())
                            BadgedBox(badge = {
                                if (showBadge) {
                                    Badge { Text("${runningTransfers + pendingRequests.size}") }
                                }
                            }) {
                                AnimatedNavIcon(
                                    selected = selected,
                                    selectedIcon = destination.selectedIcon,
                                    unselectedIcon = destination.unselectedIcon
                                )
                            }
                        },
                        label = { Text(stringResource(destination.labelRes)) }
                    )
                }
            }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            NavHost(
                navController = navController,
                startDestination = MainViewModel.ROUTE_SEND,
                enterTransition = { fadeIn() },
                exitTransition = { fadeOut() }
            ) {
                composable(MainViewModel.ROUTE_SEND) {
                    SendScreen(
                        viewModel = viewModel,
                        onOpenSettings = { showSettings = true },
                        onOpenReceive = {
                            navController.navigate(MainViewModel.ROUTE_RECEIVE) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
                composable(MainViewModel.ROUTE_RECEIVE) {
                    ReceiveScreen(viewModel = viewModel, onOpenSettings = { showSettings = true })
                }
                composable(MainViewModel.ROUTE_HISTORY) {
                    HistoryScreen(viewModel = viewModel, onOpenSettings = { showSettings = true })
                }
            }
        }
    }

    // ---- Incoming approval (first pending request) ----
    pendingRequests.firstOrNull()?.let { request ->
        ApprovalBottomSheet(
            request = request,
            onAccept = { trust -> viewModel.respondToRequest(request.id, true, trust) },
            onDecline = { viewModel.respondToRequest(request.id, false) }
        )
    }

    // ---- Exit confirmation ----
    if (showExitDialog) {
        ExitConfirmDialog(
            serverRunning = serverState is ServerState.Running || serverState == ServerState.Starting,
            activeTransfers = runningTransfers,
            onDismiss = { showExitDialog = false },
            onExit = { stopServer ->
                showExitDialog = false
                if (stopServer) viewModel.stopServer()
                (context as? Activity)?.finish()
            }
        )
    }

    // ---- Theme & palette customiser ----
    if (showSettings) {
        LaunchedEffect(Unit) { viewModel.refreshCrashLogs() }
        val systemDark = isSystemInDarkTheme()
        val darkActive = when (preferences.themeMode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        PalettePickerSheet(
            preferences = preferences,
            isDarkActive = darkActive,
            onThemeModeChange = viewModel::setThemeMode,
            onPaletteChange = viewModel::setPalette,
            onNicknameChange = viewModel::setNickname,
            onRemoveTrusted = viewModel::removeTrustedDevice,
            crashLogCount = crashLogCount,
            onShareCrashLogs = {
                scope.launch {
                    val uri = viewModel.crashReportUri() ?: return@launch
                    val share = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .putExtra(Intent.EXTRA_SUBJECT, "Laporan crash Kirimly")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    try {
                        context.startActivity(Intent.createChooser(share, tr(R.string.s_bagikan_log_crash)))
                    } catch (_: Exception) {
                        viewModel.showMessage(tr(R.string.s_tidak_ada_aplikasi_untuk_membagikan))
                    }
                }
            },
            onShowTutorial = {
                showSettings = false
                viewModel.replayOnboarding()
            },
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
private fun AnimatedNavIcon(selected: Boolean, selectedIcon: ImageVector, unselectedIcon: ImageVector) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "navIconScale"
    )
    val lift by animateFloatAsState(
        targetValue = if (selected) -2f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "navIconLift"
    )
    Crossfade(
        targetState = selected,
        label = "navIconSwap",
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            translationY = lift * density
        }
    ) { isSelected ->
        Icon(imageVector = if (isSelected) selectedIcon else unselectedIcon, contentDescription = null)
    }
}

@Composable
private fun ExitConfirmDialog(
    serverRunning: Boolean,
    activeTransfers: Int,
    onDismiss: () -> Unit,
    onExit: (stopServer: Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        icon = { AnimatedKirimlyMark(modifier = Modifier.size(56.dp), floating = false) },
        title = { Text(tr(R.string.s_keluar_dari_kirimly)) },
        text = {
            Text(
                text = when {
                    activeTransfers > 0 ->
                        tr(R.string.s_masih_ada_transfer_berjalan_server, activeTransfers)
                    serverRunning ->
                        tr(R.string.s_server_masih_aktif_kamu_bisa)
                    else -> tr(R.string.s_kamu_bisa_membuka_kirimly_lagi)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            if (serverRunning) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onExit(true) }) { Text(tr(R.string.s_matikan_server_keluar)) }
                    OutlinedButton(onClick = { onExit(false) }) { Text(tr(R.string.s_keluar_biarkan_aktif)) }
                    TextButton(onClick = onDismiss) { Text(tr(R.string.s_batal)) }
                }
            } else {
                Button(onClick = { onExit(false) }) { Text(tr(R.string.s_keluar)) }
            }
        },
        dismissButton = {
            if (!serverRunning) TextButton(onClick = onDismiss) { Text(tr(R.string.s_batal)) }
        }
    )
}
