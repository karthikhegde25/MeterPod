package com.karthikhegde.meterpod.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Captures raw microphone audio on a background thread and, for each
 * buffer, derives three things a level meter cares about:
 *
 *  - amplitudeDb: a relative (uncalibrated) loudness figure from the raw
 *    peak sample amplitude.
 *  - dominantFrequencyHz: the strongest frequency component, found via a
 *    real-input FFT over a Hann-windowed buffer, or null if nothing stands
 *    out clearly above the noise floor (silence/broadband noise has no
 *    single dominant tone).
 *  - waveform: the raw samples (normalized to -1..1) for drawing an
 *    oscilloscope-style graph.
 *
 * Results are throttled and delivered on the main thread via the provided
 * callback, so callers can update views directly without extra dispatching.
 */
class AudioAnalyzer(
    private val sampleRate: Int = 44100,
    private val fftSize: Int = 1024,
    private val onResult: (Result) -> Unit
) {
    data class Result(
        val amplitudeDb: Float,
        val dominantFrequencyHz: Float?,
        val waveform: FloatArray
    )

    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null

    @Volatile
    private var running = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastUiPostTimeMs = 0L
    private val uiPostIntervalMs = 66L // ~15 fps is plenty for a meter/waveform display.

    /** @return true if the microphone was successfully opened and capture started. */
    fun start(): Boolean {
        if (running) return true

        val minBufferBytes = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferBytes == AudioRecord.ERROR || minBufferBytes == AudioRecord.ERROR_BAD_VALUE) {
            return false
        }
        val bufferBytes = maxOf(minBufferBytes, fftSize * 2 * 2)

        return try {
            @Suppress("MissingPermission") // Caller is required to have checked RECORD_AUDIO first.
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferBytes
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return false
            }

            audioRecord = record
            running = true
            record.startRecording()

            captureThread = Thread(::captureLoop, "MeterPod-AudioAnalyzer").apply { start() }
            true
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    fun stop() {
        running = false
        try {
            captureThread?.join(300)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        captureThread = null

        audioRecord?.let {
            try {
                it.stop()
            } catch (e: Exception) {
                // Can throw if stop() races start(); safe to ignore, we're releasing anyway.
            }
            it.release()
        }
        audioRecord = null
    }

    private fun captureLoop() {
        val record = audioRecord ?: return

        val shortBuffer = ShortArray(fftSize)
        val re = DoubleArray(fftSize)
        val im = DoubleArray(fftSize)
        val hannWindow = DoubleArray(fftSize) { i ->
            0.5 * (1.0 - cos(2.0 * Math.PI * i / (fftSize - 1)))
        }

        // Ignore FFT bins below ~50 Hz (rumble/DC leakage isn't a meaningful "tone").
        val minBin = maxOf(1, (50.0 * fftSize / sampleRate).toInt())
        val maxBin = fftSize / 2

        while (running) {
            val samplesRead = record.read(shortBuffer, 0, fftSize)
            if (samplesRead <= 0) continue

            var peakAbs = 0
            for (i in 0 until samplesRead) {
                val sample = shortBuffer[i].toInt()
                val absSample = abs(sample)
                if (absSample > peakAbs) peakAbs = absSample
                re[i] = shortBuffer[i] * hannWindow[i]
                im[i] = 0.0
            }
            for (i in samplesRead until fftSize) {
                re[i] = 0.0
                im[i] = 0.0
            }

            val amplitudeDb = if (peakAbs > 0) (20.0 * log10(peakAbs.toDouble())).toFloat() else 0f

            fft(re, im)

            var maxMagnitude = -1.0
            var maxBinIndex = -1
            var magnitudeSum = 0.0
            for (bin in minBin until maxBin) {
                val magnitude = sqrt(re[bin] * re[bin] + im[bin] * im[bin])
                magnitudeSum += magnitude
                if (magnitude > maxMagnitude) {
                    maxMagnitude = magnitude
                    maxBinIndex = bin
                }
            }
            val averageMagnitude = if (maxBin > minBin) magnitudeSum / (maxBin - minBin) else 0.0

            // Only report a frequency when one bin clearly stands out above the average
            // (a real tone) and the signal isn't just quiet noise.
            val dominantFrequencyHz: Float? =
                if (maxBinIndex >= 0 && maxMagnitude > averageMagnitude * 3.0 && amplitudeDb > 25f) {
                    (maxBinIndex.toDouble() * sampleRate / fftSize).toFloat()
                } else {
                    null
                }

            val waveform = FloatArray(samplesRead) { i -> shortBuffer[i] / 32768f }

            val now = System.currentTimeMillis()
            if (now - lastUiPostTimeMs >= uiPostIntervalMs) {
                lastUiPostTimeMs = now
                val result = Result(amplitudeDb, dominantFrequencyHz, waveform)
                mainHandler.post { onResult(result) }
            }
        }
    }

    /**
     * Iterative radix-2 Cooley-Tukey FFT (decimation in time), computed in place.
     * `re`/`im` must have a power-of-two length.
     */
    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        if (n <= 1) return

        // Bit-reversal permutation.
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tempRe = re[i]; re[i] = re[j]; re[j] = tempRe
                val tempIm = im[i]; im[i] = im[j]; im[j] = tempIm
            }
        }

        var length = 2
        while (length <= n) {
            val angle = -2.0 * Math.PI / length
            val wReal = cos(angle)
            val wImag = kotlin.math.sin(angle)
            var i = 0
            while (i < n) {
                var curReal = 1.0
                var curImag = 0.0
                for (k in 0 until length / 2) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + length / 2] * curReal - im[i + k + length / 2] * curImag
                    val vIm = re[i + k + length / 2] * curImag + im[i + k + length / 2] * curReal

                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + length / 2] = uRe - vRe
                    im[i + k + length / 2] = uIm - vIm

                    val nextCurReal = curReal * wReal - curImag * wImag
                    val nextCurImag = curReal * wImag + curImag * wReal
                    curReal = nextCurReal
                    curImag = nextCurImag
                }
                i += length
            }
            length = length shl 1
        }
    }
}
