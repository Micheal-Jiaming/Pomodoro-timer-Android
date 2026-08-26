package io.github.michealjiaming.pomodoro

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The timer itself. Same state machine as the desktop app, including the part
 * that matters most: the countdown is derived from a deadline rather than by
 * decrementing a counter, so it cannot drift.
 *
 * Two clocks are kept because they answer different questions. elapsedRealtime
 * is monotonic and immune to the user changing the time, so it drives the
 * countdown; the wall clock is what an alarm and a saved file can be expressed
 * in, so it is what gets scheduled and persisted.
 */
class TimerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val prefs = Prefs(application)

    var state by mutableStateOf(TimerState())
        private set

    var palette by mutableStateOf(paletteFor(prefs.theme))
        private set

    var keepScreenOn by mutableStateOf(prefs.keepScreenOn)
        private set

    // The same instant expressed on both clocks, or 0 when nothing is running.
    // deadlineElapsed drives the display; deadlineWall is what gets persisted and
    // handed to the alarm.
    private var deadlineElapsed = 0L
    private var deadlineWall = 0L

    init {
        Alerts.ensureChannels(application)
        restore()
        // One coroutine for the whole life of the view model. It is deliberately
        // never cancelled and has no exit condition: viewModelScope cancels it when
        // the view model dies, and giving it a `while (state.running)` condition
        // would mean starting a fresh coroutine on every Start, which is more moving
        // parts for no gain.
        viewModelScope.launch {
            while (true) {
                if (state.running) tick()
                // Polling idles at a slower rate rather than stopping. It still has
                // to wake up while paused, because `running` can be turned on by a
                // button press or by restore(), and the loop is what notices. IDLE_MS
                // is the compromise: slow enough to be negligible, fast enough that
                // pressing Start feels immediate.
                delay(if (state.running) TICK_MS else IDLE_MS)
            }
        }
    }

    // ------------------------------------------------------------- controls

    /**
     * Choose a session length without starting it.
     *
     * minutes is clamped to `CUSTOM_MIN..CUSTOM_MAX` (1..180), so an out-of-range
     * value is silently pulled into range rather than rejected — the UI validates
     * before calling, and this is the backstop. mode selects work or break.
     *
     * Has a side effect worth knowing: it cancels any countdown in flight, alarm
     * and notification included. Picking a new duration therefore abandons the
     * current session rather than queuing behind it.
     */
    fun setDuration(minutes: Int, mode: Mode) {
        stopCountdown()
        val total = minutes.coerceIn(CUSTOM_MIN, CUSTOM_MAX) * 60
        state = state.copy(
            mode = mode,
            totalSeconds = total,
            remainingMillis = total * 1000L,
            running = false,
            prompt = null,
        )
    }

    /** What the single Start/Pause button does, whichever it currently says. */
    fun toggle() = if (state.running) pause() else start()

    /**
     * Start or resume the countdown. This is the one place a session is armed, and
     * it does five things that must all happen together.
     *
     * Resuming uses whatever time is left; starting from a finished session (0 left)
     * restarts the full length, which is what makes pressing Start after a session
     * ends do something sensible instead of nothing.
     */
    fun start() {
        val remaining =
            if (state.remainingMillis <= 0) state.totalSeconds * 1000L else state.remainingMillis
        // Both clocks are set from the same `remaining`, so they describe one instant.
        deadlineElapsed = SystemClock.elapsedRealtime() + remaining
        deadlineWall = System.currentTimeMillis() + remaining

        // Persisted before the alarm is set. If the process dies between these two
        // groups the session is recoverable from disk; the other order could leave an
        // alarm armed for a session nothing remembers.
        prefs.pendingDeadline = deadlineWall
        prefs.pendingMode = state.mode
        prefs.pendingTotalSeconds = state.totalSeconds

        AlarmScheduler.schedule(app, deadlineWall, state.mode)
        // Clear any leftover "finished" notification from the previous session before
        // posting the new running one, or the shade shows both at once.
        Alerts.cancelFinished(app)
        Alerts.showRunning(app, state.mode, deadlineWall)

        state = state.copy(running = true, remainingMillis = remaining, prompt = null)
    }

    /**
     * Stop the countdown but keep the remaining time, so Start resumes from here.
     *
     * The remaining time is read from the monotonic clock rather than from
     * `state.remainingMillis`, because the latter is only as fresh as the last tick —
     * up to TICK_MS stale. Floored at 0 in case the deadline passed between the last
     * tick and this call.
     */
    fun pause() {
        val left = (deadlineElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        stopCountdown()
        state = state.copy(running = false, remainingMillis = left)
    }

    /**
     * Back to a full, stopped session of the current length. Unlike [pause] this
     * discards the remaining time, and unlike [setDuration] it keeps the length and
     * mode. Clearing `prompt` also dismisses the end-of-session dialog if it is open.
     */
    fun reset() {
        stopCountdown()
        state = state.copy(
            running = false,
            remainingMillis = state.totalSeconds * 1000L,
            prompt = null,
        )
    }

    /** Pick a duration and run it straight away — the prompt buttons. */
    fun beginNext(minutes: Int, mode: Mode) {
        setDuration(minutes, mode)
        start()
    }

    /**
     * Close the end-of-session dialog without starting anything ("Not now"). The
     * session has already been counted by then, so this only hides the prompt.
     */
    fun dismissPrompt() {
        state = state.copy(prompt = null)
    }

    /**
     * Advance to the next theme and remember it. Persisted immediately rather than
     * on exit, because the process can be killed without warning and a theme that
     * forgot itself would be the most visible possible bug.
     */
    fun cycleTheme() {
        palette = nextPalette(palette.key)
        prefs.theme = palette.key
    }

    /**
     * Turn the keep-screen-awake preference on or off, and persist it.
     *
     * Named `updateKeepScreenOn` rather than `setKeepScreenOn` because the latter is
     * the JVM name Kotlin already generates for the `keepScreenOn` property's own
     * setter — identical signature, so the two cannot coexist. Do not rename it back.
     *
     * Note this only records the preference. Whether the screen is actually held
     * awake is decided in MainActivity, which additionally requires a running
     * countdown.
     */
    fun updateKeepScreenOn(value: Boolean) {
        keepScreenOn = value
        prefs.keepScreenOn = value
    }

    // -------------------------------------------------------------- ticking

    /**
     * One step of the countdown: work out what is left and either redraw or finish.
     *
     * Called from the polling loop while running, and once from [onResume] to catch
     * up after time spent in the background. Because the answer is computed from the
     * deadline rather than accumulated, a missed tick costs nothing — the next one
     * shows the correct time regardless of how long the gap was.
     */
    private fun tick() {
        val left = deadlineElapsed - SystemClock.elapsedRealtime()
        if (left <= 0L) finish() else state = state.copy(remainingMillis = left)
    }

    private fun finish() {
        val finished = state.mode
        // May be a no-op: the alarm can get there first. Either way the prompt
        // is shown and the count comes back from the one place that owns it.
        Sessions.completeOnce(app, finished, deadlineWall)
        prefs.finishedMode = null      // about to be shown in the app itself
        Alerts.cancelFinished(app)
        state = state.copy(
            running = false,
            remainingMillis = 0L,
            sessionsDone = prefs.sessionsDone,
            prompt = finished,
        )
    }

    /**
     * Tear down everything a running countdown owns outside this object: the alarm,
     * the ongoing notification, and the persisted deadline.
     *
     * Called by pause, reset and setDuration. It deliberately does not touch `state`
     * — each caller leaves the timer in a different place, so they set that
     * themselves. Missing any one of these three is what leaves a phantom timer
     * behind: an alarm that fires for a cancelled session, a notification counting
     * down to nothing, or a deadline that gets restored on next launch.
     */
    private fun stopCountdown() {
        AlarmScheduler.cancel(app)
        Alerts.cancelRunning(app)
        prefs.clearPending()
    }

    // ------------------------------------------------------------ restoring

    /**
     * Called when the app comes back to the foreground, to catch up on anything that
     * happened while it was away.
     *
     * Three cases, in order. A countdown still believed to be running is ticked once,
     * which either redraws it or finishes it on the spot. Otherwise, a session that
     * ended in the background — recorded by the alarm receiver in `finishedMode` — is
     * turned into the prompt. Failing both, the counter alone may have moved, and is
     * copied across.
     *
     * The finished notification is withdrawn first in every case, because the app is
     * now visible and about to say the same thing on screen.
     */
    fun onResume() {
        Alerts.cancelFinished(app)
        if (state.running) {
            tick()
            return
        }
        val finished = prefs.finishedMode
        if (finished != null) {
            prefs.finishedMode = null
            state = state.copy(
                mode = finished,
                remainingMillis = 0L,
                sessionsDone = prefs.sessionsDone,
                prompt = finished,
            )
        } else if (state.sessionsDone != prefs.sessionsDone) {
            state = state.copy(sessionsDone = prefs.sessionsDone)
        }
    }

    /**
     * Rebuild whatever was going on before the process went away: a countdown
     * still in flight is picked up mid-air, one that ran out is completed.
     */
    private fun restore() {
        var restored = TimerState(sessionsDone = prefs.sessionsDone)
        val deadline = prefs.pendingDeadline

        // A non-zero deadline means a countdown was in flight when the process went
        // away. Note that everything below this point is guaranteed a real deadline,
        // which is why Sessions.completeOnce can refuse a zero one outright.
        if (deadline > 0L) {
            // Wall clock, not elapsedRealtime: the process may have been gone across
            // a reboot, which resets elapsedRealtime but not the wall clock.
            val left = deadline - System.currentTimeMillis()
            val mode = prefs.pendingMode
            val total = prefs.pendingTotalSeconds
            if (left > 0L) {
                deadlineWall = deadline
                deadlineElapsed = SystemClock.elapsedRealtime() + left
                Alerts.showRunning(app, mode, deadline)
                restored = restored.copy(
                    mode = mode,
                    totalSeconds = total,
                    remainingMillis = left,
                    running = true,
                )
            } else {
                Sessions.completeOnce(app, mode, deadline)
                prefs.finishedMode = null
                restored = restored.copy(
                    mode = mode,
                    totalSeconds = total,
                    remainingMillis = 0L,
                    sessionsDone = prefs.sessionsDone,
                    prompt = mode,
                )
            }
        } else {
            val finished = prefs.finishedMode
            if (finished != null) {
                prefs.finishedMode = null
                restored = restored.copy(
                    mode = finished,
                    totalSeconds = prefs.pendingTotalSeconds,
                    remainingMillis = 0L,
                    prompt = finished,
                )
            }
        }
        state = restored
    }

    private companion object {
        // How often the display is refreshed while counting down. 200 ms is five
        // times a second: fast enough that the seconds digit never visibly lags,
        // slow enough to be trivial. It matches the desktop app's refresh rate, but
        // the reason it is right is the seconds digit, not the parity.
        const val TICK_MS = 200L
        // How often the loop wakes while nothing is running. Nothing needs redrawing
        // then; this is only how quickly the loop notices that `running` has become
        // true. Under a second, so Start feels instant, and rare enough to cost
        // nothing measurable.
        const val IDLE_MS = 750L
    }
}
