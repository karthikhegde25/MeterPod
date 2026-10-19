package com.karthikhegde.meterpod

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.karthikhegde.meterpod.compose.HairlineDivider
import com.karthikhegde.meterpod.compose.LeaveConfirmDialog
import com.karthikhegde.meterpod.compose.LocalOnBack
import com.karthikhegde.meterpod.compose.MP
import com.karthikhegde.meterpod.compose.RealButton
import com.karthikhegde.meterpod.timer.TimeEngine
import com.karthikhegde.meterpod.timer.TimerState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.sin

private val Mono = FontFamily.Monospace

/** A clock that ticks every [periodMs]; each display composable reads it so only that display redraws. */
@Composable
private fun rememberTicker(periodMs: Long): androidx.compose.runtime.State<Long> {
    val s = remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(periodMs) {
        while (true) {
            s.longValue = SystemClock.elapsedRealtime()
            delay(periodMs)
        }
    }
    return s
}

private fun fmtStopwatch(ms: Long): String {
    val cs = (ms / 10) % 100
    val s = (ms / 1000) % 60
    val m = (ms / 60000) % 60
    val h = ms / 3600000
    return if (h > 0) String.format("%d:%02d:%02d.%02d", h, m, s, cs) else String.format("%02d:%02d.%02d", m, s, cs)
}

private fun fmtLap(ms: Long) = fmtStopwatch(ms)

private fun fmtCountdown(ms: Long): String {
    val total = (ms + 999) / 1000
    val h = total / 3600; val m = (total / 60) % 60; val s = total % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}

/**
 * Time tool: a clock (analog + digital), a countdown timer and a stopwatch with laps. State lives in
 * [TimeEngine] so it keeps running if About is opened; leaving the tool asks for confirmation because it
 * resets everything.
 */
@Composable
fun TimeScreen() {
    val ctx = LocalContext.current
    val onBack = LocalOnBack.current
    var tab by remember { mutableStateOf(0) }
    var showLeave by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        if (TimeEngine.hasData()) showLeave = true else onBack()
    }

    // Keep the screen awake while something is counting, handy on a pitch or track.
    val view = LocalView.current
    val keepOn = TimeEngine.swRunning || TimeEngine.timerState == TimerState.RUNNING
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    if (showLeave) {
        LeaveConfirmDialog(
            title = "Leave Time?",
            message = "Your stopwatch time, laps and timer will be reset. This data will be lost.",
            confirmText = "Leave and reset",
            onConfirm = { showLeave = false; TimeEngine.resetAll(ctx); onBack() },
            onDismiss = { showLeave = false }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MP.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            RealButton("Clock", onClick = { tab = 0 }, primary = tab == 0, compact = true)
            RealButton("Timer", onClick = { tab = 1 }, primary = tab == 1, compact = true)
            RealButton("Stopwatch", onClick = { tab = 2 }, primary = tab == 2, compact = true)
        }
        when (tab) {
            0 -> ClockTab()
            1 -> TimerTab()
            else -> StopwatchTab()
        }
    }
}

// ============================================================ clock

@Composable
private fun ClockTab() {
    val ctx = LocalContext.current
    var use24 by remember { mutableStateOf(DateFormat.is24HourFormat(ctx)) }
    Column(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ClockFace(Modifier.size(250.dp))
        ClockText(use24)
        RealButton(
            text = if (use24) "Switch to 12-hour" else "Switch to 24-hour",
            onClick = { use24 = !use24 },
            compact = true,
            modifier = Modifier.padding(top = 18.dp)
        )
    }
}

