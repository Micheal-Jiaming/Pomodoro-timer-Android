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

    fun schedule(context: Context, deadlineWallMs: Long, mode: Mode) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        try {
            manager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                deadlineWallMs,
                pendingIntent(context, deadlineWallMs, mode),
            )
        } catch (_: SecurityException) {
            // Exact alarms refused: fall back to an inexact one. It may be a
            // minute late in deep doze, but nothing is silently lost.
            manager.set(
                AlarmManager.RTC_WAKEUP,
                deadlineWallMs,
                pendingIntent(context, deadlineWallMs, mode),
            )
        }
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        manager.cancel(pendingIntent(context, 0L, Mode.WORK))
    }

    private fun pendingIntent(context: Context, deadline: Long, mode: Mode): PendingIntent {
        // The extras change but the request code does not, so a new schedule
        // replaces the previous alarm instead of stacking another one.
        val intent = Intent(context, FinishReceiver::class.java)
            .setAction(ACTION_FINISHED)
            .putExtra(EXTRA_MODE, mode.name)
            .putExtra(EXTRA_DEADLINE, deadline)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/**
 * Completing a session is funnelled through one place because two things race
 * to do it: the in-app ticker reaching zero, and the alarm going off. Whichever
 * arrives first does the work; the other sees the deadline already recorded and
 * leaves it alone, so a session is never counted or announced twice.
 */
object Sessions {

    @Synchronized
    fun completeOnce(context: Context, mode: Mode, deadlineWallMs: Long): Boolean {
        val prefs = Prefs(context)
        if (deadlineWallMs != 0L && prefs.lastCompletedDeadline == deadlineWallMs) {
            return false
        }
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

class FinishReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val mode = Mode.from(intent.getStringExtra(EXTRA_MODE))
        val deadline = intent.getLongExtra(EXTRA_DEADLINE, 0L)
        Alerts.ensureChannels(context)
        Sessions.completeOnce(context, mode, deadline)
    }
}
