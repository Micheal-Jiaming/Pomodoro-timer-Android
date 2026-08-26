package io.github.michealjiaming.pomodoro

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

/**
 * The only activity. It owns the window — background colour, system bars, keeping
 * the screen awake — and hands everything else to Compose.
 *
 * It holds no timer state of its own; [TimerViewModel] owns all of it, and
 * `by viewModels()` is what makes that state outlive a recreation of this activity
 * where a plain field would not.
 *
 * Rotation is *not* the example to reach for, despite being the usual one. The
 * manifest declares `configChanges="orientation|screenSize|screenLayout|..."`, so a
 * rotation is delivered as `onConfigurationChanged` and this activity is never
 * destroyed for it. What `viewModels()` covers is the configuration changes *not*
 * on that list — a locale change, for instance — which do recreate the activity.
 *
 * It does **not** survive the process being killed: a ViewModel is retained across
 * configuration changes only, and after process death a fresh one is constructed.
 * Surviving that is [TimerViewModel.restore]'s job, rebuilding from `Prefs`, which
 * is why the countdown is persisted rather than merely held in memory.
 */
class MainActivity : ComponentActivity() {

    private val vm: TimerViewModel by viewModels()

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Deliberately empty. Be clear about the cost, because it is larger
            // than it looks: declining suppresses every notification this app
            // posts, which means both the ongoing countdown in the shade
            // (Alerts.showRunning, posted on start and on restore) and the notice
            // when a session ends off-screen. The app loses its whole presence in
            // the shade for the session, not just the finished-session alert.
            //
            // Everything outside the shade still works - the timer, the scheduled
            // alarm and its sound, and the in-app finished prompt - so there is
            // nothing to recover from here and nothing worth nagging about.
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
            // Keyed on `palette`, so this re-runs whenever the theme is cycled.
            //
            // The two suppressions are deliberate, not leftovers. Both setters are
            // deprecated from Android 15 in favour of drawing behind the bars with
            // edge-to-edge insets, but they still work, and the alternative is a
            // layout change this app does not otherwise need. Revisit if targetSdk
            // moves to 35 or beyond.
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
        // Catch up on anything that finished while the app was away. A countdown is
        // held as a deadline rather than a ticking loop, so returning to the app
        // after minutes or days away is the same operation: compare the stored
        // deadline with the clock and act on the result.
        vm.onResume()
    }

    override fun onStop() {
        // Set BEFORE super.onStop(), and the order is load-bearing. Sessions
        // .completeOnce reads this flag to decide whether to post a notification or
        // let the in-app prompt handle it. If a session ends during the teardown
        // that super.onStop() performs, a flag still reading `true` would mean no
        // notification is posted for a session the user can no longer see.
        AppVisibility.foreground = false
        super.onStop()
    }

    /**
     * Ask for notification permission, but only where such a permission exists.
     *
     * POST_NOTIFICATIONS was introduced in Android 13 (API 33, codenamed TIRAMISU).
     * Below that version notifications need no runtime permission at all, so the
     * early return is not a feature being skipped — there is simply nothing to ask
     * for, and the notifications work regardless.
     *
     * Asking is one-shot and unforced: if the user has already decided, Android
     * suppresses the dialog, and a refusal is accepted silently.
     */
    private fun askForNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
