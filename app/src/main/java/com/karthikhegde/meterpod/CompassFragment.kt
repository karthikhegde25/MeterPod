package com.karthikhegde.meterpod

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.karthikhegde.meterpod.ui.CompassView
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
class CompassFragment : Fragment(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var rotationVectorSensor: Sensor? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private var usingRotationVector = false

    private lateinit var headingText: TextView
    private lateinit var cardinalText: TextView
    private lateinit var fieldStrengthText: TextView
    private lateinit var fieldAxesText: TextView
    private lateinit var compassView: CompassView
    private lateinit var instructions: TextView

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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_compass, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        headingText = view.findViewById(R.id.headingText)
        cardinalText = view.findViewById(R.id.cardinalText)
        fieldStrengthText = view.findViewById(R.id.fieldStrengthText)
        fieldAxesText = view.findViewById(R.id.fieldAxesText)
        compassView = view.findViewById(R.id.compassView)
        instructions = view.findViewById(R.id.instructions)

        sensorManager = requireContext().getSystemService(SensorManager::class.java)
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        usingRotationVector = rotationVectorSensor != null

        if (!usingRotationVector && (accelerometer == null || magnetometer == null)) {
            headingText.text = "N/A"
            instructions.text = "This device is missing the sensors needed for a compass."
        }
        if (magnetometer == null) {
            fieldStrengthText.text = "Field: N/A"
            fieldAxesText.text = "X: — Y: — Z: —"
        }
    }

    override fun onResume() {
        super.onResume()
        if (usingRotationVector) {
            rotationVectorSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        } else {
            accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        }
        // Registered unconditionally (in addition to whichever heading source is active
        // above) so field strength is always available, even when rotation vector is
        // handling heading and wouldn't otherwise need raw magnetometer data.
        magnetometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

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

        headingText.text = String.format("%.0f°", smoothedHeading)
        cardinalText.text = cardinalDirection(smoothedHeading)
        compassView.setHeading(smoothedHeading)
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

        fieldStrengthText.text = String.format("Net field: %.0f µT", smoothedFieldMicroTesla)
        fieldAxesText.text = String.format(
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
