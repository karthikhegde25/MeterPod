package com.karthikhegde.meterpod.record

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.karthikhegde.meterpod.compose.SeriesBuffer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class RecState { IDLE, RECORDING, PAUSED, STOPPED }

/**
 * Process-wide audio recorder. Audio goes straight to a temporary AAC (.m4a) file on disk through
 * MediaRecorder, so memory use stays flat however long the recording runs (about 1 MB per minute). A
 * foreground service ([RecorderService]) keeps it alive with the screen off or the app in the background.
 * The file is only copied to the destination the person picks when they choose Save.
 */
object RecorderEngine {
    var state by mutableStateOf(RecState.IDLE)
        private set
    var error by mutableStateOf<String?>(null)
    var saved by mutableStateOf(false)
    var playing by mutableStateOf(false)
        private set

    /** Recent loudness (0..1) for the live waveform. */
    val wave = SeriesBuffer(90)

    var file: File? = null
        private set

    private var recorder: MediaRecorder? = null
    private var accumulated = 0L
    private var segmentStart = 0L
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    fun elapsedMs(): Long = accumulated + if (state == RecState.RECORDING) SystemClock.elapsedRealtime() - segmentStart else 0L

    fun suggestedName(): String =
        "Recording " + SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.US).format(Date()) + ".m4a"

    /** Caller must already hold RECORD_AUDIO. */
    fun start(ctx: Context): Boolean {
        val app = ctx.applicationContext
        if (state == RecState.RECORDING || state == RecState.PAUSED) return true
        cleanupFile()
        error = null; saved = false; accumulated = 0L; wave.clear()
        val dir = File(app.filesDir, "recordings").apply { mkdirs() }
        val f = File(dir, "rec_${System.currentTimeMillis()}.m4a")
        try {
            ContextCompat.startForegroundService(app, Intent(app, RecorderService::class.java))
            @Suppress("DEPRECATION")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(app) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(44100)
            r.setAudioEncodingBitRate(128_000)
            r.setOutputFile(f.absolutePath)
            r.setOnErrorListener { _, what, _ -> fail(app, "Recording stopped unexpectedly (code $what)") }
            r.prepare()
            r.start()
            recorder = r
            file = f
            segmentStart = SystemClock.elapsedRealtime()
            state = RecState.RECORDING
            handler.post(sampler)
            return true
        } catch (e: Exception) {
            error = "Could not start recording: ${e.message}"
            releaseRecorder()
            f.delete()
            app.stopService(Intent(app, RecorderService::class.java))
            return false
        }
    }

    fun pause() {
        if (state != RecState.RECORDING) return
        try {
            recorder?.pause()
            accumulated += SystemClock.elapsedRealtime() - segmentStart
            state = RecState.PAUSED
        } catch (e: Exception) { error = "Could not pause: ${e.message}" }
    }

    fun resume() {
        if (state != RecState.PAUSED) return
        try {
            recorder?.resume()
            segmentStart = SystemClock.elapsedRealtime()
            state = RecState.RECORDING
            handler.post(sampler)
        } catch (e: Exception) { error = "Could not resume: ${e.message}" }
    }

    fun stop(ctx: Context) {
        if (state != RecState.RECORDING && state != RecState.PAUSED) return
        val app = ctx.applicationContext
        if (state == RecState.RECORDING) accumulated += SystemClock.elapsedRealtime() - segmentStart
        handler.removeCallbacks(sampler)
        try {
            recorder?.stop()
            state = RecState.STOPPED
        } catch (e: RuntimeException) {
            error = "Nothing was recorded"
            file?.delete(); file = null
            state = RecState.IDLE
        }
        releaseRecorder()
        app.stopService(Intent(app, RecorderService::class.java))
    }

    /** Throws away the current recording (running, paused or finished) and returns to idle. */
    fun discard(ctx: Context) {
        val app = ctx.applicationContext
        handler.removeCallbacks(sampler)
        stopPlayback()
        try { recorder?.stop() } catch (_: Exception) { }
        releaseRecorder()
        app.stopService(Intent(app, RecorderService::class.java))
        cleanupFile()
        accumulated = 0L; saved = false; error = null; wave.clear()
        state = RecState.IDLE
    }

    private fun fail(app: Context, msg: String) {
        handler.removeCallbacks(sampler)
        releaseRecorder()
        app.stopService(Intent(app, RecorderService::class.java))
        error = msg
        state = if (file?.exists() == true) RecState.STOPPED else RecState.IDLE
    }

    private fun releaseRecorder() {
        try { recorder?.release() } catch (_: Exception) { }
        recorder = null
    }

    private fun cleanupFile() {
        file?.delete(); file = null
    }

    private val sampler = object : Runnable {
        override fun run() {
            if (state != RecState.RECORDING) return
            val amp = try { recorder?.maxAmplitude ?: 0 } catch (_: Exception) { 0 }
            wave.add(Math.sqrt((amp / 32767.0).coerceIn(0.0, 1.0)).toFloat())
            handler.postDelayed(this, 60L)
        }
    }

    // ------------------------------------------------------------ preview playback
    fun togglePlayback() {
        if (playing) { stopPlayback(); return }
        val f = file ?: return
        try {
            val p = MediaPlayer()
            p.setDataSource(f.absolutePath)
            p.setOnCompletionListener { stopPlayback() }
            p.prepare()
            p.start()
            player = p
            playing = true
        } catch (e: Exception) {
            error = "Could not play the recording: ${e.message}"
            stopPlayback()
        }
    }

    fun stopPlayback() {
        try { player?.release() } catch (_: Exception) { }
        player = null
        playing = false
    }
}