@Composable
private fun ClockText(use24: Boolean) {
    val tick by rememberTicker(200)
    val now = remember(tick / 200) { Date() }
    val tz = TimeZone.getDefault()
    val time = SimpleDateFormat(if (use24) "HH:mm:ss" else "hh:mm:ss a", Locale.getDefault()).format(now)
    val date = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(now)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(
            time,
            modifier = Modifier.padding(top = 18.dp),
            style = TextStyle(color = MP.TextPrimary, fontSize = 40.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
        )
        BasicText(date, modifier = Modifier.padding(top = 4.dp), style = TextStyle(color = MP.Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold))
        BasicText(
            tz.getDisplayName(tz.inDaylightTime(now), TimeZone.LONG) + "  (" + tz.id + ")",
            modifier = Modifier.padding(top = 2.dp),
            style = TextStyle(color = MP.TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
        )
    }
}

@Composable
private fun ClockFace(modifier: Modifier) {
    val tick by rememberTicker(100)
    Canvas(modifier) {
        tick // redraw on every tick
        val cal = Calendar.getInstance()
        val ms = cal.get(Calendar.MILLISECOND)
        val sec = cal.get(Calendar.SECOND) + ms / 1000f
        val min = cal.get(Calendar.MINUTE) + sec / 60f
        val hr = (cal.get(Calendar.HOUR) % 12) + min / 60f
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f - 4.dp.toPx()
        drawCircle(MP.SurfaceElevated, r, c)
        drawCircle(MP.SurfaceOutline, r, c, style = Stroke(3.dp.toPx()))
        for (i in 0 until 60) {
            val a = Math.toRadians(i * 6.0 - 90.0)
            val big = i % 5 == 0
            val r1 = r - (if (big) 14.dp.toPx() else 8.dp.toPx())
            val r2 = r - 3.dp.toPx()
            drawLine(
                if (big) MP.TextPrimary else MP.TextSecondary,
                Offset(c.x + (cos(a) * r1).toFloat(), c.y + (sin(a) * r1).toFloat()),
                Offset(c.x + (cos(a) * r2).toFloat(), c.y + (sin(a) * r2).toFloat()),
                strokeWidth = if (big) 3.dp.toPx() else 1.dp.toPx(), cap = StrokeCap.Round
            )
        }
        fun hand(angleDeg: Float, len: Float, w: Float, color: Color) {
            val a = Math.toRadians(angleDeg - 90.0)
            drawLine(color, c, Offset(c.x + (cos(a) * len).toFloat(), c.y + (sin(a) * len).toFloat()), strokeWidth = w, cap = StrokeCap.Round)
        }
        hand(hr * 30f, r * 0.50f, 6.dp.toPx(), MP.TextPrimary)
        hand(min * 6f, r * 0.75f, 4.dp.toPx(), MP.TextPrimary)
        hand(sec * 6f, r * 0.85f, 2.dp.toPx(), MP.Needle)
        drawCircle(MP.Needle, 5.dp.toPx(), c)
    }
}

// ============================================================ stopwatch

@Composable
private fun StopwatchTab() {
    val ctx = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val running = TimeEngine.swRunning
    val laps = TimeEngine.laps
    Column(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        StopwatchDisplay()
        Row(
            Modifier.padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RealButton("Lap", onClick = { TimeEngine.swLap() }, enabled = running)
            RealButton(
                if (running) "Pause" else if (TimeEngine.swElapsed() > 0) "Resume" else "Start",
                onClick = {
                    if (!running && Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    TimeEngine.swStartPause(ctx)
                }, primary = true
            )
            RealButton("Reset", onClick = { TimeEngine.swReset(ctx) }, textColor = MP.Needle, enabled = running || TimeEngine.swElapsed() > 0 || laps.isNotEmpty())
        }
        if (laps.isNotEmpty()) {
            val best = laps.minOf { it.lapMs }
            val worst = laps.maxOf { it.lapMs }
            Column(Modifier.fillMaxWidth().padding(top = 22.dp)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    LapCell("Lap", Modifier.weight(0.6f), MP.TextSecondary, 12)
                    LapCell("Lap time", Modifier.weight(1.2f), MP.TextSecondary, 12)
                    LapCell("Total", Modifier.weight(1.2f), MP.TextSecondary, 12)
                }
                HairlineDivider()
                laps.forEach { lap ->
                    val tint = when {
                        laps.size >= 3 && lap.lapMs == best -> Color(0xFF4CAF50)
                        laps.size >= 3 && lap.lapMs == worst -> MP.Needle
                        else -> MP.TextPrimary
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        LapCell("${lap.number}", Modifier.weight(0.6f), MP.TextSecondary, 15)
                        LapCell(fmtLap(lap.lapMs), Modifier.weight(1.2f), tint, 15)
                        LapCell(fmtLap(lap.totalMs), Modifier.weight(1.2f), MP.TextSecondary, 15)
                    }
                    HairlineDivider()
                }
            }
        }
    }
}

@Composable
private fun LapCell(text: String, modifier: Modifier, color: Color, size: Int) {
    BasicText(text, modifier = modifier, style = TextStyle(color = color, fontSize = size.sp, fontFamily = Mono))
}

@Composable
private fun StopwatchDisplay() {
    val tick by rememberTicker(30)
    tick
    BasicText(
        fmtStopwatch(TimeEngine.swElapsed()),
        style = TextStyle(color = MP.TextPrimary, fontSize = 54.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
    )
}

// ============================================================ timer

@Composable
private fun TimerTab() {
    val ctx = LocalContext.current
    val state = TimeEngine.timerState
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun startTimer() {
        if (TimeEngine.configuredMs() <= 0L) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        TimeEngine.timerStart(ctx)
    }

    Column(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        when (state) {
            TimerState.IDLE -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stepper("hours", TimeEngine.setHours, 99) { TimeEngine.setHours = it }
                    Stepper("min", TimeEngine.setMinutes, 59) { TimeEngine.setMinutes = it }
                    Stepper("sec", TimeEngine.setSeconds, 59) { TimeEngine.setSeconds = it }
                }
                Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "1 min", 5 to "5 min", 10 to "10 min", 30 to "30 min").forEach { (m, label) ->
                        RealButton(label, onClick = { TimeEngine.setHours = 0; TimeEngine.setMinutes = m; TimeEngine.setSeconds = 0 }, compact = true)
                    }
                }
                RealButton(
                    "Start", onClick = { startTimer() }, primary = true,
                    enabled = TimeEngine.configuredMs() > 0L, modifier = Modifier.padding(top = 26.dp)
                )
            }
            TimerState.RUNNING, TimerState.PAUSED -> {
                TimerRing(Modifier.size(260.dp))
                Row(Modifier.padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state == TimerState.RUNNING) RealButton("Pause", onClick = { TimeEngine.timerPause(ctx) }, primary = true)
                    else RealButton("Resume", onClick = { TimeEngine.timerResume(ctx) }, primary = true)
                    RealButton("Reset", onClick = { TimeEngine.timerReset(ctx) }, textColor = MP.Needle)
                }
            }
            TimerState.FINISHED -> {
                BasicText("Time's up!", modifier = Modifier.padding(top = 40.dp),
                    style = TextStyle(color = MP.Needle, fontSize = 40.sp, fontWeight = FontWeight.Bold))
                BasicText("The alarm is sounding", modifier = Modifier.padding(top = 6.dp),
                    style = TextStyle(color = MP.TextSecondary, fontSize = 14.sp))
                RealButton("Stop alarm", onClick = { TimeEngine.timerReset(ctx) }, primary = true, modifier = Modifier.padding(top = 28.dp))
            }
        }
        BasicText(
            text = "The timer and stopwatch also show in the notification bar and keep running with the screen off. The timer rings a bell when it finishes.",
            modifier = Modifier.fillMaxWidth().padding(top = 26.dp),
            style = TextStyle(color = MP.TextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
        )
    }
}

