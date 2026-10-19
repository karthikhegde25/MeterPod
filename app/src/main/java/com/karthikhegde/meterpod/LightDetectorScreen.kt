package com.karthikhegde.meterpod

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.karthikhegde.meterpod.compose.ChartLine
import com.karthikhegde.meterpod.compose.LineChart
import com.karthikhegde.meterpod.compose.MP
import com.karthikhegde.meterpod.compose.SeriesBuffer
import com.karthikhegde.meterpod.compose.ResumePauseEffect
import com.karthikhegde.meterpod.compose.toolTileBackground
import com.karthikhegde.meterpod.ui.SoundLevelView
import kotlin.math.log10

/**
 * Ambient light meter using the phone's built-in light sensor (the same
 * one behind auto-brightness). Lux readings span several orders of
 * magnitude — moonlight is ~0.1 lux, a lit office ~500 lux, direct
 * sunlight ~30,000-100,000 lux — so the level bar is scaled logarithmically
 * rather than linearly, or the bar would sit empty for almost every indoor
 * reading and only move near the very top for outdoor light.
 */
@Composable
fun LightDetectorScreen() {
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(SensorManager::class.java) }
    val lightSensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) }

    var luxText by remember { mutableStateOf(if (lightSensor == null) "N/A" else "0") }
    var descriptionText by remember { mutableStateOf("Dark") }
    var guidanceText by remember {
        mutableStateOf(
            if (lightSensor == null) "No ambient light sensor was found on this device."
            else "For an accurate reading: the light sensor sits near the front camera/earpiece — point that edge of the phone directly at the light source, keep fingers and cases clear of it, and avoid casting your own shadow over the phone."
        )
    }
    val levelHolder = remember { arrayOfNulls<SoundLevelView>(1) }
    val history = remember { SeriesBuffer(150) }

    val listener = remember {
        object : SensorEventListener {
            private var smoothedLux = 0f
            private var peakLux = 0f
            private var hasFirstReading = false
            private val filterAlpha = 0.3f

            // log10(100000) = 5, so this comfortably covers everything up to bright direct sunlight.
            private val maxLogLux = 5f

            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_LIGHT) return

                val lux = event.values[0]
                smoothedLux = if (!hasFirstReading) {
                    hasFirstReading = true
                    lux
                } else {
                    filterAlpha * lux + (1 - filterAlpha) * smoothedLux
                }
                if (smoothedLux > peakLux) peakLux = smoothedLux

                history.add(log10((smoothedLux + 1).toDouble()).toFloat())
                luxText = String.format("%,.0f", smoothedLux)
                descriptionText = describeLux(smoothedLux)

                val normalized = (log10((smoothedLux + 1).toDouble()).toFloat() / maxLogLux).coerceIn(0f, 1f)
                val normalizedPeak = (log10((peakLux + 1).toDouble()).toFloat() / maxLogLux).coerceIn(0f, 1f)
                levelHolder[0]?.setLevel(normalized, normalizedPeak)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // no-op
            }

            private fun describeLux(lux: Float): String = when {
                lux < 1f -> "Dark"
                lux < 50f -> "Dim indoor"
                lux < 300f -> "Indoor lighting"
                lux < 1000f -> "Bright indoor / overcast"
                lux < 10000f -> "Daylight (shade)"
                lux < 30000f -> "Daylight"
                else -> "Direct sunlight"
            }
        }
    }

    ResumePauseEffect(
        onResume = {
            lightSensor?.let {
                sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        },
        onPause = { sensorManager.unregisterListener(listener) }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MP.Background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BasicText(
            text = luxText,
            modifier = Modifier.padding(top = 16.dp),
            style = TextStyle(color = MP.TextPrimary, fontSize = 64.sp, fontWeight = FontWeight.Bold)
        )
        BasicText(
            text = "lux",
            style = TextStyle(color = MP.TextSecondary, fontSize = 14.sp)
        )
        BasicText(
            text = descriptionText,
            modifier = Modifier.padding(top = 6.dp),
            style = TextStyle(color = MP.Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        )
        AndroidView(
            factory = { ctx -> SoundLevelView(ctx).also { levelHolder[0] = it } },
            modifier = Modifier
                .padding(top = 28.dp)
                .fillMaxWidth()
                .height(24.dp)
        )
        // Lux history graph. Plotted as log10(lux + 1) so dim rooms and sunlight both stay readable;
        // the axis labels are shown in lux (1, 10, 100 ... 100,000).
        BasicText(
            text = "Light history (lux, log scale)",
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp),
            style = TextStyle(color = MP.TextSecondary, fontSize = 12.sp)
        )
        LineChart(
            lines = listOf(ChartLine(history, MP.Accent)),
            modifier = Modifier.fillMaxWidth().height(130.dp),
            yMin = 0f, yMax = 5f, fixedStep = 1f,
            labelFor = { v -> if (v <= 0f) "0" else String.format("%,.0f", Math.pow(10.0, v.toDouble())) }
        )
        // guidance: vertically centred in the space left below the level bar (ConstraintLayout
        // with top and bottom constraints and wrap_content height), 24dp margin above.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = guidanceText,
                modifier = Modifier
                    .fillMaxWidth()
                    .toolTileBackground()
                    .padding(14.dp),
                style = TextStyle(
                    color = MP.TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}
