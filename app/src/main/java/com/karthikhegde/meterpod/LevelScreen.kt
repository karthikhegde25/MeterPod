package com.karthikhegde.meterpod

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.karthikhegde.meterpod.compose.MP
import com.karthikhegde.meterpod.compose.Text3D
import com.karthikhegde.meterpod.compose.ResumePauseEffect
import com.karthikhegde.meterpod.ui.BubbleLevelView
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Digital spirit level.
 *
 * roll  = tilt around the phone's long axis (left/right), computed from
 *         the X and combined Y/Z gravity components.
 * pitch = tilt around the phone's short axis (front/back), computed from
 *         the Y and combined X/Z gravity components.
 *
 * Both are 0 when the phone (and whatever surface it's resting flat on)
 * is horizontal, and approach +/-90 when the phone is standing on an
 * edge (checking a vertical surface, e.g. a wall, for plumb).
 */
@Composable
fun LevelScreen() {
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(SensorManager::class.java) }
    val accelerometer = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }

    var statusText by remember { mutableStateOf(if (accelerometer == null) "NO SENSOR" else "TILTED") }
    var xValue by remember { mutableStateOf("0.0°") }
    var yValue by remember { mutableStateOf("0.0°") }
    var combinedValue by remember { mutableStateOf("0.0°") }
    val bubbleHolder = remember { arrayOfNulls<BubbleLevelView>(1) }

    val listener = remember {
        object : SensorEventListener {
            private val gravity = FloatArray(3)
            private var hasFirstReading = false
            private val filterAlpha = 0.15f

            private val levelToleranceDeg = 0.7f

            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

                if (!hasFirstReading) {
                    gravity[0] = event.values[0]
                    gravity[1] = event.values[1]
                    gravity[2] = event.values[2]
                    hasFirstReading = true
                } else {
                    gravity[0] = filterAlpha * event.values[0] + (1 - filterAlpha) * gravity[0]
                    gravity[1] = filterAlpha * event.values[1] + (1 - filterAlpha) * gravity[1]
                    gravity[2] = filterAlpha * event.values[2] + (1 - filterAlpha) * gravity[2]
                }

                val gx = gravity[0]
                val gy = gravity[1]
                val gz = gravity[2]

                val roll = Math.toDegrees(
                    atan2(gx.toDouble(), sqrt((gy * gy + gz * gz).toDouble()))
                ).toFloat()

                val pitch = Math.toDegrees(
                    atan2(gy.toDouble(), sqrt((gx * gx + gz * gz).toDouble()))
                ).toFloat()

                val combined = sqrt(roll * roll + pitch * pitch)

                xValue = String.format("%.1f°", roll)
                yValue = String.format("%.1f°", pitch)
                combinedValue = String.format("%.1f°", combined)
                bubbleHolder[0]?.setTilt(roll, pitch)

                val nearHorizontal = abs(roll) < levelToleranceDeg && abs(pitch) < levelToleranceDeg
                val nearVertical = abs(abs(roll) - 90f) < levelToleranceDeg || abs(abs(pitch) - 90f) < levelToleranceDeg

                statusText = when {
                    nearHorizontal -> "LEVEL · HORIZONTAL"
                    nearVertical -> "PLUMB · VERTICAL"
                    else -> "TILTED"
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // no-op
            }
        }
    }

    ResumePauseEffect(
        onResume = {
            accelerometer?.let {
                sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
            }
        },
        onPause = { sensorManager.unregisterListener(listener) }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MP.Background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text3D(text = statusText, fontSize = 28f, modifier = Modifier.padding(top = 12.dp), color = MP.TextPrimary)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx -> BubbleLevelView(ctx).also { bubbleHolder[0] = it } },
                modifier = Modifier.squareFit()
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
        ) {
            ReadoutCell(xValue, "X tilt", Modifier.weight(1f))
            ReadoutCell(yValue, "Y tilt", Modifier.weight(1f))
            ReadoutCell(combinedValue, "Combined", Modifier.weight(1f))
        }
        Text(
            text = "Lay the phone flat on a surface. X/Y show tilt in each direction; the bubble centers and turns green when the surface is horizontal. Stand it on edge against a wall to check vertical.",
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            color = MP.TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ReadoutCell(value: String, label: String, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = MP.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = MP.TextSecondary, fontSize = 12.sp)
    }
}

/** Largest square that fits the incoming constraints (1:1 ConstraintLayout box with 0dp x 0dp). */
private fun Modifier.squareFit(): Modifier = this.layout { measurable, constraints ->
    val side = min(constraints.maxWidth, constraints.maxHeight)
    val p = measurable.measure(Constraints.fixed(side, side))
    layout(side, side) { p.placeRelative(0, 0) }
}
