package com.karthikhegde.meterpod.timer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * A synthesised bell ("ding-ding ... ding-ding"), so the timer rings without any sound file and does not
 * depend on the phone's default ringtone. It plays on the alarm volume (falling back to the media volume
 * when the alarm volume is muted) and loops until [stop] is called.
 */
object BellSound {
    private const val SAMPLE_RATE = 44100
    private var track: AudioTrack? = null
    private var pcm: ShortArray? = null

    /** One bell strike: a fundamental plus inharmonic partials, each decaying at its own rate. */
    private fun strike(out: FloatArray, startSec: Double, gain: Float) {
        val f0 = 880.0
        val partials = arrayOf(
            doubleArrayOf(1.00, 1.00, 2.4), doubleArrayOf(2.00, 0.60, 3.0), doubleArrayOf(2.76, 0.50, 3.6),
            doubleArrayOf(4.07, 0.28, 4.6), doubleArrayOf(5.40, 0.22, 5.4), doubleArrayOf(8.93, 0.10, 7.5)
        )
        val start = (startSec * SAMPLE_RATE).toInt()
        val len = (2.0 * SAMPLE_RATE).toInt()
        for (i in 0 until len) {
            val idx = start + i
            if (idx >= out.size) break
            val t = i / SAMPLE_RATE.toDouble()
            val attack = if (t < 0.004) (t / 0.004) else 1.0
            var v = 0.0
            for (p in partials) v += p[1] * exp(-t * p[2]) * sin(2 * PI * f0 * p[0] * t)
            out[idx] += (v * attack * gain).toFloat()
        }
    }

    private fun build(): ShortArray {
        // 2.6 s loop: two strikes close together, then a pause to let the bell ring out.
        val total = (2.6 * SAMPLE_RATE).toInt()
        val f = FloatArray(total)
        strike(f, 0.0, 1.0f)
        strike(f, 0.55, 0.9f)
        var peak = 0f
        for (x in f) peak = maxOf(peak, abs(x))
        val k = if (peak > 0f) 0.85f / peak else 1f
        return ShortArray(total) { (f[it] * k * Short.MAX_VALUE).toInt().toShort() }
    }

    /** Starts the looping bell. @return false if the sound could not be started. */
    @Synchronized
    fun start(ctx: Context): Boolean {
        stop()
        return try {
            val data = pcm ?: build().also { pcm = it }
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            // A silent alarm stream is the usual reason a timer "makes no sound": lift it to a clearly audible level.
            try {
                val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                if (am.getStreamVolume(AudioManager.STREAM_ALARM) < max / 2) am.setStreamVolume(AudioManager.STREAM_ALARM, (max * 0.7f).toInt().coerceAtLeast(1), 0)
            } catch (_: Exception) { }
            @Suppress("DEPRECATION")
            try { am.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT) } catch (_: Exception) { }
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(data.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            if (t.state != AudioTrack.STATE_INITIALIZED) { t.release(); return false }
            val written = t.write(data, 0, data.size)
            if (written != data.size) { t.release(); return false }
            t.setLoopPoints(0, data.size, -1)
            t.setVolume(1.0f)
            t.play()
            track = t
            t.playState == AudioTrack.PLAYSTATE_PLAYING
        } catch (e: Exception) {
            track = null
            false
        }
    }

    @Synchronized
    fun stop() {
        try { track?.stop() } catch (_: Exception) { }
        try { track?.release() } catch (_: Exception) { }
        track = null
    }
}
