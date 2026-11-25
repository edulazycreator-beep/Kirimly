package com.kirimly.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kirimly.app.ui.KirimlyApp
import com.kirimly.app.ui.MainViewModel
import com.kirimly.app.ui.theme.KirimlyTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            viewModel.handleShareIntent(intent)
        }

        setContent {
            val preferences by viewModel.preferences.collectAsStateWithLifecycle()
            KirimlyTheme(themeMode = preferences.themeMode, palette = preferences.palette) {
                KirimlyApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.handleShareIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAddress()
    }

    companion object {
        const val EXTRA_OPEN_TAB = MainViewModel.EXTRA_OPEN_TAB
        const val TAB_RECEIVE = MainViewModel.ROUTE_RECEIVE
    }
}
