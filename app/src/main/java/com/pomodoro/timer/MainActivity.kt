package com.pomodoro.timer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {

    private val vm: TimerViewModel by viewModels()

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Declining only costs the background alert; the app still works.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Paint the window in the saved theme before the first frame, otherwise
        // a light theme starts with a dark flash.
        window.setBackgroundDrawable(ColorDrawable(paletteFor(Prefs(this).theme).bg.toArgb()))

        askForNotificationsIfNeeded()

        setContent {
            val palette = vm.palette
            val view = LocalView.current

            // System bars follow the theme, so a light theme gets dark icons.
            LaunchedEffect(palette) {
                @Suppress("DEPRECATION")
                window.statusBarColor = palette.bg.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = palette.bg.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = palette.light
                    isAppearanceLightNavigationBars = palette.light
                }
            }

            // The desktop's "always on top" has no meaning here; keeping the
            // screen awake while a countdown runs is the equivalent idea.
            DisposableEffect(vm.keepScreenOn, vm.state.running) {
                view.keepScreenOn = vm.keepScreenOn && vm.state.running
                onDispose { view.keepScreenOn = false }
            }

            PomodoroTheme(palette) {
                PomodoroScreen(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.foreground = true
        // Catch up on anything that finished while the app was away.
        vm.onResume()
    }

    override fun onStop() {
        AppVisibility.foreground = false
        super.onStop()
    }

    private fun askForNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
