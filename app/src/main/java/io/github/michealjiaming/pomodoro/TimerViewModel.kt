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

    private var deadlineElapsed = 0L
    private var deadlineWall = 0L

    init {
        Alerts.ensureChannels(application)
        restore()
        viewModelScope.launch {
            while (true) {
                if (state.running) tick()
                delay(if (state.running) TICK_MS else IDLE_MS)
            }
        }
    }

    // ------------------------------------------------------------- controls
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

    fun toggle() = if (state.running) pause() else start()

    fun start() {
        val remaining =
            if (state.remainingMillis <= 0) state.totalSeconds * 1000L else state.remainingMillis
        deadlineElapsed = SystemClock.elapsedRealtime() + remaining
        deadlineWall = System.currentTimeMillis() + remaining

        prefs.pendingDeadline = deadlineWall
        prefs.pendingMode = state.mode
        prefs.pendingTotalSeconds = state.totalSeconds

        AlarmScheduler.schedule(app, deadlineWall, state.mode)
        Alerts.cancelFinished(app)
        Alerts.showRunning(app, state.mode, deadlineWall)

        state = state.copy(running = true, remainingMillis = remaining, prompt = null)
    }

    fun pause() {
        val left = (deadlineElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        stopCountdown()
        state = state.copy(running = false, remainingMillis = left)
    }

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

    fun dismissPrompt() {
        state = state.copy(prompt = null)
    }

    fun cycleTheme() {
        palette = nextPalette(palette.key)
        prefs.theme = palette.key
    }

    // Not setKeepScreenOn: that is the JVM name the property's own setter takes.
    fun updateKeepScreenOn(value: Boolean) {
        keepScreenOn = value
        prefs.keepScreenOn = value
    }

    // -------------------------------------------------------------- ticking
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

    private fun stopCountdown() {
        AlarmScheduler.cancel(app)
        Alerts.cancelRunning(app)
        prefs.clearPending()
    }

    // ------------------------------------------------------------ restoring
    /** Called when the app comes back to the foreground. */
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

        if (deadline > 0L) {
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
        const val TICK_MS = 200L    // the desktop refreshes at the same rate
        const val IDLE_MS = 750L
    }
}
