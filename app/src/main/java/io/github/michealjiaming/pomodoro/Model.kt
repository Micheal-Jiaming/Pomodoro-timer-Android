package io.github.michealjiaming.pomodoro

/** Work or break, exactly as on the desktop. */
enum class Mode {
    WORK,
    BREAK;

    /** The uppercase text shown under the ring. */
    val label: String get() = if (this == BREAK) "BREAK" else "WORK"

    companion object {
        /**
         * Turn a stored or broadcast string back into a Mode.
         *
         * Deliberately total: null, an empty string, an old name and anything
         * misspelled all become WORK rather than throwing. This is the read side of
         * two lossy channels — SharedPreferences and an intent extra — and a
         * corrupt value there should start a work session, not crash the app on
         * launch. The cost is that a genuine bug in the write side would be silent.
         */
        fun from(name: String?): Mode = if (name == BREAK.name) BREAK else WORK
    }
}

// The preset buttons, in minutes. Index 0 of WORK_PRESETS doubles as the app's
// starting duration, via TimerState's defaults below.
val WORK_PRESETS = listOf(15, 30, 45)
val BREAK_PRESETS = listOf(5, 10, 15)

// Bounds for the Custom dialog, in minutes. CUSTOM_MAX being three digits is
// relied on by the input filter in Ui.kt, which caps typing at three characters.
const val CUSTOM_MIN = 1
const val CUSTOM_MAX = 180

/**
 * Everything the screen needs to draw itself, in one immutable snapshot.
 *
 * totalSeconds is the session's full length and remainingMillis what is left of it;
 * the two use different units because the UI counts down in milliseconds while a
 * session is only ever *chosen* in whole minutes. running distinguishes a paused
 * countdown from a live one, and prompt is non-null only in the moment after a
 * session ends.
 *
 * Being a data class matters: Compose redraws when the object changes identity, so
 * updates are made with copy() rather than by mutating fields.
 */
data class TimerState(
    val mode: Mode = Mode.WORK,
    val totalSeconds: Int = WORK_PRESETS[0] * 60,
    val remainingMillis: Long = WORK_PRESETS[0] * 60_000L,
    val running: Boolean = false,
    val sessionsDone: Int = 0,
    /** Set when a countdown has just ended; drives the same prompt the desktop shows. */
    val prompt: Mode? = null,
) {
    /**
     * How much of the session is still to run, from 1.0 at the start down to 0.0
     * at the end. This is what the ring sweeps, so it counts *down*, not up.
     *
     * Returns 0f when totalSeconds is 0 or negative — an empty ring rather than a
     * division by zero. Clamped at both ends because remainingMillis can briefly
     * exceed the total between the deadline being set and the first tick.
     */
    val fraction: Float
        get() = if (totalSeconds <= 0) 0f
        else (remainingMillis.toFloat() / (totalSeconds * 1000f)).coerceIn(0f, 1f)
}

/**
 * Format milliseconds as mm:ss for the middle of the ring.
 *
 * Rounds *up*, which is why 999 is added before dividing: a timer showing 00:00
 * while a second still remains looks broken, so the last second reads 00:01 and
 * 00:00 appears only when the session is genuinely over. Negative input — possible
 * if the deadline has just passed — is floored at 0 rather than printing "-1:59".
 *
 * Minutes are not capped, so a 180-minute session correctly reads 180:00 rather
 * than wrapping at 60.
 */
fun formatRemaining(millis: Long): String {
    val seconds = ((millis + 999L) / 1000L).coerceAtLeast(0L)
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}
