package com.karthikhegde.meterpod

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.karthikhegde.meterpod.audio.AudioAnalyzer
import com.karthikhegde.meterpod.compose.ChartLine
import com.karthikhegde.meterpod.compose.LineChart
import com.karthikhegde.meterpod.compose.MP
import com.karthikhegde.meterpod.compose.SeriesBuffer
import com.karthikhegde.meterpod.compose.RealButton
import com.karthikhegde.meterpod.compose.ResumePauseEffect
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
private class SoundLevelController(private val context: Context) {

    var dbText by mutableStateOf("0")
    var descriptionText by mutableStateOf("Quiet")
    var frequencyText by mutableStateOf("Frequency: —")
    var peakText by mutableStateOf("0 dB")

    val history = SeriesBuffer(150)
    var levelBar: SoundLevelView? = null
    var waveformView: WaveformView? = null

    private var analyzer: AudioAnalyzer? = null

    private var smoothedDb = 0f
    private var hasFirstReading = false
    private val filterAlpha = 0.3f

    private var peakDb = 0f
    private val minDb = 0f
    private val maxDb = 100f // Used only to normalize the bar fill, not a hard cap on the reading.

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            startAnalyzer()
        } else {
            descriptionText = "Microphone permission is required for this tab."
        }
    }

    fun resetPeak() {
        history.clear()
        peakDb = 0f
        peakText = "0 dB"
    }

    fun onResume(requestMicPermission: ActivityResultLauncher<String>) {
        if (hasMicPermission()) {
            startAnalyzer()
        } else {
            descriptionText = "Tap to allow microphone access"
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun onPause() {
        analyzer?.stop()
        analyzer = null
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startAnalyzer() {
        if (analyzer != null) return

        val newAnalyzer = AudioAnalyzer(onResult = ::onAudioResult)
        val started = newAnalyzer.start()
        if (started) {
            analyzer = newAnalyzer
        } else {
            descriptionText = "Couldn't start the microphone."
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
            peakText = String.format("%.0f dB", peakDb)
        }

        history.add(smoothedDb)
        dbText = String.format("%.0f", smoothedDb)
        descriptionText = describeLevel(smoothedDb)

        frequencyText = result.dominantFrequencyHz?.let { freq ->
            String.format("Frequency: %.0f Hz", freq)
        } ?: "Frequency: —"

        val normalized = ((smoothedDb - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
        val normalizedPeak = ((peakDb - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
        levelBar?.setLevel(normalized, normalizedPeak)

        waveformView?.setSamples(result.waveform)
    }

    private fun describeLevel(db: Float): String = when {
        db < 30f -> "Quiet"
        db < 50f -> "Moderate"
        db < 70f -> "Noticeable"
        db < 85f -> "Loud"
        else -> "Very loud"
    }
}

@Composable
fun SoundLevelScreen() {
    val context = LocalContext.current
    val controller = remember { SoundLevelController(context) }

    val requestMicPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> controller.onPermissionResult(granted) }

    ResumePauseEffect(
        onResume = { controller.onResume(requestMicPermission) },
        onPause = { controller.onPause() }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MP.Background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BasicText(
            text = controller.dbText,
            modifier = Modifier.padding(top = 8.dp),
            style = TextStyle(color = MP.TextPrimary, fontSize = 52.sp, fontWeight = FontWeight.Bold)
        )
        BasicText(
            text = "dB (approximate)",
            style = TextStyle(color = MP.TextSecondary, fontSize = 13.sp)
        )
        BasicText(
            text = controller.descriptionText,
            modifier = Modifier.padding(top = 4.dp),
            style = TextStyle(color = MP.Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        )
        BasicText(
            text = controller.frequencyText,
            modifier = Modifier.padding(top = 2.dp),
            style = TextStyle(color = MP.TextSecondary, fontSize = 13.sp)
        )

        AndroidView(
            factory = { ctx -> WaveformView(ctx).also { controller.waveformView = it } },
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .height(110.dp)
        )

        AndroidView(
            factory = { ctx -> SoundLevelView(ctx).also { controller.levelBar = it } },
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .height(24.dp)
        )

        // dB history graph: the last few seconds of smoothed level, fixed 0-100 dB scale.
        BasicText(
            text = "Level history (dB)",
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
            style = TextStyle(color = MP.TextSecondary, fontSize = 12.sp)
        )
        LineChart(
            lines = listOf(ChartLine(controller.history, MP.Accent)),
            modifier = Modifier.fillMaxWidth().height(120.dp),
            yMin = 0f, yMax = 100f, fixedStep = 20f
        )

        // The status row sits vertically centred between the level bar and the instructions.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BasicText(
                        text = controller.peakText,
                        style = TextStyle(color = MP.Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    )
                    BasicText(
                        text = "Peak this session",
                        style = TextStyle(color = MP.TextSecondary, fontSize = 12.sp)
                    )
                }
                RealButton(
                    text = "Reset peak",
                    onClick = { controller.resetPeak() },
                    modifier = Modifier.align(Alignment.CenterVertically),
                    primary = false,
                    textColor = MP.TextPrimary,
                    compact = true
                )
                // XML used weightSum=2 with a single weight=1 child: the other half stays empty.
                Spacer(Modifier.weight(1f))
            }
        }

        BasicText(
            text = "Raw microphone amplitude and FFT-based frequency estimate — an uncalibrated approximation, not a certified SPL meter or tuner. Frequency shows only when one tone clearly stands out from background noise.",
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            style = TextStyle(color = MP.TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
        )
    }
}
