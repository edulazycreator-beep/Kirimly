package com.kirimly.app

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnticipateInterpolator
import android.view.animation.PathInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kirimly.app.data.prefs.UserPreferencesRepository
import com.kirimly.app.ui.KirimlyApp
import com.kirimly.app.ui.MainViewModel
import com.kirimly.app.ui.screens.OnboardingScreen
import com.kirimly.app.ui.theme.KirimlyTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Hold the splash until preferences (theme, onboarding flag) are known, so the first
        // frame is already the right screen in the right theme.
        splash.setKeepOnScreenCondition { viewModel.loadedPreferences.value == null }
        splash.setOnExitAnimationListener { provider ->
            val root: View = provider.view
            val icon: View? = try {
                provider.iconView
            } catch (_: Exception) {
                null
            }
            val fade = ObjectAnimator.ofFloat(root, View.ALPHA, 1f, 0f).apply {
                duration = 260L
                startDelay = 120L
                interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
            }
            val animators = mutableListOf<android.animation.Animator>(fade)
            if (icon != null) {
                animators += ObjectAnimator.ofFloat(icon, View.TRANSLATION_X, 0f, icon.width * 0.35f).apply {
                    duration = 380L
                    interpolator = AnticipateInterpolator(1.4f)
                }
                animators += ObjectAnimator.ofFloat(icon, View.TRANSLATION_Y, 0f, -icon.height * 0.35f).apply {
                    duration = 380L
                    interpolator = AnticipateInterpolator(1.4f)
                }
            }
            AnimatorSet().apply {
                playTogether(animators)
                doOnEnd { provider.remove() }
                start()
            }
        }

        if (savedInstanceState == null) {
            viewModel.handleShareIntent(intent)
        }

        setContent {
            val loaded by viewModel.loadedPreferences.collectAsStateWithLifecycle()
            val preferences = loaded ?: UserPreferencesRepository.DEFAULT
            KirimlyTheme(themeMode = preferences.themeMode, palette = preferences.palette) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val current = loaded
                    if (current != null) {
                        AnimatedContent(
                            targetState = current.onboardingCompleted,
                            transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(220)) },
                            label = "onboarding"
                        ) { completed ->
                            if (completed) {
                                KirimlyApp(viewModel = viewModel)
                            } else {
                                OnboardingScreen(onFinish = viewModel::completeOnboarding)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.handleShareIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        (application as KirimlyApplication).container.appInForeground.value = true
        // Receiving is always on while Kirimly is open (it may have stopped itself while idle).
        viewModel.ensureServerRunning()
    }

    override fun onStop() {
        super.onStop()
        (application as KirimlyApplication).container.appInForeground.value = false
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
