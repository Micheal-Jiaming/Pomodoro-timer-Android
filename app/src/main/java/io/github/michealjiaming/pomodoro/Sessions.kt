package io.github.michealjiaming.pomodoro

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

private const val ACTION_FINISHED = "io.github.michealjiaming.pomodoro.SESSION_FINISHED"
private const val EXTRA_MODE = "mode"
private const val EXTRA_DEADLINE = "deadline"
private const val REQUEST_CODE = 100

/**
 * Wakes the app up at the end of a countdown.
 *
 * The screen is often off by then, so the end of a session cannot depend on the
 * UI still ticking. An exact alarm fires either way, and it is what makes the
 * timer trustworthy when the phone is in a pocket.
 */
object AlarmScheduler {

    /**
     * Arm the alarm that ends the current session.
     *
     * deadlineWallMs is when the session ends, in milliseconds since the epoch
     * (wall clock, not `elapsedRealtime` — an alarm can only be expressed on the
     * wall clock). mode is the session being timed, and rides along in the
     * broadcast so the receiver knows what finished.
     *
     * `RTC_WAKEUP` is what wakes the device from sleep; without it the alarm
     * would simply wait for the next time someone switched the screen on.
     * Re-arming replaces any previous alarm rather than adding one — see
     * [pendingIntent]. A device with no AlarmManager at all is a no-op, which in
     * practice cannot happen.
     */
    fun schedule(context: Context, deadlineWallMs: Long, mode: Mode) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        try {
            manager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                deadlineWallMs,
                pendingIntent(context, deadlineWallMs, mode),
            )
        } catch (_: SecurityException) {
            // Exact alarms refused — the permission can be revoked by the user or
            // the OEM. Fall back to an inexact alarm: the session is still
            // announced, but the OS may hold it until the next Doze maintenance
            // window, which can be considerably later than the deadline rather
            // than merely a minute. Late is the trade being accepted here; the
            // point is that nothing is silently dropped.
            manager.set(
                AlarmManager.RTC_WAKEUP,
                deadlineWallMs,
                pendingIntent(context, deadlineWallMs, mode),
            )
        }
    }

    /**
     * Drop the pending end-of-session alarm, if one is outstanding.
     *
     * Nothing needs to be passed in, because there is only ever one alarm:
     * [REQUEST_CODE] is fixed, and PendingIntent matching ignores extras, so the
     * live alarm can be found without knowing its deadline or mode.
     *
     * A missing AlarmManager and "no alarm pending" are both treated as success
     * and return quietly — the caller wants no alarm outstanding, and in either
     * case none is.
     */
    fun cancel(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        // This deliberately does NOT use FLAG_UPDATE_CURRENT, and that is the
        // whole reason the lookup is a separate method. Extras play no part in
        // matching a PendingIntent, but FLAG_UPDATE_CURRENT *rewrites* them on
        // the live one. Cancelling therefore used to overwrite the armed
        // deadline with 0, and a broadcast already dispatched but not yet
        // delivered would arrive carrying 0 — which the duplicate guard in
        // Sessions.completeOnce then waved through, counting one session twice.
        val pending = existingAlarm(context) ?: return
        manager.cancel(pending)
        // Retire the PendingIntent itself too, so a later existingAlarm() lookup
        // correctly reports that nothing is outstanding.
        pending.cancel()
    }

    /**
     * The broadcast that ends a session: an explicit intent aimed at
     * [FinishReceiver], carrying which mode finished and the deadline that
     * identifies the session.
     *
     * deadline is milliseconds since the epoch, on the same clock as
     * System.currentTimeMillis. It doubles as the session's identity in
     * [Sessions.completeOnce], which is why a real session's deadline is never 0.
     */
    private fun finishIntent(context: Context, deadline: Long, mode: Mode): Intent =
        Intent(context, FinishReceiver::class.java)
            .setAction(ACTION_FINISHED)
            .putExtra(EXTRA_MODE, mode.name)
            .putExtra(EXTRA_DEADLINE, deadline)

    /**
     * The PendingIntent used to *arm* an alarm. FLAG_UPDATE_CURRENT belongs here
     * and only here: the extras change from one session to the next while the
     * request code does not, so re-arming replaces the previous alarm and its
     * now-stale deadline instead of stacking a second one.
     */
    private fun pendingIntent(context: Context, deadline: Long, mode: Mode): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST_CODE, finishIntent(context, deadline, mode),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * The PendingIntent for an alarm that is *already* armed, or null if there is
     * none. FLAG_NO_CREATE neither creates nor modifies, so this is safe to call
     * when nothing is running. The values handed to [finishIntent] are
     * placeholders: matching compares action, class and data, never extras.
     *
     * PendingIntents are owned by the system rather than by this process, so this
     * still finds an alarm armed before the app was killed and restarted.
     */
    private fun existingAlarm(context: Context): PendingIntent? =
        PendingIntent.getBroadcast(
            context, REQUEST_CODE, finishIntent(context, 0L, Mode.WORK),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
}

