package com.karthikhegde.meterpod

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.karthikhegde.meterpod.compose.MP
import com.karthikhegde.meterpod.compose.Text3D
import com.karthikhegde.meterpod.compose.ResumePauseEffect
import com.karthikhegde.meterpod.ui.AngleDialView
import kotlin.math.acos
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Measures the acute angle between the plane of the phone and the ground,
 * and derives the slope (rise/run ratio) from that same angle.
 *
 * Physics: when the phone rests on a corner or edge, the component of
 * gravity along the phone's screen-normal axis (Z) tells us the angle
 * between that normal and true vertical. The angle between the phone's
 * PLANE and the ground plane is the same as the angle between their
 * normals (taking the acute version), so:
 *
 *   angleFromVerticalZ = acos(gz / |g|)      // 0..180
 *   planeAngle          = min(angleFromVerticalZ, 180 - angleFromVerticalZ)
 *
 * Slope is just tan(planeAngle): a 45 degree incline is slope 1.0 (100%),
 * a 30 degree incline is slope ~0.577 (58%), and it grows without bound
 * as the angle approaches 90 degrees (a sheer vertical face).
 */
@Composable
fun AngleScreen() {
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(SensorManager::class.java) }
    val accelerometer = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }

    var angleText by remember { mutableStateOf(if (accelerometer == null) "N/A" else "0.0°") }
    var slopeText by remember { mutableStateOf("Slope: 0.00 (0%)") }
    var instructions by remember {
        mutableStateOf(
            if (accelerometer == null) "No accelerometer sensor was found on this device."
            else "Rest a corner or edge of the phone on the ground. Slope is the rise-over-run ratio (tan of the angle) — 1.0 means a 45° incline."
        )
    }
    val dialHolder = remember { arrayOfNulls<AngleDialView>(1) }

    val listener = remember {
        object : SensorEventListener {
            private val gravity = FloatArray(3)
            private var hasFirstReading = false
            private val filterAlpha = 0.15f

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

                val norm = sqrt(
                    gravity[0] * gravity[0] +
                    gravity[1] * gravity[1] +
                    gravity[2] * gravity[2]
                )
                if (norm < 0.1f) return

                val cosAngle = (gravity[2] / norm).coerceIn(-1f, 1f)
                val angleFromVerticalZ = Math.toDegrees(acos(cosAngle).toDouble()).toFloat()
                val planeAngle = min(angleFromVerticalZ, 180f - angleFromVerticalZ)

                angleText = String.format("%.1f°", planeAngle)
                dialHolder[0]?.setAngle(planeAngle)

                // Slope = tan(angle). Cap the displayed angle just shy of 90 degrees
                // so the ratio doesn't blow up to a meaningless huge number.
                if (planeAngle >= 89.5f) {
                    slopeText = "Slope: vertical (∞)"
                } else {
                    val slopeRatio = tan(Math.toRadians(planeAngle.toDouble())).toFloat()
                    slopeText = String.format("Slope: %.2f (%.0f%%)", slopeRatio, slopeRatio * 100f)
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
        Text3D(text = angleText, fontSize = 72f, modifier = Modifier.padding(top = 16.dp), color = MP.TextPrimary)
        Text(
            text = "from ground",
            color = MP.TextSecondary,
            fontSize = 14.sp
        )
        Text(
            text = slopeText,
            modifier = Modifier.padding(top = 10.dp),
            color = MP.Accent,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        // dial: square, as large as fits between the slope text and the instructions
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx -> AngleDialView(ctx).also { dialHolder[0] = it } },
                modifier = Modifier.squareFit()
            )
        }
        Text(
            text = instructions,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            color = MP.TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

/** Largest square that fits the incoming constraints (1:1 ConstraintLayout box with 0dp x 0dp). */
private fun Modifier.squareFit(): Modifier = this.layout { measurable, constraints ->
    val side = min(constraints.maxWidth, constraints.maxHeight)
    val p = measurable.measure(
        androidx.compose.ui.unit.Constraints.fixed(side, side)
    )
    layout(side, side) { p.placeRelative(0, 0) }
}