@Composable
private fun TimerRing(modifier: Modifier) {
    val tick by rememberTicker(50)
    val paused = TimeEngine.timerState == TimerState.PAUSED
    Box(modifier, contentAlignment = Alignment.Center) {
        tick
        val remaining = TimeEngine.timerRemaining()
        val total = TimeEngine.timerTotalMs.coerceAtLeast(1L)
        val frac = (remaining.toFloat() / total).coerceIn(0f, 1f)
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2f
            val sz = Size(size.width - stroke, size.height - stroke)
            drawArc(MP.SurfaceOutline, 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(stroke))
            drawArc(if (paused) MP.TextSecondary else MP.Accent, -90f, 360f * frac, false, Offset(inset, inset), sz, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(fmtCountdown(remaining), style = TextStyle(color = MP.TextPrimary, fontSize = 48.sp, fontWeight = FontWeight.Bold, fontFamily = Mono))
            if (paused) BasicText("Paused", style = TextStyle(color = MP.TextSecondary, fontSize = 14.sp))
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, max: Int, onChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RealButton("+", onClick = { onChange(if (value >= max) 0 else value + 1) }, compact = true)
        BasicText(
            String.format("%02d", value),
            modifier = Modifier.padding(vertical = 10.dp),
            style = TextStyle(color = MP.TextPrimary, fontSize = 44.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
        )
        RealButton("−", onClick = { onChange(if (value <= 0) max else value - 1) }, compact = true)
        BasicText(label, modifier = Modifier.padding(top = 6.dp), style = TextStyle(color = MP.TextSecondary, fontSize = 12.sp))
    }
}
