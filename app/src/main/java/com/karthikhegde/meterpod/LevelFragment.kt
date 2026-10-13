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
import com.karthikhegde.meterpod.ui.BubbleLevelView
import kotlin.math.abs
import kotlin.math.atan2
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
class LevelFragment : Fragment(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private lateinit var statusText: TextView
    private lateinit var xValue: TextView
    private lateinit var yValue: TextView
    private lateinit var combinedValue: TextView
    private lateinit var bubbleView: BubbleLevelView

    private val gravity = FloatArray(3)
    private var hasFirstReading = false
    private val filterAlpha = 0.15f

    private val levelToleranceDeg = 0.7f

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_level, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        statusText = view.findViewById(R.id.statusText)
        xValue = view.findViewById(R.id.xValue)
        yValue = view.findViewById(R.id.yValue)
        combinedValue = view.findViewById(R.id.combinedValue)
        bubbleView = view.findViewById(R.id.bubbleView)

        sensorManager = requireContext().getSystemService(SensorManager::class.java)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer == null) {
            statusText.text = "NO SENSOR"
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

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

        xValue.text = String.format("%.1f°", roll)
        yValue.text = String.format("%.1f°", pitch)
        combinedValue.text = String.format("%.1f°", combined)
        bubbleView.setTilt(roll, pitch)

        val nearHorizontal = abs(roll) < levelToleranceDeg && abs(pitch) < levelToleranceDeg
        val nearVertical = abs(abs(roll) - 90f) < levelToleranceDeg || abs(abs(pitch) - 90f) < levelToleranceDeg

        statusText.text = when {
            nearHorizontal -> "LEVEL · HORIZONTAL"
            nearVertical -> "PLUMB · VERTICAL"
            else -> "TILTED"
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // no-op
    }
}
