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
class AngleFragment : Fragment(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private lateinit var angleText: TextView
    private lateinit var slopeText: TextView
    private lateinit var dialView: AngleDialView
    private lateinit var instructions: TextView

    private val gravity = FloatArray(3)
    private var hasFirstReading = false
    private val filterAlpha = 0.15f

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_angle, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        angleText = view.findViewById(R.id.angleText)
        slopeText = view.findViewById(R.id.slopeText)
        dialView = view.findViewById(R.id.dialView)
        instructions = view.findViewById(R.id.instructions)

        sensorManager = requireContext().getSystemService(SensorManager::class.java)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer == null) {
            angleText.text = "N/A"
            instructions.text = "No accelerometer sensor was found on this device."
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

        val norm = sqrt(
            gravity[0] * gravity[0] +
            gravity[1] * gravity[1] +
            gravity[2] * gravity[2]
        )
        if (norm < 0.1f) return

        val cosAngle = (gravity[2] / norm).coerceIn(-1f, 1f)
        val angleFromVerticalZ = Math.toDegrees(acos(cosAngle).toDouble()).toFloat()
        val planeAngle = min(angleFromVerticalZ, 180f - angleFromVerticalZ)

        angleText.text = String.format("%.1f°", planeAngle)
        dialView.setAngle(planeAngle)

        // Slope = tan(angle). Cap the displayed angle just shy of 90 degrees
        // so the ratio doesn't blow up to a meaningless huge number.
        if (planeAngle >= 89.5f) {
            slopeText.text = "Slope: vertical (∞)"
        } else {
            val slopeRatio = tan(Math.toRadians(planeAngle.toDouble())).toFloat()
            slopeText.text = String.format("Slope: %.2f (%.0f%%)", slopeRatio, slopeRatio * 100f)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // no-op
    }
}
