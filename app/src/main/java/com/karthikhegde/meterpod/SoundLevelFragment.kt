package com.karthikhegde.meterpod

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.karthikhegde.meterpod.audio.AudioAnalyzer
import com.karthikhegde.meterpod.ui.SoundLevelView
import com.karthikhegde.meterpod.ui.WaveformView

/**
 * Ambient sound level meter: dB level, dominant frequency, and a live
 * waveform, all derived from [AudioAnalyzer] reading the microphone.
 *
 * The dB figure is a relative (uncalibrated) approximation from raw sample
 * amplitude, not a certified SPL reading — see AudioAnalyzer's doc comment
 * for why that's inherent to consumer phone mics, not just a shortcut here.
 */
class SoundLevelFragment : Fragment() {

    private lateinit var dbText: TextView
    private lateinit var descriptionText: TextView
    private lateinit var frequencyText: TextView
    private lateinit var peakText: TextView
    private lateinit var levelBar: SoundLevelView
    private lateinit var waveformView: WaveformView

    private var analyzer: AudioAnalyzer? = null

    private var smoothedDb = 0f
    private var hasFirstReading = false
    private val filterAlpha = 0.3f

    private var peakDb = 0f
    private val minDb = 0f
    private val maxDb = 100f // Used only to normalize the bar fill, not a hard cap on the reading.

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startAnalyzer()
        } else {
            descriptionText.text = "Microphone permission is required for this tab."
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_sound_level, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dbText = view.findViewById(R.id.dbText)
        descriptionText = view.findViewById(R.id.descriptionText)
        frequencyText = view.findViewById(R.id.frequencyText)
        peakText = view.findViewById(R.id.peakText)
        levelBar = view.findViewById(R.id.levelBar)
        waveformView = view.findViewById(R.id.waveformView)

        view.findViewById<Button>(R.id.resetPeakButton).setOnClickListener {
            peakDb = 0f
            peakText.text = "0 dB"
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasMicPermission()) {
            startAnalyzer()
        } else {
            descriptionText.text = "Tap to allow microphone access"
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onPause() {
        super.onPause()
        analyzer?.stop()
        analyzer = null
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startAnalyzer() {
        if (analyzer != null) return

        val newAnalyzer = AudioAnalyzer(onResult = ::onAudioResult)
        val started = newAnalyzer.start()
        if (started) {
            analyzer = newAnalyzer
        } else {
            descriptionText.text = "Couldn't start the microphone."
        }
    }

    private fun onAudioResult(result: AudioAnalyzer.Result) {
        val clampedDb = result.amplitudeDb.coerceIn(minDb, maxDb)

        smoothedDb = if (!hasFirstReading) {
            hasFirstReading = true
            clampedDb
        } else {
            filterAlpha * clampedDb + (1 - filterAlpha) * smoothedDb
        }

        if (smoothedDb > peakDb) {
            peakDb = smoothedDb
            peakText.text = String.format("%.0f dB", peakDb)
        }

        dbText.text = String.format("%.0f", smoothedDb)
        descriptionText.text = describeLevel(smoothedDb)

        frequencyText.text = result.dominantFrequencyHz?.let { freq ->
            String.format("Frequency: %.0f Hz", freq)
        } ?: "Frequency: —"

        val normalized = ((smoothedDb - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
        val normalizedPeak = ((peakDb - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
        levelBar.setLevel(normalized, normalizedPeak)

        waveformView.setSamples(result.waveform)
    }

    private fun describeLevel(db: Float): String = when {
        db < 30f -> "Quiet"
        db < 50f -> "Moderate"
        db < 70f -> "Noticeable"
        db < 85f -> "Loud"
        else -> "Very loud"
    }
}
