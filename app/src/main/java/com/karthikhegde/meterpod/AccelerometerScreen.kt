package com.karthikhegde.meterpod

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karthikhegde.meterpod.compose.ChartLine
import com.karthikhegde.meterpod.compose.ChartTitle
import com.karthikhegde.meterpod.compose.LineChart
import com.karthikhegde.meterpod.compose.MP
import com.karthikhegde.meterpod.compose.RealButton
import com.karthikhegde.meterpod.compose.ResumePauseEffect
import com.karthikhegde.meterpod.compose.SeriesBuffer
import kotlin.math.sqrt

private val AxisX = Color(0xFFFF5252)
private val AxisY = Color(0xFF4CAF50)
private val AxisZ = Color(0xFF4C8DFF)
private val AxisTotal = Color(0xFFFFB300)

/**
 * Accelerometer: reports acceleration along the phone's own X, Y and Z axes in m/s² (gravity included).
 * The screen shows the magnitude (total acceleration) and one live graph per axis plus one for the magnitude.
 */
@Composable
fun AccelerometerScreen() {
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(SensorManager::class.java) }
    val accSensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }

    val bx = remember { SeriesBuffer(200) }
    val by = remember { SeriesBuffer(200) }
    val bz = remember { SeriesBuffer(200) }
    val bt = remember { SeriesBuffer(200) }

    var totalText by remember { mutableStateOf(if (accSensor == null) "N/A" else "0.0") }
    var xText by remember { mutableStateOf("0.0") }
    var yText by remember { mutableStateOf("0.0") }
    var zText by remember { mutableStateOf("0.0") }
    var accuracyText by remember { mutableStateOf(if (accSensor == null) "No accelerometer was found on this device." else "Waiting for the sensor…") }
    var paused by remember { mutableStateOf(false) }
    val pausedRef = remember { booleanArrayOf(false) }

    val listener = remember {
        object : SensorEventListener {
            private var sx = 0f; private var sy = 0f; private var sz = 0f
            private var first = true
            private val alpha = 0.5f

            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ACCELEROMETER || pausedRef[0]) return
                val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
                if (first) { sx = x; sy = y; sz = z; first = false }
                else {
                    sx = alpha * x + (1 - alpha) * sx
                    sy = alpha * y + (1 - alpha) * sy
                    sz = alpha * z + (1 - alpha) * sz
                }
                val total = sqrt(sx * sx + sy * sy + sz * sz)
                bx.add(sx); by.add(sy); bz.add(sz); bt.add(total)
                totalText = String.format("%.1f", total)
                xText = String.format("%.1f", sx)
                yText = String.format("%.1f", sy)
                zText = String.format("%.1f", sz)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                accuracyText = when (accuracy) {
                    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "Sensor accuracy: high"
                    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Sensor accuracy: medium"
                    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Sensor accuracy: low"
                    else -> "Sensor accuracy: unreliable"
                }
            }
        }
    }

    ResumePauseEffect(
        onResume = {
            accSensor?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        },
        onPause = { sensorManager.unregisterListener(listener) }
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
            text = totalText,
            style = TextStyle(color = MP.TextPrimary, fontSize = 56.sp, fontWeight = FontWeight.Bold)
        )
        BasicText(text = "m/s² total acceleration (magnitude)", style = TextStyle(color = MP.TextSecondary, fontSize = 13.sp))

        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AxisReadout("X", xText, AxisX)
            AxisReadout("Y", yText, AxisY)
            AxisReadout("Z", zText, AxisZ)
        }

        Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
            ChartTitle("X axis (m/s²)", AxisX, xText)
            LineChart(listOf(ChartLine(bx, AxisX)), Modifier.height(100.dp), minSpan = 4f, zeroLine = true)
            Column(Modifier.padding(top = 12.dp)) { ChartTitle("Y axis (m/s²)", AxisY, yText) }
            LineChart(listOf(ChartLine(by, AxisY)), Modifier.height(100.dp), minSpan = 4f, zeroLine = true)
            Column(Modifier.padding(top = 12.dp)) { ChartTitle("Z axis (m/s²)", AxisZ, zText) }
            LineChart(listOf(ChartLine(bz, AxisZ)), Modifier.height(100.dp), minSpan = 4f, zeroLine = true)
            Column(Modifier.padding(top = 12.dp)) { ChartTitle("Magnitude (m/s²)", AxisTotal, totalText) }
            LineChart(listOf(ChartLine(bt, AxisTotal)), Modifier.height(100.dp), minSpan = 4f)
        }

        Row(
            Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RealButton(
                text = if (paused) "Resume" else "Pause",
                onClick = { paused = !paused; pausedRef[0] = paused },
                compact = true,
                primary = paused
            )
            RealButton(
                text = "Clear graphs",
                onClick = { bx.clear(); by.clear(); bz.clear(); bt.clear() },
                compact = true
            )
        }

        BasicText(
            text = accuracyText,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            style = TextStyle(color = MP.TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
        )
        BasicText(
            text = "X, Y and Z are the phone's own axes (X across the screen, Y along it, Z out of the screen), in metres per second squared. Lying flat and still, Z reads about 9.8 m/s² (gravity) and the magnitude stays near 9.8; shake or move the phone and the graphs and magnitude jump.",
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            style = TextStyle(color = MP.TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
        )
    }
}

@Composable
private fun AxisReadout(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(text = value, style = TextStyle(color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold))
        BasicText(text = "$label (m/s²)", style = TextStyle(color = MP.TextSecondary, fontSize = 12.sp))
    }
}
