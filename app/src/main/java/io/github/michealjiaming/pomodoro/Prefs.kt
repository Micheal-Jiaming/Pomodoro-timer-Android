package io.github.michealjiaming.pomodoro

import android.content.Context

/**
 * The Android counterpart of the desktop app's settings.json.
 *
 * Besides the theme it records the countdown in flight, so a session that ends
 * while the app is in the background — or after its process has been killed —
 * is still counted and still announces itself when the app is next opened.
 */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("pomodoro", Context.MODE_PRIVATE)

    var theme: String
        get() = sp.getString(KEY_THEME, PALETTES[0].key) ?: PALETTES[0].key
        set(value) = sp.edit().putString(KEY_THEME, value).apply()

    var keepScreenOn: Boolean
        get() = sp.getBoolean(KEY_KEEP_SCREEN_ON, true)
        set(value) = sp.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()

    var sessionsDone: Int
        get() = sp.getInt(KEY_SESSIONS, 0)
        set(value) = sp.edit().putInt(KEY_SESSIONS, value).apply()

    /** Wall-clock deadline of a running countdown, or 0. Wall clock, not uptime,
     *  because this has to survive the process going away. */
    var pendingDeadline: Long
        get() = sp.getLong(KEY_PENDING_DEADLINE, 0L)
        set(value) = sp.edit().putLong(KEY_PENDING_DEADLINE, value).apply()

    var pendingMode: Mode
        get() = Mode.from(sp.getString(KEY_PENDING_MODE, null))
        set(value) = sp.edit().putString(KEY_PENDING_MODE, value.name).apply()

    var pendingTotalSeconds: Int
        get() = sp.getInt(KEY_PENDING_TOTAL, WORK_PRESETS[0] * 60)
        set(value) = sp.edit().putInt(KEY_PENDING_TOTAL, value).apply()

    /** A countdown that finished while nobody was looking; null once shown. */
    var finishedMode: Mode?
        get() = sp.getString(KEY_FINISHED_MODE, null)?.let { Mode.from(it) }
        set(value) = sp.edit().putString(KEY_FINISHED_MODE, value?.name).apply()

    /**
     * The deadline of the last completed session. Both the in-app ticker and the
     * alarm receiver try to complete a session; this is what stops the two of
     * them counting it twice when they race.
     */
    var lastCompletedDeadline: Long
        get() = sp.getLong(KEY_LAST_COMPLETED, 0L)
        set(value) = sp.edit().putLong(KEY_LAST_COMPLETED, value).apply()

    fun clearPending() {
        sp.edit().putLong(KEY_PENDING_DEADLINE, 0L).apply()
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_SESSIONS = "sessions_done"
        const val KEY_PENDING_DEADLINE = "pending_deadline"
        const val KEY_PENDING_MODE = "pending_mode"
        const val KEY_PENDING_TOTAL = "pending_total"
        const val KEY_FINISHED_MODE = "finished_mode"
        const val KEY_LAST_COMPLETED = "last_completed_deadline"
    }
}
