package com.kirimly.app.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.kirimly.app.KirimlyApplication
import com.kirimly.app.MainActivity
import com.kirimly.app.R
import com.kirimly.app.data.model.ServerState
import com.kirimly.app.ui.MainViewModel

/**
 * Quick Settings tile: one pull-down and Kirimly opens on Receive, which (re)starts receiving.
 * Shown as active while the server is running.
 */
class ReceiveTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val running = (application as KirimlyApplication).container.serverManager.serverState.value is ServerState.Running
        qsTile?.apply {
            state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(R.string.tile_label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = getString(if (running) R.string.tile_on else R.string.tile_off)
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { openReceive() } else openReceive()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openReceive() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainViewModel.EXTRA_OPEN_TAB, MainViewModel.ROUTE_RECEIVE)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this, REQUEST_RECEIVE, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private companion object {
        const val REQUEST_RECEIVE = 41
    }
}
