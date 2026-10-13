package com.karthikhegde.meterpod

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.karthikhegde.meterpod.ui.MetalDetectorView
import com.karthikhegde.meterpod.ui.MetalDirectionView
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Metal detector: reads the magnetometer's raw field vector (not just the
 * heading direction, which the Compass tab uses) and compares it against a
 * calibrated baseline vector. Ferrous/metallic objects and other magnets
 * locally distort Earth's field, so a reading that deviates well from the
 * baseline usually means something metallic is nearby.
 *
 * Direction: a single 3-axis magnetometer can't triangulate a precise
 * bearing to the source the way multiple sensors could. What it *can* do
 * is show which way, in the phone's own flat plane, the field deviation
 * is strongest — the same heuristic hobbyist metal-detector apps use. This
 * only means something if you hold the phone roughly flat/level while
 * sweeping it around, like a real detector wand; the arrow is a "try this
 * direction" hint, not a laser-precise bearing.
 *
 * This is inherently approximate — phone magnetometers are small and noisy,
 * and every phone has its own baseline depending on location and nearby
 * hardware, hence the calibrate step rather than a fixed threshold.
 */
class MetalDetectorFragment : Fragment(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var magnetometer: Sensor? = null

    private lateinit var statusText: TextView
    private lateinit var fieldText: TextView
    private lateinit var deltaText: TextView
    private lateinit var intensityBar: MetalDetectorView
    private lateinit var directionView: MetalDirectionView
    private lateinit var instructions: TextView

    // Smoothed field vector, in device coordinates (x = right, y = up the screen, z = out of screen).
    private val smoothed = floatArrayOf(0f, 0f, 0f)
    private var hasFirstReading = false
    private val filterAlpha = 0.2f

    // Baseline vector captured at calibration time (or on first reading, as a default).
    private val baseline = floatArrayOf(0f, 0f, 45f)
    private var hasCalibrated = false

    // Deviation (µT) above baseline that counts as "strong signal" -> intensity 1.0.
    private val strongThreshold = 35f

    // Below this deviation, the direction arrow is considered noise and greys out.
    private val directionActiveThreshold = 8f

    private var lastVibrateTimeMs = 0L
    private val vibrateCooldownMs = 800L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_metal_detector, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        statusText = view.findViewById(R.id.statusText)
        fieldText = view.findViewById(R.id.fieldText)
        deltaText = view.findViewById(R.id.deltaText)
        intensityBar = view.findViewById(R.id.intensityBar)
        directionView = view.findViewById(R.id.directionView)
        instructions = view.findViewById(R.id.instructions)

        sensorManager = requireContext().getSystemService(SensorManager::class.java)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (magnetometer == null) {
            fieldText.text = "N/A"
            instructions.text = "No magnetometer sensor was found on this device."
        }

        view.findViewById<Button>(R.id.calibrateButton).setOnClickListener {
            baseline[0] = smoothed[0]
            baseline[1] = smoothed[1]
            baseline[2] = smoothed[2]
            hasCalibrated = true
        }
    }

    override fun onResume() {
        super.onResume()
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_MAGNETIC_FIELD) return

        if (!hasFirstReading) {
            hasFirstReading = true
            smoothed[0] = event.values[0]
            smoothed[1] = event.values[1]
            smoothed[2] = event.values[2]
            if (!hasCalibrated) {
                baseline[0] = smoothed[0]
                baseline[1] = smoothed[1]
                baseline[2] = smoothed[2]
            }
        } else {
            smoothed[0] = filterAlpha * event.values[0] + (1 - filterAlpha) * smoothed[0]
            smoothed[1] = filterAlpha * event.values[1] + (1 - filterAlpha) * smoothed[1]
            smoothed[2] = filterAlpha * event.values[2] + (1 - filterAlpha) * smoothed[2]
        }

        val dx = smoothed[0] - baseline[0]
        val dy = smoothed[1] - baseline[1]
        val dz = smoothed[2] - baseline[2]
        val deltaMagnitude = sqrt(dx * dx + dy * dy + dz * dz)
        val currentMagnitude = sqrt(smoothed[0] * smoothed[0] + smoothed[1] * smoothed[1] + smoothed[2] * smoothed[2])
        val intensity = (deltaMagnitude / strongThreshold).coerceIn(0f, 1f)

        fieldText.text = String.format("%.0f µT", currentMagnitude)
        deltaText.text = String.format("+%.0f µT from baseline", deltaMagnitude)
        intensityBar.setIntensity(intensity)

        // Bearing of the anomaly within the phone's own flat (x-y/screen) plane.
        // 0 = toward the top of the phone, 90 = toward the right edge.
        val bearingDeg = normalizeDegrees(Math.toDegrees(atan2(dx.toDouble(), dy.toDouble())).toFloat())
        directionView.setBearing(bearingDeg, deltaMagnitude >= directionActiveThreshold)

        statusText.text = when {
            deltaMagnitude < 10f -> "NO METAL DETECTED"
            deltaMagnitude < strongThreshold -> "METAL NEARBY"
            else -> "STRONG SIGNAL"
        }

        if (deltaMagnitude >= strongThreshold) {
            maybeVibrate()
        }
    }

    private fun normalizeDegrees(deg: Float): Float {
        var result = deg % 360f
        if (result < 0f) result += 360f
        return result
    }

    private fun maybeVibrate() {
        val now = System.currentTimeMillis()
        if (now - lastVibrateTimeMs < vibrateCooldownMs) return
        lastVibrateTimeMs = now

        val context = context ?: return
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(120)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // no-op
    }
}
