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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.karthikhegde.meterpod.compose.MP
import com.karthikhegde.meterpod.compose.ResumePauseEffect
import com.karthikhegde.meterpod.compose.RoundKey
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
private const val KEY_DATE = "date"
private const val KEY_TODAY_STEPS = "today_steps"
private const val KEY_LAST_RAW = "last_raw"
private const val KEY_FOOT_LENGTH = "foot_length_cm"

private class PedometerController(private val context: Context) : SensorEventListener {

    private val sensorManager: SensorManager = context.getSystemService(SensorManager::class.java)
    val stepCounterSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private val prefs: SharedPreferences =
        context.getSharedPreferences("meterpod_pedometer", Context.MODE_PRIVATE)

    var historyChart: StepHistoryChartView? = null

    var todaySteps by mutableStateOf(0)
    var footLengthCm by mutableStateOf(26.0)
    var instructionsText by mutableStateOf(
        "Tracks steps only while MeterPod is open (no background service). Distance is estimated from your foot length using a common rule-of-thumb (step length ≈ 4x foot length), not a precise measurement — adjust the foot length above to personalize it."
    )
    var stepsText by mutableStateOf("0")

    private val stepLengthToFootRatio = 4.15

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dayLabelFormat = SimpleDateFormat("EEE", Locale.US)

    init {
        footLengthCm = prefs.getFloat(KEY_FOOT_LENGTH, 26.0f).toDouble()

        if (stepCounterSensor == null) {
            stepsText = "N/A"
            instructionsText = "No step counter sensor was found on this device."
        }

        // (quirk kept from the original: the "N/A" above is immediately overwritten by updateDisplay())
        loadTodayStateWithRollover()
        updateDisplay()
    }

    fun footLengthMinus() {
        footLengthCm = (footLengthCm - 0.5).coerceAtLeast(15.0)
        prefs.edit().putFloat(KEY_FOOT_LENGTH, footLengthCm.toFloat()).apply()
        updateDisplay()
    }

    fun footLengthPlus() {
        footLengthCm = (footLengthCm + 0.5).coerceAtMost(40.0)
        prefs.edit().putFloat(KEY_FOOT_LENGTH, footLengthCm.toFloat()).apply()
        updateDisplay()
    }

    fun onResume(requestPermission: () -> Unit) {
        if (stepCounterSensor == null) return

        if (hasActivityPermission()) {
            startListening()
        } else {
            requestPermission()
        }
    }

    fun onPause() {
        sensorManager.unregisterListener(this)
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            startListening()
        } else {
            instructionsText = "Activity recognition permission is required to count steps."
        }
    }

    private fun hasActivityPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACTIVITY_RECOGNITION
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

    // distance / foot length readouts are derived from todaySteps / footLengthCm at composition
    fun distanceText(): String {
        val stepLengthCm = footLengthCm * stepLengthToFootRatio
        val distanceKm = (todaySteps * stepLengthCm) / 100000.0
        return String.format("%.2f km", distanceKm)
    }

    fun footLengthLabel(): String = String.format("%.1f cm", footLengthCm)

    private fun updateDisplay() {
        stepsText = String.format("%,d", todaySteps)
    }

    fun updateHistoryChart() {
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

        historyChart?.setEntries(entries)
    }
}

@Composable
fun PedometerScreen() {
    val context = LocalContext.current
    val controller = remember { PedometerController(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> controller.onPermissionResult(granted) }

    ResumePauseEffect(
        onResume = {
            controller.onResume { permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) }
        },
        onPause = { controller.onPause() }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MP.Background)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = controller.stepsText,
            modifier = Modifier.padding(top = 8.dp),
            color = MP.TextPrimary,
            fontSize = 52.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "steps today",
            color = MP.TextSecondary,
            fontSize = 13.sp
        )
        Text(
            text = controller.distanceText(),
            modifier = Modifier.padding(top = 6.dp),
            color = MP.Accent,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Foot length:",
                modifier = Modifier.padding(end = 6.dp),
                color = MP.TextSecondary,
                fontSize = 13.sp
            )
            RoundKey(
                onClick = { controller.footLengthMinus() },
                modifier = Modifier.size(36.dp)
            ) {
                Text("−", color = Color.White, fontSize = 18.sp)
            }
            Text(
                text = controller.footLengthLabel(),
                modifier = Modifier.width(76.dp),
                color = MP.TextPrimary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            RoundKey(
                onClick = { controller.footLengthPlus() },
                modifier = Modifier.size(36.dp)
            ) {
                Text("+", color = Color.White, fontSize = 18.sp)
            }
        }

        AndroidView(
            factory = { ctx ->
                StepHistoryChartView(ctx).also {
                    controller.historyChart = it
                    controller.updateHistoryChart()
                }
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 20.dp)
        )

        Text(
            text = controller.instructionsText,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            color = MP.TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}
