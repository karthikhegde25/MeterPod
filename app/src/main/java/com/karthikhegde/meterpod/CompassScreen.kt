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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.karthikhegde.meterpod.compose.MP
import com.karthikhegde.meterpod.compose.ResumePauseEffect
import com.karthikhegde.meterpod.ui.CompassView
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Compass heading, plus the raw magnetic field strength.
 *
 * Prefers TYPE_ROTATION_VECTOR (a fused, hardware/firmware-smoothed sensor
 * combining accelerometer + magnetometer + gyroscope where available),
 * which is far less jittery than computing orientation from raw
 * accelerometer + magnetometer readings directly. Falls back to that raw
 * combination on devices without a rotation vector sensor.
 *
 * Either way we end up with an azimuth in degrees, 0-360, where 0 = north,
 * 90 = east, 180 = south, 270 = west, increasing clockwise.
 *
 * The magnetometer is registered unconditionally (even when rotation
 * vector is doing the heading math) purely to read field strength -
 * Earth's field is typically ~25-65 microtesla depending on location;
 * readings well outside that range usually mean nearby metal/magnets or
 * electronics are distorting the reading, same idea as the Metal Detector
 * tab but shown as an absolute figure here rather than a baseline delta.
 */
@Composable
fun CompassScreen() {
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(SensorManager::class.java) }
    val rotationVectorSensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }
    val accelerometer = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    val magnetometer = remember { sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) }
    val usingRotationVector = rotationVectorSensor != null

    val noCompassSensors = !usingRotationVector && (accelerometer == null || magnetometer == null)

    var headingText by remember { mutableStateOf(if (noCompassSensors) "N/A" else "0°") }
    var cardinalText by remember { mutableStateOf("N") }
    var fieldStrengthText by remember {
        mutableStateOf(if (magnetometer == null) "Field: N/A" else "Net field: 0 µT")
    }
    var fieldAxesText by remember {
        mutableStateOf(if (magnetometer == null) "X: — Y: — Z: —" else "X: 0   Y: 0   Z: 0 µT")
    }
    val instructions = if (noCompassSensors) {
        "This device is missing the sensors needed for a compass."
    } else {
        "Hold the phone flat and away from metal or magnets. The blue pointer shows the direction the top of the phone faces; the dial rotates so N stays pointed at magnetic north."
    }
    val compassHolder = remember { arrayOfNulls<CompassView>(1) }

    val listener = remember {
        object : SensorEventListener {
            // Raw sensor buffers for the accelerometer + magnetometer fallback path.
            private val accelReading = FloatArray(3)
            private val magnetReading = FloatArray(3)
            private var hasAccel = false
            private var hasMagnet = false

            private val rotationMatrix = FloatArray(9)
            private val orientationValues = FloatArray(3)

            private var smoothedHeading = 0f
            private var hasFirstHeading = false
            private val filterAlpha = 0.15f

            private var smoothedFieldMicroTesla = 0f
            // Smoothed per-axis (device frame) field components, X/Y/Z, in microtesla.
            private val smoothedFieldAxes = FloatArray(3)
            private var hasFirstFieldReading = false
            private val fieldFilterAlpha = 0.2f

            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientationValues)
                        val azimuthDeg = normalizeDegrees(Math.toDegrees(orientationValues[0].toDouble()).toFloat())
                        updateHeading(azimuthDeg)
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, accelReading, 0, 3)
                        hasAccel = true
                        computeAzimuthFromAccelMagnet()?.let { updateHeading(it) }
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, magnetReading, 0, 3)
                        hasMagnet = true
                        updateFieldStrength(event.values)
                        if (!usingRotationVector) {
                            computeAzimuthFromAccelMagnet()?.let { updateHeading(it) }
                        }
                    }
                }
            }

            private fun computeAzimuthFromAccelMagnet(): Float? {
                if (!hasAccel || !hasMagnet) return null
                val success = SensorManager.getRotationMatrix(rotationMatrix, null, accelReading, magnetReading)
                if (!success) return null
                SensorManager.getOrientation(rotationMatrix, orientationValues)
                return normalizeDegrees(Math.toDegrees(orientationValues[0].toDouble()).toFloat())
            }

            private fun normalizeDegrees(deg: Float): Float {
                var result = deg % 360f
                if (result < 0f) result += 360f
                return result
            }

            /** Low-pass filters across the 0/360 wraparound boundary to avoid a visible snap. */
            private fun updateHeading(newHeadingDeg: Float) {
                if (!hasFirstHeading) {
                    smoothedHeading = newHeadingDeg
                    hasFirstHeading = true
                } else {
                    var delta = newHeadingDeg - smoothedHeading
                    if (delta > 180f) delta -= 360f
                    if (delta < -180f) delta += 360f
                    smoothedHeading = normalizeDegrees(smoothedHeading + filterAlpha * delta)
                }

                headingText = String.format("%.0f°", smoothedHeading)
                cardinalText = cardinalDirection(smoothedHeading)
                compassHolder[0]?.setHeading(smoothedHeading)
            }

            private fun updateFieldStrength(values: FloatArray) {
                val magnitude = sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])

                if (!hasFirstFieldReading) {
                    hasFirstFieldReading = true
                    smoothedFieldMicroTesla = magnitude
                    smoothedFieldAxes[0] = values[0]
                    smoothedFieldAxes[1] = values[1]
                    smoothedFieldAxes[2] = values[2]
                } else {
                    smoothedFieldMicroTesla = fieldFilterAlpha * magnitude + (1 - fieldFilterAlpha) * smoothedFieldMicroTesla
                    for (i in 0..2) {
                        smoothedFieldAxes[i] = fieldFilterAlpha * values[i] + (1 - fieldFilterAlpha) * smoothedFieldAxes[i]
                    }
                }

                fieldStrengthText = String.format("Net field: %.0f µT", smoothedFieldMicroTesla)
                fieldAxesText = String.format(
                    "X: %.0f   Y: %.0f   Z: %.0f µT",
                    smoothedFieldAxes[0], smoothedFieldAxes[1], smoothedFieldAxes[2]
                )
            }

            private fun cardinalDirection(deg: Float): String {
                val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
                val index = (((deg + 22.5f) / 45f).toInt()) % 8
                return directions[index]
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // no-op
            }
        }
    }

    ResumePauseEffect(
        onResume = {
            if (usingRotationVector) {
                rotationVectorSensor?.let {
                    sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
                }
            } else {
                accelerometer?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
            }
            // Registered unconditionally (in addition to whichever heading source is active
            // above) so field strength is always available, even when rotation vector is
            // handling heading and wouldn't otherwise need raw magnetometer data.
            magnetometer?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
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
        Text(
            text = headingText,
            modifier = Modifier.padding(top = 16.dp),
            color = MP.TextPrimary,
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = cardinalText,
            color = MP.Accent,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = fieldStrengthText,
            modifier = Modifier.padding(top = 4.dp),
            color = MP.TextSecondary,
            fontSize = 13.sp
        )
        Text(
            text = fieldAxesText,
            modifier = Modifier.padding(top = 2.dp),
            color = MP.TextSecondary,
            fontSize = 12.sp
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx -> CompassView(ctx).also { compassHolder[0] = it } },
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
    val p = measurable.measure(Constraints.fixed(side, side))
    layout(side, side) { p.placeRelative(0, 0) }
}
