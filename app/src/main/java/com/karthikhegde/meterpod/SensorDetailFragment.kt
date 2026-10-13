package com.karthikhegde.meterpod

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

/**
 * Shows one sensor's static specs (vendor, range, resolution, power) plus
 * its live readings, generically: however many floats a given sensor
 * reports in SensorEvent.values (1 for light/proximity, 3 for
 * accelerometer/gyroscope/magnetometer, up to 5 for rotation vector, etc.)
 * are each shown as their own row, rather than hardcoding a layout per
 * sensor type.
 */
class SensorDetailFragment : Fragment(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var sensor: Sensor? = null

    private lateinit var valuesContainer: LinearLayout
    private lateinit var accuracyText: TextView
    private lateinit var noticeText: TextView
    private val valueRows = mutableListOf<TextView>()

    private val requestActivityPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening() else showNotice("Permission denied - no live values available for this sensor.")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_sensor_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val type = arguments?.getInt(ARG_SENSOR_TYPE) ?: return
        val name = arguments?.getString(ARG_SENSOR_NAME) ?: return

        sensorManager = requireContext().getSystemService(SensorManager::class.java)
        sensor = sensorManager.getSensorList(type).find { it.name == name }
            ?: sensorManager.getDefaultSensor(type)

        valuesContainer = view.findViewById(R.id.valuesContainer)
        accuracyText = view.findViewById(R.id.accuracyText)
        noticeText = view.findViewById(R.id.noticeText)

        val currentSensor = sensor
        if (currentSensor == null) {
            view.findViewById<TextView>(R.id.sensorNameText).text = name
            showNotice("This sensor could not be opened.")
            return
        }

        view.findViewById<TextView>(R.id.sensorNameText).text = currentSensor.name
        view.findViewById<TextView>(R.id.sensorVendorText).text =
            "${currentSensor.vendor} · v${currentSensor.version} · ${currentSensor.stringType}"

        val specsContainer = view.findViewById<LinearLayout>(R.id.specsContainer)
        addSpecRow(specsContainer, "Max range", "${currentSensor.maximumRange}")
        addSpecRow(specsContainer, "Resolution", "${currentSensor.resolution}")
        addSpecRow(specsContainer, "Power", String.format("%.2f mA", currentSensor.power))
        addSpecRow(specsContainer, "Min delay", "${currentSensor.minDelay} µs")
        addSpecRow(specsContainer, "Reporting mode", reportingModeName(currentSensor.reportingMode))
    }

    override fun onResume() {
        super.onResume()
        val currentSensor = sensor ?: return

        if (needsActivityRecognitionPermission(currentSensor) && !hasActivityPermission()) {
            requestActivityPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            return
        }
        startListening()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    private fun needsActivityRecognitionPermission(s: Sensor): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            (s.type == Sensor.TYPE_STEP_COUNTER || s.type == Sensor.TYPE_STEP_DETECTOR)
    }

    private fun hasActivityPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startListening() {
        val currentSensor = sensor ?: return
        sensorManager.registerListener(this, currentSensor, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor != sensor) return

        if (valueRows.size != event.values.size) {
            valuesContainer.removeAllViews()
            valueRows.clear()
            for (i in event.values.indices) {
                val row = TextView(requireContext()).apply {
                    setTextColor(Color.parseColor("#E4E8EC"))
                    textSize = 14f
                    setPadding(0, 4, 0, 4)
                    typeface = Typeface.MONOSPACE
                }
                valuesContainer.addView(row)
                valueRows.add(row)
            }
        }

        for (i in event.values.indices) {
            valueRows[i].text = String.format("values[%d]: %.6f", i, event.values[i])
        }
    }

    override fun onAccuracyChanged(changedSensor: Sensor?, accuracy: Int) {
        accuracyText.text = "Accuracy: ${accuracyName(accuracy)}"
    }

    private fun accuracyName(accuracy: Int): String = when (accuracy) {
        SensorManager.SENSOR_STATUS_UNRELIABLE -> "Unreliable"
        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low"
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium"
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "High"
        else -> "Unknown"
    }

    private fun reportingModeName(mode: Int): String = when (mode) {
        Sensor.REPORTING_MODE_CONTINUOUS -> "Continuous"
        Sensor.REPORTING_MODE_ON_CHANGE -> "On change"
        Sensor.REPORTING_MODE_ONE_SHOT -> "One-shot"
        Sensor.REPORTING_MODE_SPECIAL_TRIGGER -> "Special trigger"
        else -> "Unknown"
    }

    private fun addSpecRow(container: LinearLayout, label: String, value: String) {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4, 0, 4)
        }
        row.addView(TextView(requireContext()).apply {
            text = label
            setTextColor(Color.parseColor("#9AA5B1"))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(requireContext()).apply {
            text = value
            setTextColor(Color.parseColor("#FFFFFF"))
            textSize = 13f
            gravity = android.view.Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        container.addView(row)
    }

    private fun showNotice(message: String) {
        noticeText.text = message
        noticeText.visibility = View.VISIBLE
    }

    companion object {
        private const val ARG_SENSOR_TYPE = "sensor_type"
        private const val ARG_SENSOR_NAME = "sensor_name"

        fun newInstance(sensorType: Int, sensorName: String): SensorDetailFragment {
            return SensorDetailFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_SENSOR_TYPE, sensorType)
                    putString(ARG_SENSOR_NAME, sensorName)
                }
            }
        }
    }
}
