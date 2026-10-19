package com.karthikhegde.meterpod.timer

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class Lap(val number: Int, val lapMs: Long, val totalMs: Long)

enum class TimerState { IDLE, RUNNING, PAUSED, FINISHED }

/**
 * Process-wide state for the Time tool (stopwatch + countdown timer). It lives outside the composables so
 * the values keep running while other screens (About) are shown. The timer is backed by an
 * AlarmManager alarm clock so it still fires with the screen off.
 */
object TimeEngine {

    // ------------------------------------------------------------ stopwatch
    var swRunning by mutableStateOf(false)
        private set
    val laps = mutableStateListOf<Lap>()
    private var swAccum = 0L
    private var swStart = 0L
    private var lastLapTotal = 0L

    fun swElapsed(): Long = swAccum + if (swRunning) SystemClock.elapsedRealtime() - swStart else 0L

    fun swStartPause(ctx: Context) {
        if (swRunning) {
            swAccum += SystemClock.elapsedRealtime() - swStart
            swRunning = false
        } else {
            swStart = SystemClock.elapsedRealtime()
            swRunning = true
        }
        TimeService.sync(ctx)
    }

    fun swLap() {
        if (!swRunning) return
        val total = swElapsed()
        laps.add(0, Lap(laps.size + 1, total - lastLapTotal, total))
        lastLapTotal = total
    }

    fun swReset(ctx: Context) {
        swRunning = false
        swAccum = 0L; swStart = 0L; lastLapTotal = 0L
        laps.clear()
        TimeService.sync(ctx)
    }

    // ------------------------------------------------------------ timer
    var timerState by mutableStateOf(TimerState.IDLE)
        private set
    var setHours by mutableIntStateOf(0)
    var setMinutes by mutableIntStateOf(5)
    var setSeconds by mutableIntStateOf(0)
    var timerTotalMs by mutableStateOf(0L)
        private set
    private var endAtElapsed = 0L
    private var remainingAtPause = 0L

    fun configuredMs(): Long = ((setHours * 60L + setMinutes) * 60L + setSeconds) * 1000L

    fun timerRemaining(): Long = when (timerState) {
        TimerState.RUNNING -> (endAtElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        TimerState.PAUSED -> remainingAtPause
        TimerState.FINISHED -> 0L
        TimerState.IDLE -> configuredMs()
    }

    fun timerStart(ctx: Context) {
        val ms = configuredMs()
        if (ms <= 0L) return
        timerTotalMs = ms
        schedule(ctx, ms)
        timerState = TimerState.RUNNING
        TimeService.sync(ctx)
    }

    fun timerPause(ctx: Context) {
        if (timerState != TimerState.RUNNING) return
        remainingAtPause = timerRemaining()
        cancelAlarm(ctx)
        timerState = TimerState.PAUSED
        TimeService.sync(ctx)
    }

    fun timerResume(ctx: Context) {
        if (timerState != TimerState.PAUSED) return
        schedule(ctx, remainingAtPause)
        timerState = TimerState.RUNNING
        TimeService.sync(ctx)
    }

    /** Stops the countdown (or silences a finished alarm) and returns to the setup screen. */
    fun timerReset(ctx: Context) {
        cancelAlarm(ctx)
        stopAlert(ctx)
        timerState = TimerState.IDLE
        TimeService.sync(ctx)
    }

    private fun schedule(ctx: Context, ms: Long) {
        val app = ctx.applicationContext
        endAtElapsed = SystemClock.elapsedRealtime() + ms
        val am = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val fire = firePending(app)
        val launch = app.packageManager.getLaunchIntentForPackage(app.packageName)
        try {
            if (launch != null) {
                val open = PendingIntent.getActivity(app, 7002, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                am.setAlarmClock(AlarmManager.AlarmClockInfo(System.currentTimeMillis() + ms, open), fire)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endAtElapsed, fire)
            }
        } catch (e: SecurityException) {
            // Android 12+ may refuse exact alarms unless the user allowed them: fall back to an inexact alarm
            // (can be a little late) rather than crashing.
            try { am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endAtElapsed, fire) } catch (_: Exception) { }
        } catch (_: Exception) { }
    }

