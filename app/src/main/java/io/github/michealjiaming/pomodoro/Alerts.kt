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

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * An ongoing notification that counts down on its own — the platform ticks
     * the chronometer, so no service has to stay awake to update it. This is the
     * counterpart of the remaining time in the desktop title bar.
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

    fun cancelRunning(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ID_RUNNING)
    }

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

    fun cancelFinished(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ID_FINISHED)
    }

    private fun notify(context: Context, id: Int, notification: Notification) {
        try {
            context.getSystemService(NotificationManager::class.java)?.notify(id, notification)
        } catch (_: SecurityException) {
            // Notification permission was refused; the in-app UI still works.
        }
    }

    fun alert(context: Context) {
        playBeeps()
        vibrate(context)
    }

    private fun playBeeps() {
        try {
            val rate = 44_100
            val beepSamples = rate * BEEP_MS / 1000
            val gapSamples = rate * GAP_MS / 1000
            val samples = ShortArray(BEEPS * beepSamples + (BEEPS - 1) * gapSamples)
            // A few milliseconds of fade at each end; a square-edged tone clicks.
            val fade = rate * 0.006f
            var at = 0
            repeat(BEEPS) { beep ->
                for (n in 0 until beepSamples) {
                    val envelope = minOf(1f, minOf(n, beepSamples - n).toFloat() / fade)
                    val value = sin(2.0 * PI * TONE_HZ * n / rate) * 0.6 * envelope
                    samples[at++] = (value * Short.MAX_VALUE).toInt().toShort()
                }
                if (beep < BEEPS - 1) repeat(gapSamples) { samples[at++] = 0 }
            }

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
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
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(samples, 0, samples.size)
            track.play()

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

    private fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return
        val pattern = longArrayOf(0, 180, 80, 180, 80, 180)   // matches the beeps
        try {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (_: Exception) {
        }
    }
}
