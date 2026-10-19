package com.karthikhegde.meterpod.morse

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

enum class MorseOutput { SOUND, VIBRATION, FLASH }

/** Plays a Morse timing pattern as a tone, as vibration, or by blinking the camera torch. One pattern at a time. */
class MorsePlayer(private val ctx: Context) {
    private var job: Job? = null
    private var track: AudioTrack? = null

    val isPlaying get() = job?.isActive == true

    fun hasFlash(): Boolean = torchId() != null
    fun hasVibrator(): Boolean = try { (ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).hasVibrator() } catch (_: Exception) { false }

    private fun torchId(): String? = try {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    } catch (_: Exception) { null }

    fun play(scope: CoroutineScope, output: MorseOutput, timings: LongArray, onDone: () -> Unit): Boolean {
        stop()
        if (timings.isEmpty()) return false
        when (output) {
            MorseOutput.FLASH -> if (torchId() == null) return false
            MorseOutput.VIBRATION -> if (!hasVibrator()) return false
            else -> {}
        }
        job = scope.launch {
            try {
                when (output) {
                    MorseOutput.SOUND -> playSound(timings)
                    MorseOutput.VIBRATION -> playVibration(timings)
                    MorseOutput.FLASH -> playFlash(timings)
                }
            } finally {
                cleanup()
                onDone()
            }
        }
        return true
    }

    fun stop() {
        job?.cancel(); job = null
        cleanup()
    }

    private fun cleanup() {
        try { track?.stop() } catch (_: Exception) { }
        try { track?.release() } catch (_: Exception) { }
        track = null
        try { (ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).cancel() } catch (_: Exception) { }
        try { torchId()?.let { (ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager).setTorchMode(it, false) } } catch (_: Exception) { }
    }

    private suspend fun playSound(t: LongArray) {
        val rate = 8000
        val total = t.sum()
        val samples = ShortArray((total * rate / 1000).toInt() + 1)
        var pos = 0L
        for ((i, d) in t.withIndex()) {
            val n = (d * rate / 1000).toInt()
            if (i % 2 == 0) {
                val ramp = (rate * 0.005).toInt().coerceAtLeast(1)
                for (k in 0 until n) {
                    val env = minOf(1f, k / ramp.toFloat(), (n - k) / ramp.toFloat())
                    samples[(pos + k).toInt()] = (sin(2 * PI * 650.0 * k / rate) * env * 0.8 * Short.MAX_VALUE).toInt().toShort()
                }
            }
            pos += n
        }
        withContext(Dispatchers.Default) {
            val tr = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track = tr
            tr.write(samples, 0, samples.size)
            tr.play()
        }
        delay(total + 300)
    }

    private suspend fun playVibration(t: LongArray) {
        // createWaveform wants off/on/off/on..., so prepend a 0 ms "off".
        val wave = LongArray(t.size + 1).also { System.arraycopy(t, 0, it, 1, t.size) }
        val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(wave, -1))
        else @Suppress("DEPRECATION") v.vibrate(wave, -1)
        delay(t.sum() + 200)
    }

    private suspend fun playFlash(t: LongArray) {
        val id = torchId() ?: return
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        for ((i, d) in t.withIndex()) {
            try { cm.setTorchMode(id, i % 2 == 0) } catch (_: Exception) { }
            delay(d)
        }
        try { cm.setTorchMode(id, false) } catch (_: Exception) { }
    }
}
