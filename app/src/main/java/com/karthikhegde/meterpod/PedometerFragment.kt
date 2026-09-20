package com.karthikhegde.meterpod

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.karthikhegde.meterpod.ui.StepHistoryChartView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Pedometer: live step count for today plus a 7-day history, and a distance
 * estimate derived from an adjustable "foot length" figure.
 *
 * Uses TYPE_STEP_COUNTER, which reports a cumulative step total since the
 * device's last reboot. To get "steps today" we persist the last-seen raw
 * sensor value and a running daily total in SharedPreferences, and roll
 * over to a new day (archiving yesterday's total into history) whenever
 * the stored date no longer matches today's date.
 *
 * Scope note: this only counts steps while MeterPod is open and this tab
 * is visible - there's no background service keeping the sensor running
 * when the app is closed, so a day you didn't open the app will show 0 (or
 * an incomplete total) in the history. Genuine all-day background tracking
 * would need a foreground service with a persistent notification, which is
 * a much bigger addition than a single tab.
 *
 * Distance: there's no rigorously standardized "foot length -> stride
 * length" formula the way there is for height-based stride estimates.
 * STEP_LENGTH_TO_FOOT_RATIO below is a commonly circulated casual
 * rule-of-thumb (step length ~4x foot length), not a clinically validated
 * figure — the adjustable foot-length field lets you personalize it
 * somewhat, but treat the distance reading as a rough estimate.
 */
class PedometerFragment : Fragment(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null

    private lateinit var stepsText: TextView
    private lateinit var distanceText: TextView
    private lateinit var footLengthText: TextView
    private lateinit var historyChart: StepHistoryChartView
    private lateinit var instructions: TextView

    private lateinit var prefs: SharedPreferences

    private var todaySteps = 0
    private var footLengthCm = 26.0

    private val stepLengthToFootRatio = 4.15

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dayLabelFormat = SimpleDateFormat("EEE", Locale.US)

    private val requestActivityPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startListening()
        } else {
            instructions.text = "Activity recognition permission is required to count steps."
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_pedometer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        stepsText = view.findViewById(R.id.stepsText)
        distanceText = view.findViewById(R.id.distanceText)
        footLengthText = view.findViewById(R.id.footLengthText)
        historyChart = view.findViewById(R.id.historyChart)
        instructions = view.findViewById(R.id.instructions)

        prefs = requireContext().getSharedPreferences("meterpod_pedometer", Context.MODE_PRIVATE)
        footLengthCm = prefs.getFloat(KEY_FOOT_LENGTH, 26.0f).toDouble()

        sensorManager = requireContext().getSystemService(SensorManager::class.java)
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        if (stepCounterSensor == null) {
            stepsText.text = "N/A"
            instructions.text = "No step counter sensor was found on this device."
        }

        view.findViewById<Button>(R.id.footLengthMinusButton).setOnClickListener {
            footLengthCm = (footLengthCm - 0.5).coerceAtLeast(15.0)
            prefs.edit().putFloat(KEY_FOOT_LENGTH, footLengthCm.toFloat()).apply()
            updateDisplay()
        }
        view.findViewById<Button>(R.id.footLengthPlusButton).setOnClickListener {
            footLengthCm = (footLengthCm + 0.5).coerceAtMost(40.0)
            prefs.edit().putFloat(KEY_FOOT_LENGTH, footLengthCm.toFloat()).apply()
            updateDisplay()
        }

        loadTodayStateWithRollover()
        updateDisplay()
        updateHistoryChart()
    }

    override fun onResume() {
        super.onResume()
        if (stepCounterSensor == null) return

        if (hasActivityPermission()) {
            startListening()
        } else {
            requestActivityPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    private fun hasActivityPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startListening() {
        stepCounterSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return

        loadTodayStateWithRollover()

        val rawValue = event.values[0].toLong()
        val lastRawValue = prefs.getLong(KEY_LAST_RAW, -1L)

        if (lastRawValue < 0L) {
            // First reading ever (or after clearing app data): just establish the baseline.
            prefs.edit().putLong(KEY_LAST_RAW, rawValue).apply()
        } else {
            val delta = rawValue - lastRawValue
            // A negative or wildly large delta usually means the device rebooted
            // (the cumulative counter resets to 0) - treat it as a fresh baseline
            // rather than letting it corrupt today's total.
            if (delta in 0..200000) {
                todaySteps += delta.toInt()
                prefs.edit()
                    .putInt(KEY_TODAY_STEPS, todaySteps)
                    .putLong(KEY_LAST_RAW, rawValue)
                    .apply()
            } else {
                prefs.edit().putLong(KEY_LAST_RAW, rawValue).apply()
            }
        }

        updateDisplay()
        updateHistoryChart()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // no-op
    }

    /** Ensures todaySteps reflects the current calendar day, archiving the previous day if it rolled over. */
    private fun loadTodayStateWithRollover() {
        val today = dateFormat.format(Date())
        val storedDate = prefs.getString(KEY_DATE, null)

        if (storedDate == today) {
            todaySteps = prefs.getInt(KEY_TODAY_STEPS, 0)
            return
        }

        // New day (or first run). Archive the previous day's total into history, then reset.
        if (storedDate != null) {
            val previousSteps = prefs.getInt(KEY_TODAY_STEPS, 0)
            prefs.edit().putInt(historyKey(storedDate), previousSteps).apply()
        }

        todaySteps = 0
        prefs.edit()
            .putString(KEY_DATE, today)
            .putInt(KEY_TODAY_STEPS, 0)
            .apply()
        // Deliberately not resetting KEY_LAST_RAW here: the step counter sensor's
        // cumulative value keeps counting across midnight, so the next sensor event's
        // delta still needs the real last-seen raw value to compute correctly.
    }

    private fun historyKey(date: String) = "history_$date"

    private fun updateDisplay() {
        stepsText.text = String.format("%,d", todaySteps)

        val stepLengthCm = footLengthCm * stepLengthToFootRatio
        val distanceKm = (todaySteps * stepLengthCm) / 100000.0
        distanceText.text = String.format("%.2f km", distanceKm)

        footLengthText.text = String.format("%.1f cm", footLengthCm)
    }

    private fun updateHistoryChart() {
        val baseCalendar = Calendar.getInstance()
        val entries = mutableListOf<StepHistoryChartView.DayEntry>()

        for (offset in 6 downTo 0) {
            val cal = baseCalendar.clone() as Calendar
            cal.add(Calendar.DAY_OF_YEAR, -offset)
            val dateKey = dateFormat.format(cal.time)
            val label = dayLabelFormat.format(cal.time)
            val isToday = offset == 0
            val steps = if (isToday) todaySteps else prefs.getInt(historyKey(dateKey), 0)
            entries.add(StepHistoryChartView.DayEntry(label, steps, isToday))
        }

        historyChart.setEntries(entries)
    }

    companion object {
        private const val KEY_DATE = "date"
        private const val KEY_TODAY_STEPS = "today_steps"
        private const val KEY_LAST_RAW = "last_raw"
        private const val KEY_FOOT_LENGTH = "foot_length_cm"
    }
}
