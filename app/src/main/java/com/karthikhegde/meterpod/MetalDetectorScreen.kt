package com.karthikhegde.meterpod

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.karthikhegde.meterpod.compose.MP
import com.karthikhegde.meterpod.compose.RealButton
import com.karthikhegde.meterpod.compose.ResumePauseEffect
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
private class MetalDetectorController(private val context: Context) : SensorEventListener {

    val sensorManager: SensorManager = context.getSystemService(SensorManager::class.java)
    val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    var statusText by mutableStateOf("NO METAL DETECTED")
    var fieldText by mutableStateOf("0 µT")
    var deltaText by mutableStateOf("+0 µT from baseline")
    var instructionsText by mutableStateOf(
        "Uses the magnetometer's raw field vector. Tap Calibrate away from metal, then sweep the phone flat and level near an object — the bar shows how strong the anomaly is, and the arrow gives a rough sense of which way to move the phone (approximate, not a precise bearing)."
    )

    var intensityBar: MetalDetectorView? = null
    var directionView: MetalDirectionView? = null

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

    init {
        if (magnetometer == null) {
            fieldText = "N/A"
            instructionsText = "No magnetometer sensor was found on this device."
        }
    }

    fun calibrate() {
        baseline[0] = smoothed[0]
        baseline[1] = smoothed[1]
        baseline[2] = smoothed[2]
        hasCalibrated = true
    }

    fun register() {
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun unregister() {
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

        fieldText = String.format("%.0f µT", currentMagnitude)
        deltaText = String.format("+%.0f µT from baseline", deltaMagnitude)
        intensityBar?.setIntensity(intensity)

        // Bearing of the anomaly within the phone's own flat (x-y/screen) plane.
        // 0 = toward the top of the phone, 90 = toward the right edge.
        val bearingDeg = normalizeDegrees(Math.toDegrees(atan2(dx.toDouble(), dy.toDouble())).toFloat())
        directionView?.setBearing(bearingDeg, deltaMagnitude >= directionActiveThreshold)

        statusText = when {
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

@Composable
fun MetalDetectorScreen() {
    val context = LocalContext.current
    val controller = remember { MetalDetectorController(context) }

    ResumePauseEffect(
        onResume = { controller.register() },
        onPause = { controller.unregister() }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MP.Background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BasicText(
            text = controller.statusText,
            modifier = Modifier.padding(top = 16.dp),
            style = TextStyle(color = MP.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        )
        BasicText(
            text = controller.fieldText,
            modifier = Modifier.padding(top = 8.dp),
            style = TextStyle(color = MP.Accent, fontSize = 48.sp, fontWeight = FontWeight.Bold)
        )
        BasicText(
            text = controller.deltaText,
            style = TextStyle(color = MP.TextSecondary, fontSize = 14.sp)
        )

        AndroidView(
            factory = { ctx -> MetalDetectorView(ctx).also { controller.intensityBar = it } },
            modifier = Modifier
                .padding(top = 28.dp)
                .fillMaxWidth()
                .height(28.dp)
        )

        BasicText(
            text = "Point this way (hold phone flat)",
            modifier = Modifier.padding(top = 16.dp),
            style = TextStyle(color = MP.TextSecondary, fontSize = 12.sp)
        )

        AndroidView(
            factory = { ctx -> MetalDirectionView(ctx).also { controller.directionView = it } },
            modifier = Modifier
                .padding(top = 8.dp)
                .size(120.dp)
        )

        RealButton(
            text = "Calibrate baseline",
            onClick = { controller.calibrate() },
            modifier = Modifier.padding(top = 12.dp),
            primary = true,
            textColor = MP.TextPrimary
        )

        Spacer(Modifier.weight(1f))

        BasicText(
            text = controller.instructionsText,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            style = TextStyle(color = MP.TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
        )
    }
}
