package io.github.michealjiaming.pomodoro

/** Work or break, exactly as on the desktop. */
enum class Mode {
    WORK,
    BREAK;

    val label: String get() = if (this == BREAK) "BREAK" else "WORK"

    companion object {
        fun from(name: String?): Mode = if (name == BREAK.name) BREAK else WORK
    }
}

val WORK_PRESETS = listOf(15, 30, 45)   // minutes
val BREAK_PRESETS = listOf(5, 10, 15)

const val CUSTOM_MIN = 1
const val CUSTOM_MAX = 180

data class TimerState(
    val mode: Mode = Mode.WORK,
    val totalSeconds: Int = WORK_PRESETS[0] * 60,
    val remainingMillis: Long = WORK_PRESETS[0] * 60_000L,
    val running: Boolean = false,
    val sessionsDone: Int = 0,
    /** Set when a countdown has just ended; drives the same prompt the desktop shows. */
    val prompt: Mode? = null,
) {
    val fraction: Float
        get() = if (totalSeconds <= 0) 0f
        else (remainingMillis.toFloat() / (totalSeconds * 1000f)).coerceIn(0f, 1f)
}

/** mm:ss, rounding up so the last second is shown as 00:01 rather than 00:00. */
fun formatRemaining(millis: Long): String {
    val seconds = ((millis + 999L) / 1000L).coerceAtLeast(0L)
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}