/**
 * Completing a session is funnelled through one place because two things race
 * to do it: the in-app ticker reaching zero, and the alarm going off. Whichever
 * arrives first records the session's deadline; the other finds that same
 * deadline already recorded and returns without doing anything, so a session is
 * counted and announced exactly once.
 *
 * The deadline *is* the session's identity, which is why a call carrying no
 * deadline at all is refused outright rather than let through unchecked.
 */
object Sessions {

    /**
     * Finish the session identified by [deadlineWallMs], exactly once.
     *
     * mode is the session that just ended, and decides whether the completed-work
     * counter advances — only [Mode.WORK] does. deadlineWallMs is that session's
     * wall-clock end in milliseconds since the epoch, the same value that was
     * persisted and put into the alarm broadcast.
     *
     * Returns true if this call is the one that completed the session, false if it
     * was a duplicate or was refused. Callers may ignore the result: the in-app
     * path calls this knowing the alarm may have won the race.
     */
    @Synchronized
    fun completeOnce(context: Context, mode: Mode, deadlineWallMs: Long): Boolean {
        // A deadline of 0 is not a session. Every in-app caller passes a live
        // deadline, and restore() only reaches this from inside `if (deadline >
        // 0L)`, so the one way to arrive here with 0 is a broadcast that reached
        // FinishReceiver without EXTRA_DEADLINE, leaving getLongExtra to hand back
        // its default. Refusing it is what makes the check below unbypassable: the
        // guard used to be skipped whenever the deadline was 0, which is exactly
        // what a malformed broadcast produces.
        if (deadlineWallMs == 0L) return false
        val prefs = Prefs(context)
        // Whoever got here first stored this same deadline, so a second arrival
        // for the same session recognises it and stops.
        if (prefs.lastCompletedDeadline == deadlineWallMs) return false
        prefs.lastCompletedDeadline = deadlineWallMs
        prefs.clearPending()
        if (mode == Mode.WORK) {
            prefs.sessionsDone = prefs.sessionsDone + 1
        }
        prefs.finishedMode = mode

        AlarmScheduler.cancel(context)
        Alerts.cancelRunning(context)
        Alerts.alert(context)
        // On the desktop the window raises itself; here that only makes sense
        // when the app is not already the thing you are looking at.
        if (!AppVisibility.foreground) {
            Alerts.showFinished(context, mode, prefs.sessionsDone)
        }
        return true
    }
}

/** Set from the activity so alerts know whether the user can already see them. */
object AppVisibility {
    @Volatile
    var foreground: Boolean = false
}

/**
 * Receives the alarm and ends the session. Declared `exported="false"` in the
 * manifest and only ever targeted by an explicit intent, so nothing outside the
 * app can reach it.
 */
class FinishReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // This can run in a process Android has just created from nothing to
        // deliver the broadcast, so no earlier setup can be assumed — hence
        // ensureChannels() below rather than relying on the activity having run.
        // onReceive is also on the main thread with a short budget (about ten
        // seconds), which is why the work here stays synchronous and small.
        val mode = Mode.from(intent.getStringExtra(EXTRA_MODE))
        // Missing extras degrade instead of crashing: Mode.from maps anything
        // unrecognised to WORK, and a missing deadline becomes 0 — which
        // completeOnce refuses outright, so a malformed broadcast does nothing.
        val deadline = intent.getLongExtra(EXTRA_DEADLINE, 0L)
        Alerts.ensureChannels(context)
        Sessions.completeOnce(context, mode, deadline)
    }
}