    private fun firePending(app: Context): PendingIntent = PendingIntent.getBroadcast(
        app, 7001, Intent(app, TimerReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun cancelAlarm(ctx: Context) {
        val app = ctx.applicationContext
        try { (app.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(firePending(app)) } catch (_: Exception) { }
    }

    /** True when there is something the person would lose by leaving (used for the back confirmation). */
    fun hasData(): Boolean = swElapsed() > 0L || laps.isNotEmpty() || timerState != TimerState.IDLE

    fun resetAll(ctx: Context) {
        swReset(ctx)
        timerReset(ctx)
    }

    // ------------------------------------------------------------ alert (sound + vibration + notification)
    const val ACTION_FIRE = "com.karthikhegde.meterpod.TIMER_FIRE"
    private const val CHANNEL = "timer_done"
    private const val NOTIF_ID = 7003
    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private var alerting = false
    val isAlerting: Boolean get() = alerting
    internal fun endAtElapsedMs(): Long = endAtElapsed

    fun onTimerFired(ctx: Context) {
        val app = ctx.applicationContext
        if (timerState != TimerState.RUNNING) return // already handled (service timer and alarm both fire)
        timerState = TimerState.FINISHED
        try { startAlert(app) } catch (_: Exception) { }
        try { postNotification(app) } catch (_: Exception) { }
        TimeService.sync(app)
    }

    private val loopRunnable = object : Runnable {
        override fun run() {
            if (!alerting) return
            ringtone?.let { if (!it.isPlaying) it.play() }
            handler.postDelayed(this, 1000L)
        }
    }

    private fun startAlert(app: Context) {
        if (alerting) return
        alerting = true
        // Bell first; if it cannot start, fall back to the phone's alarm ringtone.
        if (!BellSound.start(app)) {
            try {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ringtone = RingtoneManager.getRingtone(app, uri)?.apply {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                }
            } catch (_: Exception) { ringtone = null }
            handler.post(loopRunnable)
        }
        try {
            val v = app.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            val pattern = longArrayOf(0, 600, 400)
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pattern, 0))
            else @Suppress("DEPRECATION") v.vibrate(pattern, 0)
        } catch (_: Exception) { }
        handler.postDelayed({
            stopAlertSound(app)
            TimeService.sync(app)
            if (timerState == TimerState.FINISHED) try { postNotification(app) } catch (_: Exception) { }
        }, 120_000L) // never ring forever
    }

    private fun stopAlertSound(app: Context) {
        alerting = false
        handler.removeCallbacks(loopRunnable)
        BellSound.stop()
        try { ringtone?.stop() } catch (_: Exception) { }
        ringtone = null
        try { (app.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).cancel() } catch (_: Exception) { }
    }

    private fun stopAlert(ctx: Context) {
        val app = ctx.applicationContext
        stopAlertSound(app)
        try { (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIF_ID) } catch (_: Exception) { }
    }

    private fun postNotification(app: Context) {
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try { nm.notify(NOTIF_ID, buildFinished(app)) } catch (_: Exception) { }
    }

    const val FINISHED_ID = 7003
    internal fun buildFinished(app: Context): android.app.Notification {
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Timer", NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null) })
        }
        val launch = app.packageManager.getLaunchIntentForPackage(app.packageName)
        val open = if (launch != null) PendingIntent.getActivity(
            app, 7004, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        ) else null
        val b = if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(app, CHANNEL)
        else @Suppress("DEPRECATION") android.app.Notification.Builder(app)
        val n = b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Timer finished")
            .setContentText("Time's up - tap to open MeterPod")
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "Stop", TimeService.actionIntent(app, TimeService.ACTION_T_RESET))
            .build()
        return n
    }
}

/** Handles the timer alarm and the buttons on the timer / stopwatch notifications. */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TimeEngine.ACTION_FIRE -> TimeEngine.onTimerFired(context)
            TimeService.ACTION_T_PAUSE -> TimeEngine.timerPause(context)
            TimeService.ACTION_T_RESUME -> TimeEngine.timerResume(context)
            TimeService.ACTION_T_RESET -> TimeEngine.timerReset(context)
            TimeService.ACTION_SW_TOGGLE -> TimeEngine.swStartPause(context)
            TimeService.ACTION_SW_LAP -> TimeEngine.swLap()
            TimeService.ACTION_SW_RESET -> TimeEngine.swReset(context)
        }
    }
}
