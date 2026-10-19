package com.karthikhegde.meterpod.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat

/**
 * Foreground service behind the notification-bar timer and stopwatch. While either is running (or paused)
 * it shows a live notification (the system draws the ticking chronometer), keeps the process alive and
 * fires the timer at the right moment so the bell rings even if the AlarmManager alarm is late.
 * [TimeEngine] calls [sync] after every state change.
 */
class TimeService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private val fire = Runnable { TimeEngine.onTimerFired(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        refresh()
        return START_NOT_STICKY
    }

    private fun refresh() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)
        handler.removeCallbacks(fire)

        val ts = TimeEngine.timerState
        val timerShown = ts == TimerState.RUNNING || ts == TimerState.PAUSED
        val finished = ts == TimerState.FINISHED && TimeEngine.isAlerting
        val swShown = TimeEngine.swRunning || TimeEngine.swElapsed() > 0L

        if (!timerShown && !finished && !swShown) {
            stopForeground(true)
            nm.cancel(ID_TIMER); nm.cancel(ID_STOPWATCH)
            releaseWake()
            stopSelf()
            return
        }

        if (ts == TimerState.RUNNING) {
            val left = TimeEngine.timerRemaining()
            handler.postDelayed(fire, left.coerceAtLeast(0L))
            acquireWake(left + 5_000L)
        } else if (finished) acquireWake(125_000L) else releaseWake()

        val timerN = if (timerShown) buildTimer(ts == TimerState.RUNNING) else null
        val swN = if (swShown) buildStopwatch() else null
        if (!timerShown) nm.cancel(ID_TIMER)
        if (!swShown) nm.cancel(ID_STOPWATCH)

        // The foreground notification is whichever is most important; the other is posted beside it.
        val (fgId, fgN) = when {
            finished -> TimeEngine.FINISHED_ID to TimeEngine.buildFinished(this)
            timerN != null -> ID_TIMER to timerN
            else -> ID_STOPWATCH to swN!!
        }
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(fgId, fgN, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(fgId, fgN)
        } catch (_: Exception) { }
        if (timerN != null && fgId != ID_TIMER) try { nm.notify(ID_TIMER, timerN) } catch (_: Exception) { }
        if (swN != null && fgId != ID_STOPWATCH) try { nm.notify(ID_STOPWATCH, swN) } catch (_: Exception) { }
    }

    private fun builder(): Notification.Builder =
        if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL)
        else @Suppress("DEPRECATION") Notification.Builder(this)

    private fun openIntent(code: Int): PendingIntent? {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(this, code, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun buildTimer(running: Boolean): Notification {
        val left = TimeEngine.timerRemaining()
        val b = builder()
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(openIntent(7010))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (running) {
            b.setContentTitle("Timer running")
                .setContentText("Counting down")
                .setWhen(System.currentTimeMillis() + left)
                .setShowWhen(true)
                .setUsesChronometer(true)
            if (Build.VERSION.SDK_INT >= 24) b.setChronometerCountDown(true)
            b.addAction(0, "Pause", actionIntent(this, ACTION_T_PAUSE))
        } else {
            b.setContentTitle("Timer paused")
                .setContentText(fmt(left) + " left")
                .setShowWhen(false)
            b.addAction(0, "Resume", actionIntent(this, ACTION_T_RESUME))
        }
        b.addAction(0, "Reset", actionIntent(this, ACTION_T_RESET))
        return b.build()
    }

    private fun buildStopwatch(): Notification {
        val b = builder()
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentIntent(openIntent(7011))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (TimeEngine.swRunning) {
            b.setContentTitle("Stopwatch running")
                .setContentText("Tap to open")
                .setWhen(System.currentTimeMillis() - TimeEngine.swElapsed())
                .setShowWhen(true)
                .setUsesChronometer(true)
            b.addAction(0, "Lap", actionIntent(this, ACTION_SW_LAP))
            b.addAction(0, "Pause", actionIntent(this, ACTION_SW_TOGGLE))
        } else {
            b.setContentTitle("Stopwatch paused")
                .setContentText(fmt(TimeEngine.swElapsed()))
                .setShowWhen(false)
            b.addAction(0, "Resume", actionIntent(this, ACTION_SW_TOGGLE))
        }
        b.addAction(0, "Reset", actionIntent(this, ACTION_SW_RESET))
        return b.build()
    }

    private fun fmt(ms: Long): String {
        val t = (ms + 999) / 1000
        val h = t / 3600; val m = (t / 60) % 60; val s = t % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Timer and stopwatch", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
    }

    private fun acquireWake(ms: Long) {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MeterPod:time").apply { setReferenceCounted(false) }
            }
            wakeLock?.acquire(ms.coerceAtLeast(1_000L))
        } catch (_: Exception) { }
    }

    private fun releaseWake() {
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) { }
    }

    override fun onDestroy() {
        handler.removeCallbacks(fire)
        releaseWake()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "timer_live"
        const val ID_TIMER = 7010
        const val ID_STOPWATCH = 7011
        const val ACTION_T_PAUSE = "com.karthikhegde.meterpod.T_PAUSE"
        const val ACTION_T_RESUME = "com.karthikhegde.meterpod.T_RESUME"
        const val ACTION_T_RESET = "com.karthikhegde.meterpod.T_RESET"
        const val ACTION_SW_TOGGLE = "com.karthikhegde.meterpod.SW_TOGGLE"
        const val ACTION_SW_LAP = "com.karthikhegde.meterpod.SW_LAP"
        const val ACTION_SW_RESET = "com.karthikhegde.meterpod.SW_RESET"

        fun actionIntent(ctx: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
            ctx, action.hashCode(), Intent(ctx, TimerReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        private fun active(): Boolean {
            val ts = TimeEngine.timerState
            return TimeEngine.swRunning || TimeEngine.swElapsed() > 0L ||
                ts == TimerState.RUNNING || ts == TimerState.PAUSED || (ts == TimerState.FINISHED && TimeEngine.isAlerting)
        }

        /** Starts, refreshes or stops the notification service to match the engine's state. */
        fun sync(ctx: Context) {
            val app = ctx.applicationContext
            try {
                if (active()) {
                    ContextCompat.startForegroundService(app, Intent(app, TimeService::class.java))
                } else {
                    app.stopService(Intent(app, TimeService::class.java))
                    val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.cancel(ID_TIMER); nm.cancel(ID_STOPWATCH)
                }
            } catch (_: Exception) { }
        }
    }
}
