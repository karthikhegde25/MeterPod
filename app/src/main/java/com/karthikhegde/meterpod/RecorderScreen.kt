package com.karthikhegde.meterpod

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.karthikhegde.meterpod.compose.LeaveConfirmDialog
import com.karthikhegde.meterpod.compose.LocalOnBack
import com.karthikhegde.meterpod.compose.MP
import com.karthikhegde.meterpod.compose.RealButton
import com.karthikhegde.meterpod.record.RecState
import com.karthikhegde.meterpod.record.RecorderEngine
import kotlinx.coroutines.delay

private fun fmtRec(ms: Long): String {
    val s = (ms / 1000) % 60; val m = (ms / 60000) % 60; val h = ms / 3600000
    return String.format("%02d:%02d:%02d", h, m, s)
}

/**
 * Audio recorder: live waveform, elapsed time, pause / resume / stop, then Save to a place the person
 * picks. Built for long recordings (streams to disk, foreground service). Leaving while a recording is
 * running or unsaved asks for confirmation because the audio would be deleted.
 */
@Composable
fun RecorderScreen() {
    val ctx = LocalContext.current
    val onBack = LocalOnBack.current
    val handler = remember { Handler(Looper.getMainLooper()) }
    val state = RecorderEngine.state
    var showLeave by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()

    val needsConfirm = state == RecState.RECORDING || state == RecState.PAUSED ||
        (state == RecState.STOPPED && !RecorderEngine.saved)

    BackHandler(enabled = true) {
        if (needsConfirm) showLeave = true
        else { RecorderEngine.discard(ctx); onBack() }   // nothing to lose: just clean the temp file
    }

    if (showLeave) {
        LeaveConfirmDialog(
            title = "Leave Recorder?",
            message = if (state == RecState.STOPPED)
                "This recording has not been saved. If you leave, the audio will be deleted."
            else "A recording is in progress (${fmtRec(RecorderEngine.elapsedMs())}). If you leave, it will stop and the audio will be deleted.",
            confirmText = "Delete and leave",
            cancelText = if (state == RecState.STOPPED) "Stay" else "Keep recording",
            onConfirm = { showLeave = false; RecorderEngine.discard(ctx); onBack() },
            onDismiss = { showLeave = false }
        )
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.RECORD_AUDIO] == true) RecorderEngine.start(ctx)
        else RecorderEngine.error = "Microphone permission is needed to record."
    }

    fun startRecording() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            RecorderEngine.start(ctx)
        } else {
            val perms = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
            else arrayOf(Manifest.permission.RECORD_AUDIO)
            permLauncher.launch(perms)
        }
    }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uri = r.data?.data
        val src = RecorderEngine.file
        if (r.resultCode != Activity.RESULT_OK || uri == null || src == null) return@rememberLauncherForActivityResult
        busy = true
        Thread {
            var err: String? = null
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    src.inputStream().use { it.copyTo(out, 64 * 1024) }
                } ?: throw java.io.IOException("Could not open the destination")
            } catch (e: Exception) { err = e.message ?: "unknown error" }
            handler.post {
                busy = false
                if (err == null) { RecorderEngine.saved = true; toast("Saved") } else toast("Could not save: $err")
            }
        }.start()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MP.Background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BasicText(
            text = when (state) {
                RecState.IDLE -> "Ready to record"
                RecState.RECORDING -> "● Recording"
                RecState.PAUSED -> "Paused"
                RecState.STOPPED -> if (RecorderEngine.saved) "Saved" else "Recording stopped - not saved yet"
            },
            modifier = Modifier.padding(top = 6.dp),
            style = TextStyle(
                color = if (state == RecState.RECORDING) MP.Needle else MP.Accent,
                fontSize = 15.sp, fontWeight = FontWeight.Bold
            )
        )
        RecTime()
        RecWave(Modifier.fillMaxWidth().height(130.dp).padding(top = 14.dp))

        Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when (state) {
                RecState.IDLE -> RealButton("Record", onClick = { startRecording() }, primary = true)
                RecState.RECORDING -> {
                    RealButton("Pause", onClick = { RecorderEngine.pause() })
                    RealButton("Stop", onClick = { RecorderEngine.stop(ctx) }, primary = true)
                }
                RecState.PAUSED -> {
                    RealButton("Resume", onClick = { RecorderEngine.resume() }, primary = true)
                    RealButton("Stop", onClick = { RecorderEngine.stop(ctx) })
                }
                RecState.STOPPED -> {
                    RealButton(if (RecorderEngine.playing) "Stop playback" else "Play", onClick = { RecorderEngine.togglePlayback() })
                    RealButton(
                        if (busy) "Saving…" else if (RecorderEngine.saved) "Save again" else "Save…",
                        onClick = {
                            saver.launch(
                                Intent(Intent.ACTION_CREATE_DOCUMENT)
                                    .addCategory(Intent.CATEGORY_OPENABLE)
                                    .setType("audio/mp4")
                                    .putExtra(Intent.EXTRA_TITLE, RecorderEngine.suggestedName())
                            )
                        },
                        primary = true, enabled = !busy
                    )
                }
            }
        }
        if (state == RecState.STOPPED) {
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RealButton("Discard", onClick = { RecorderEngine.discard(ctx) }, compact = true, textColor = MP.Needle)
                RealButton("New recording", onClick = { RecorderEngine.discard(ctx); startRecording() }, compact = true)
            }
        }

        RecorderEngine.error?.let {
            BasicText(it, modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                style = TextStyle(color = MP.Needle, fontSize = 13.sp, textAlign = TextAlign.Center))
        }
        BasicText(
            text = "Records AAC audio (.m4a, about 1 MB per minute) straight to storage, so long recordings are fine, " +
                "and keeps going with the screen off. Press Stop, then Save to choose where the file goes.",
            modifier = Modifier.fillMaxWidth().padding(top = 26.dp),
            style = TextStyle(color = MP.TextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
        )
    }
}

@Composable
private fun RecTime() {
    val tick = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { tick.longValue = SystemClock.elapsedRealtime(); delay(100) } }
    tick.longValue
    BasicText(
        fmtRec(RecorderEngine.elapsedMs()),
        modifier = Modifier.padding(top = 10.dp),
        style = TextStyle(color = MP.TextPrimary, fontSize = 56.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    )
}

/** Scrolling loudness bars, newest on the right (like a voice-memo app). */
@Composable
private fun RecWave(modifier: Modifier) {
    val wave = RecorderEngine.wave
    val active = RecorderEngine.state == RecState.RECORDING
    Canvas(modifier.background(MP.Surface, RoundedCornerShape(10.dp))) {
        wave.version // redraw on every new sample
        val n = wave.capacity
        val slot = size.width / n
        val barW = (slot * 0.62f).coerceAtLeast(1.5f)
        val mid = size.height / 2f
        drawLine(MP.SurfaceOutline, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.dp.toPx())
        val count = wave.count
        for (i in 0 until count) {
            val a = wave[i]
            val h = (a * size.height * 0.92f).coerceAtLeast(3.dp.toPx())
            val x = (n - count + i) * slot + (slot - barW) / 2f
            val newest = i >= count - 3
            drawRoundRect(
                if (active && newest) MP.Needle else MP.Accent,
                Offset(x, mid - h / 2f), Size(barW, h), CornerRadius(barW / 2f)
            )
        }
    }
}
