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
class LightDetectorFragment : Fragment(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null

    private lateinit var luxText: TextView
    private lateinit var descriptionText: TextView
    private lateinit var guidanceText: TextView
    private lateinit var levelBar: SoundLevelView

    private var smoothedLux = 0f
    private var peakLux = 0f
    private var hasFirstReading = false
    private val filterAlpha = 0.3f

    // log10(100000) = 5, so this comfortably covers everything up to bright direct sunlight.
    private val maxLogLux = 5f

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_light_detector, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        luxText = view.findViewById(R.id.luxText)
        descriptionText = view.findViewById(R.id.descriptionText)
        guidanceText = view.findViewById(R.id.guidanceText)
        levelBar = view.findViewById(R.id.levelBar)

        sensorManager = requireContext().getSystemService(SensorManager::class.java)
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        if (lightSensor == null) {
            luxText.text = "N/A"
            guidanceText.text = "No ambient light sensor was found on this device."
        }
    }

    override fun onResume() {
        super.onResume()
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

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

        luxText.text = String.format("%,.0f", smoothedLux)
        descriptionText.text = describeLux(smoothedLux)

        val normalized = (log10((smoothedLux + 1).toDouble()).toFloat() / maxLogLux).coerceIn(0f, 1f)
        val normalizedPeak = (log10((peakLux + 1).toDouble()).toFloat() / maxLogLux).coerceIn(0f, 1f)
        levelBar.setLevel(normalized, normalizedPeak)
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
