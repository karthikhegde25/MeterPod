package com.karthikhegde.meterpod

import android.Manifest
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.karthikhegde.meterpod.compose.MP
import com.karthikhegde.meterpod.compose.ResumePauseEffect
import com.karthikhegde.meterpod.compose.TileCard

/**
 * Shows one sensor's static specs (vendor, range, resolution, power) plus
 * its live readings, generically: however many floats a given sensor
 * reports in SensorEvent.values (1 for light/proximity, 3 for
 * accelerometer/gyroscope/magnetometer, up to 5 for rotation vector, etc.)
 * are each shown as their own row, rather than hardcoding a layout per
 * sensor type.
 */
@Composable
fun SensorDetailScreen(type: Int, rawName: String) {
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(SensorManager::class.java) }
    // The list passes "name|wake": wake-up and non-wake-up variants of a sensor share one name.
    val name = rawName.substringBeforeLast('|', rawName)
    val wake: Boolean? = rawName.substringAfterLast('|', "").let { if (it == "1") true else if (it == "0") false else null }
    val sensor: Sensor? = remember(type, rawName) {
        val all = sensorManager.getSensorList(type).filter { it.name == name }
        all.firstOrNull { wake == null || it.isWakeUpSensor == wake } ?: all.firstOrNull()
            ?: sensorManager.getDefaultSensor(type)
    }
    val triggerOnly = sensor != null &&
        (sensor.reportingMode == Sensor.REPORTING_MODE_ONE_SHOT || sensor.reportingMode == Sensor.REPORTING_MODE_SPECIAL_TRIGGER && isTriggerType(sensor.type))
    var triggerCount by remember { mutableStateOf(0) }
    var lastTrigger by remember { mutableStateOf("never") }

    var accuracyLabel by remember { mutableStateOf("Accuracy: —") }
    var valueRows by remember { mutableStateOf<List<String>>(emptyList()) }
    var notice by remember {
        mutableStateOf<String?>(if (sensor == null) "This sensor could not be opened." else null)
    }

    val listener = remember(sensor) {
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Compare by type + name, not object identity: the framework may hand back a different Sensor instance.
                val src = event.sensor
                if (sensor != null && (src.type != sensor.type || src.name != sensor.name)) return

                // One row per float in event.values (rows are rebuilt when the count changes).
                valueRows = if (event.values.isEmpty()) listOf("(this sensor reports an event but no numbers)")
                else event.values.indices.map { i -> String.format("values[%d]: %.6f", i, event.values[i]) }
                if (notice?.startsWith("Waiting") == true) notice = null
            }

            override fun onAccuracyChanged(changedSensor: Sensor?, accuracy: Int) {
                accuracyLabel = "Accuracy: ${accuracyName(accuracy)}"
            }
        }
    }

    val triggerListener = remember(sensor) {
        object : android.hardware.TriggerEventListener() {
            override fun onTrigger(event: android.hardware.TriggerEvent) {
                triggerCount += 1
                lastTrigger = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                valueRows = event.values.indices.map { i -> String.format("values[%d]: %.6f", i, event.values[i]) }
                // One-shot sensors disarm after firing: arm again so the next event is caught too.
                try { sensorManager.requestTriggerSensor(this, event.sensor) } catch (_: Exception) { }
            }
        }
    }

    fun startListening() {
        val currentSensor = sensor ?: return
        if (triggerOnly || currentSensor.reportingMode == Sensor.REPORTING_MODE_ONE_SHOT) {
            val ok = try { sensorManager.requestTriggerSensor(triggerListener, currentSensor) } catch (_: Exception) { false }
            notice = if (ok) "Waiting for the sensor to trigger (e.g. move the phone for motion sensors)."
            else "This device refused to arm the sensor."
            return
        }
        val ok = sensorManager.registerListener(listener, currentSensor, SensorManager.SENSOR_DELAY_UI)
        notice = if (!ok) "Android could not start this sensor (it may be disabled, in use, or need a permission)."
        else "Waiting for the first reading... some sensors only report when the value changes."
    }

    val requestActivityPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening() else notice = "Permission denied - no live values available for this sensor."
    }

    val requestBodyPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening() else notice = "Permission denied - heart-rate and body sensors cannot be read without it."
    }

    ResumePauseEffect(
        onResume = {
            val currentSensor = sensor
            if (currentSensor != null) {
                if (needsActivityRecognitionPermission(currentSensor) && !hasActivityPermission(context)) {
                    requestActivityPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                } else if (needsBodySensorsPermission(currentSensor) && !hasPermission(context, Manifest.permission.BODY_SENSORS)) {
                    requestBodyPermission.launch(Manifest.permission.BODY_SENSORS)
                } else {
                    startListening()
                }
            }
        },
        onPause = {
            sensorManager.unregisterListener(listener)
            try { sensor?.let { sensorManager.cancelTriggerSensor(triggerListener, it) } } catch (_: Exception) { }
        }
    )

    // The original XML/code set these with raw pixel paddings (setPadding(0, 4, 0, 4)).
    val fourPx = with(LocalDensity.current) { 4.toDp() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MP.Background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        BasicText(
            text = sensor?.name ?: name,
            modifier = Modifier.fillMaxWidth(),
            style = TextStyle(color = MP.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        )

        BasicText(
            text = if (sensor != null) "${sensor.vendor} · v${sensor.version} · ${sensor.stringType}" else "Vendor · v1",
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp),
            style = TextStyle(color = MP.TextSecondary, fontSize = 13.sp)
        )

        TileCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                if (sensor != null) {
                    SpecRow("Max range", "${sensor.maximumRange}", fourPx)
                    SpecRow("Resolution", "${sensor.resolution}", fourPx)
                    SpecRow("Power", String.format("%.2f mA", sensor.power), fourPx)
                    SpecRow("Min delay", "${sensor.minDelay} µs", fourPx)
                    SpecRow("Reporting mode", reportingModeName(sensor.reportingMode), fourPx)
                }
            }
        }

        BasicText(
            text = "Live values",
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            style = TextStyle(color = MP.Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        )

        BasicText(
            text = accuracyLabel,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            style = TextStyle(color = MP.TextSecondary, fontSize = 12.sp)
        )

        if (triggerOnly) BasicText(
            text = "Triggered $triggerCount time(s) - last: $lastTrigger",
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            style = TextStyle(color = MP.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        )

        TileCard(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Column(Modifier.fillMaxWidth()) {
                valueRows.forEach { rowText ->
                    BasicText(
                        text = rowText,
                        modifier = Modifier.padding(top = fourPx, bottom = fourPx),
                        style = TextStyle(
                            color = Color(0xFFE4E8EC),
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }
        }

        val noticeText = notice
        if (noticeText != null) {
            BasicText(
                text = noticeText,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                style = TextStyle(
                    color = MP.TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String, vPadding: androidx.compose.ui.unit.Dp) {
    Row(Modifier.fillMaxWidth().padding(top = vPadding, bottom = vPadding)) {
        BasicText(
            text = label,
            modifier = Modifier.weight(1f),
            style = TextStyle(color = Color(0xFF9AA5B1), fontSize = 13.sp)
        )
        BasicText(
            text = value,
            modifier = Modifier.weight(1f),
            style = TextStyle(color = Color(0xFFFFFFFF), fontSize = 13.sp, textAlign = TextAlign.End)
        )
    }
}

private fun needsActivityRecognitionPermission(s: Sensor): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        (s.type == Sensor.TYPE_STEP_COUNTER || s.type == Sensor.TYPE_STEP_DETECTOR)
}

private fun isTriggerType(t: Int) = t == Sensor.TYPE_SIGNIFICANT_MOTION || t == 24 /* GLANCE_GESTURE */ || t == 25 /* WAKE_GESTURE */ ||
    t == 26 /* PICK_UP_GESTURE */ || t == 17 /* SIGNIFICANT_MOTION */ || t == 30 /* MOTION_DETECT */ || t == 29 /* STATIONARY_DETECT */

private fun needsBodySensorsPermission(s: Sensor) = s.type == Sensor.TYPE_HEART_RATE || s.stringType.contains("heart", true) || s.stringType.contains("body", true)

private fun hasPermission(context: android.content.Context, perm: String) =
    ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

private fun hasActivityPermission(context: android.content.Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACTIVITY_RECOGNITION
    ) == PackageManager.PERMISSION_GRANTED
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
