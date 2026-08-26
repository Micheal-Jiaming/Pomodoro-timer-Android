package io.github.michealjiaming.pomodoro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.PI
import kotlin.math.sin

/**
 * Sound, vibration and notifications — the Android stand-in for the desktop
 * app's three beeps and its habit of pulling the window to the front.
 */
object Alerts {

    const val CHANNEL_RUNNING = "running"
    const val CHANNEL_FINISHED = "finished"
    private const val ID_RUNNING = 1
    private const val ID_FINISHED = 2

    // The desktop plays three 880 Hz beeps of 180 ms every 260 ms. There is no
    // stock Android tone at that pitch, so the same sound is synthesised.
    private const val TONE_HZ = 880.0
    private const val BEEP_MS = 180
    private const val GAP_MS = 80
    private const val BEEPS = 3

    /**
     * Create both notification channels. Safe and cheap to call repeatedly —
     * creating a channel that already exists updates its name and leaves any
     * setting the user has since changed alone.
     *
     * It has to be called before *any* notification is posted, and on Android 8+ a
     * notification on a channel that does not exist is silently dropped. That is
     * why [FinishReceiver] calls it first: the broadcast may have started the
     * process from nothing, so no earlier call can be assumed to have happened.
     */
    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RUNNING,
                context.getString(R.string.channel_running),
                NotificationManager.IMPORTANCE_LOW,   // silent: it is just a clock
            ).apply { setShowBadge(false) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_FINISHED,
                context.getString(R.string.channel_finished),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                // The app makes its own sound, so the channel stays quiet rather
                // than layering a second one on top.
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    /**
     * What tapping either notification does: bring the app up.
     *
     * NEW_TASK is required because the notification is launched from outside any
     * activity, and CLEAR_TOP means an already-running MainActivity is reused
     * rather than a second copy being stacked on top of it.
     *
     * FLAG_IMMUTABLE is a security requirement, not a style choice: without it,
     * whoever holds this PendingIntent could fill in its blank fields and make the
     * app launch something else. There is nothing to fill in here, but immutable is
     * the correct default and Android 12+ demands one of the two flags explicitly.
     */
    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * An ongoing notification that counts down on its own — the platform ticks the
     * chronometer, so no service has to stay awake to update it. This is the
     * counterpart of the remaining time in the desktop title bar.
     *
     * mode decides the title text only. deadlineWallMs is when the session ends, in
     * milliseconds since the epoch on the same clock as `System.currentTimeMillis` —
     * the clock basis is not incidental, because the platform compares it against
     * the wall clock to render the countdown.
     *
     * Four builder calls are really one mechanism, and none works without the
     * others: `setWhen` supplies the target instant, `setShowWhen` makes it visible,
     * `setUsesChronometer` renders it as a running timer rather than a fixed time,
     * and `setChronometerCountDown` makes it count down to the instant instead of up
     * from it. Drop any one and the shade shows the wrong thing.
     *
     * `setOngoing(true)` is what stops the user swiping it away mid-session; the
     * consequence is that [cancelRunning] becomes the only way to remove it.
     */
    fun showRunning(context: Context, mode: Mode, deadlineWallMs: Long) {
        val label = if (mode == Mode.BREAK) R.string.notif_break else R.string.notif_work
        val notification = Notification.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(context.getString(label))
            .setContentText(context.getString(R.string.notif_running))
            .setContentIntent(openAppIntent(context))
            .setOngoing(true)
            .setShowWhen(true)
            .setWhen(deadlineWallMs)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .build()
        notify(context, ID_RUNNING, notification)
    }

    /**
     * Remove the ongoing countdown notification. It is `setOngoing(true)`, so the
     * user cannot swipe it away — this is the only thing that clears it, and it must
     * be called on every path that ends a countdown, or a dead timer keeps counting
     * in the shade.
     */
    fun cancelRunning(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ID_RUNNING)
    }

    /**
     * Announce a finished session in the notification shade.
     *
     * finished is the mode that ended; sessionsDone is the running total, used only
     * in the text after a work session. Called only when the app is *not* in the
     * foreground — when it is, the in-app dialog says the same thing, and both at
     * once would be saying it twice.
     *
     * The count goes through a plurals resource rather than string concatenation, so
     * "1 session" against "2 sessions" is handled by Android's own rules. This is
     * where it differs from the in-app dialog, which pluralises by hand.
     */
    fun showFinished(context: Context, finished: Mode, sessionsDone: Int) {
        val title = if (finished == Mode.WORK) R.string.prompt_work_done else R.string.prompt_break_done
        val text = if (finished == Mode.WORK) {
            context.resources.getQuantityString(
                R.plurals.sessions_completed, sessionsDone, sessionsDone
            )
        } else {
            context.getString(R.string.prompt_break_sub)
        }
        val notification = Notification.Builder(context, CHANNEL_FINISHED)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(context.getString(title))
            .setContentText(text)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
        notify(context, ID_FINISHED, notification)
    }

    /**
     * Withdraw the finished-session notification. Called when the app comes to the
     * foreground and shows the same message as a dialog, so the user is not told
     * twice about one session.
     */
    fun cancelFinished(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ID_FINISHED)
    }

    /**
     * Post a notification, tolerating a refused permission.
     *
     * Note that a refused POST_NOTIFICATIONS grant is *not* what this catch is for.
     * notify() returns void, and when notifications are blocked the platform simply
     * drops the post — only areNotificationsEnabled() would reveal it. The catch
     * exists for the narrower cases that do raise SecurityException. Either way
     * there is nothing useful to do: the session has still ended, and the in-app UI
     * will still show it. Swallowing is correct here, not laziness.
     */
    private fun notify(context: Context, id: Int, notification: Notification) {
        try {
            context.getSystemService(NotificationManager::class.java)?.notify(id, notification)
        } catch (_: SecurityException) {
            // See the note above: this is not the refused-permission path.
        }
    }

    /**
     * The end-of-session alert: three tones and a matching vibration pattern.
     *
     * Both are attempted, and neither can prevent the other — a device with no
     * vibrator still beeps, and a silenced device still buzzes.
     */
    fun alert(context: Context) {
        playBeeps()
        vibrate(context)
    }

    /**
     * Synthesise and play the three beeps.
     *
     * Android has no built-in 880 Hz tone, so the waveform is generated by hand as
     * 16-bit PCM and handed to an AudioTrack. The whole sound is built in memory
     * first — beep, gap, beep, gap, beep — and played as one buffer, so the spacing
     * cannot drift the way three separately scheduled sounds would.
     *
     * Fails silently by design: a device with no usable audio output must not stop a
     * session from ending, and the vibration and the notification carry the message
     * on their own.
     */
    private fun playBeeps() {
        try {
            // 44.1 kHz — CD rate, and universally supported. Any rate above roughly
            // 2 kHz would reproduce an 880 Hz tone (Nyquist), but an unusual rate
            // risks the device resampling, which is where artefacts creep in.
            val rate = 44_100
            // Milliseconds converted to sample counts. Integer division truncates,
            // which loses at most a fraction of a millisecond — inaudible.
            val beepSamples = rate * BEEP_MS / 1000
            val gapSamples = rate * GAP_MS / 1000
            // Three beeps with two gaps between them, hence BEEPS - 1: there is no
            // trailing silence after the last beep.
            val samples = ShortArray(BEEPS * beepSamples + (BEEPS - 1) * gapSamples)
            // A 6 ms fade in and out of each beep. Without it the waveform starts
            // and stops mid-cycle, and that instantaneous jump in amplitude is heard
            // as a click at each end of every beep.
            val fade = rate * 0.006f
            var at = 0
            repeat(BEEPS) { beep ->
                for (n in 0 until beepSamples) {
                    // Distance to the nearer end of the beep, scaled by the fade
                    // length and capped at 1. So it ramps 0 -> 1 over the first 6 ms,
                    // holds at 1, then ramps back down over the last 6 ms.
                    val envelope = minOf(1f, minOf(n, beepSamples - n).toFloat() / fade)
                    // A sine wave at TONE_HZ. 0.6 keeps it at 60% of full scale:
                    // full amplitude clips on some speakers, and this is meant to be
                    // noticeable rather than startling.
                    val value = sin(2.0 * PI * TONE_HZ * n / rate) * 0.6 * envelope
                    samples[at++] = (value * Short.MAX_VALUE).toInt().toShort()
                }
                // Silence between beeps — zero is silence in signed PCM. Skipped
                // after the final beep.
                if (beep < BEEPS - 1) repeat(gapSamples) { samples[at++] = 0 }
            }

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        // USAGE_ALARM is the load-bearing choice here. It routes the
                        // sound to the alarm volume rather than the media volume, and
                        // lets it through Do Not Disturb — which is the difference
                        // between a timer that reliably tells you it finished and one
                        // that is silent whenever the phone is muted.
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                // * 2 because each sample is a 16-bit Short, so two bytes.
                .setBufferSizeInBytes(samples.size * 2)
                // MODE_STATIC hands the whole buffer over once and lets the platform
                // play it, instead of MODE_STREAM's requirement to keep feeding it.
                // Correct for a short fixed sound, and it means nothing has to stay
                // awake while it plays.
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(samples, 0, samples.size)
            track.play()

            // An AudioTrack holds a hardware resource, so it has to be released, but
            // releasing it before the sound finishes truncates it. There is no
            // completion callback for MODE_STATIC, so the duration is computed and a
            // 250 ms margin added to absorb start-up latency.
            val playMs = samples.size * 1000L / rate + 250L
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    track.stop()
                    track.release()
                } catch (_: IllegalStateException) {
                }
            }, playMs)
        } catch (_: Exception) {
            // Never let a silent device stop the timer from finishing.
        }
    }

    /**
     * Buzz in the same rhythm as the beeps, so the two read as one alert.
     *
     * Android 12 (API 31, "S") replaced the direct Vibrator service with
     * VibratorManager, which on a multi-actuator device can address each motor
     * separately; `defaultVibrator` asks for the one the user thinks of as "the"
     * vibration. The older call still works below 12 and is deprecated above it,
     * hence the version split and the suppression.
     *
     * A device with no actuator is not the null case: it still hands back a real
     * Vibrator, whose vibrate() is simply a no-op (hasVibrator() would report
     * false). So absent hardware needs no handling at all. The `?: return` is
     * defending against the service itself being unavailable, which is rare.
     */
    private fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return
        // Alternating wait/buzz in milliseconds, starting with a wait of 0: buzz 180,
        // pause 80, buzz 180, pause 80, buzz 180. The same 180/80 figures as BEEP_MS
        // and GAP_MS, so the buzzes land with the tones.
        val pattern = longArrayOf(0, 180, 80, 180, 80, 180)
        try {
            // -1 means play once without repeating. Anything >= 0 would loop from
            // that index in the pattern forever, which here would never stop.
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (_: Exception) {
            // Swallowed for the same reason as playBeeps: some OEM vibrators throw
            // on an unsupported pattern, and a failed buzz must not stop a session
            // from ending or prevent the notification being posted.
        }
    }
}
