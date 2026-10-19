package com.karthikhegde.meterpod

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.karthikhegde.meterpod.compose.MP
import com.karthikhegde.meterpod.compose.Text3D
import com.karthikhegde.meterpod.compose.RealButton
import com.karthikhegde.meterpod.compose.ResumePauseEffect
import com.karthikhegde.meterpod.compose.toolTileBackground
import com.karthikhegde.meterpod.ui.SpeedGaugeView
import kotlin.math.sqrt

/**
 * Experimental accelerometer-only odometer: start a timer, and while it
 * runs, integrate acceleration -> velocity -> distance. See the in-app
 * warning text (and the conversation this was built from) for why this is
 * fundamentally less trustworthy than the GPS-based Speed tab — this is
 * kept here as a working, honestly-labeled demonstration of inertial dead
 * reckoning's real-world limits, not a precise measurement tool.
 *
 * The physics, in order:
 *
 * 1. TYPE_ROTATION_VECTOR gives us a rotation matrix (device -> world
 *    frame) every time it updates.
 * 2. TYPE_LINEAR_ACCELERATION gives gravity-removed acceleration, but in
 *    DEVICE coordinates - which rotate as you move the phone. Integrating
 *    device-frame readings directly would be physically meaningless the
 *    moment the phone's orientation changes, so each reading is rotated
 *    into the fixed world frame first via the current rotation matrix.
 * 3. Zero-velocity update (ZUPT): if the world-frame acceleration
 *    magnitude stays below a small threshold for ~0.3s, we force velocity
 *    to exactly zero. Without this, sensor bias alone makes a phone sitting
 *    motionless on a table appear to "drift" continuously - this is the
 *    single biggest practical improvement available without a much more
 *    sophisticated filter (e.g. a full Kalman filter with additional
 *    constraints), and even with it, genuine continuous motion still
 *    accumulates real error over time.
 * 4. velocity += worldAcceleration * dt (vector integration)
 *    speed = |velocity|
 *    distance += speed * dt
 */
private class OdometerController(private val sensorManager: SensorManager) : SensorEventListener {

    private val rotationVectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val linearAccelSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val rawAccelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val usingLinearAccelSensor = linearAccelSensor != null

    var timerText by mutableStateOf("00:00.0")
    var distanceText by mutableStateOf("0.00 m")
    var statusText by mutableStateOf("Stopped")
    var startStopLabel by mutableStateOf("Start")
    var startStopEnabled by mutableStateOf(true)

    var speedGauge: SpeedGaugeView? = null

    private val rotationMatrix = FloatArray(9)
    private var hasRotation = false

    // Manual gravity-removal fallback, only used if TYPE_LINEAR_ACCELERATION is unavailable.
    private val gravityEstimate = FloatArray(3)
    private var hasGravityEstimate = false
    private val gravityFilterAlpha = 0.05f

    private var isRunning = false
    private var lastAccelTimestampNs = 0L
    private var lowAccelDurationSec = 0f

    private val velocity = FloatArray(3)
    private var distanceMeters = 0.0

    private var startElapsedRealtimeMs = 0L
    private var accumulatedTimerMs = 0L

    private val zuptThreshold = 0.25f // m/s^2, below which we consider the phone "at rest"
    private val zuptTimeThresholdSec = 0.3f
    private val maxDtSec = 0.2f // clamps any unusually large gap between sensor events

    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            updateTimerDisplay()
            if (isRunning) timerHandler.postDelayed(this, 100L)
        }
    }

    init {
        if (rotationVectorSensor == null || (linearAccelSensor == null && rawAccelerometer == null)) {
            startStopEnabled = false
            statusText = "Missing sensors needed for this tool on this device."
        }
    }

    fun onStartStopClicked() {
        if (isRunning) stopTracking() else startTracking()
    }

    fun onResetClicked() {
        resetAll()
    }

    fun onPause() {
        if (isRunning) stopTracking()
    }

    private fun startTracking() {
        isRunning = true
        startStopLabel = "Stop"
        statusText = "Tracking… hold the phone still to reset drift (ZUPT)"
        startElapsedRealtimeMs = SystemClock.elapsedRealtime()
        lastAccelTimestampNs = 0L
        lowAccelDurationSec = 0f
        velocity[0] = 0f; velocity[1] = 0f; velocity[2] = 0f

        rotationVectorSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        if (usingLinearAccelSensor) {
            linearAccelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        } else {
            rawAccelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        }

        timerHandler.post(timerRunnable)
    }

    private fun stopTracking() {
        isRunning = false
        startStopLabel = "Start"
        statusText = "Stopped"
        accumulatedTimerMs += SystemClock.elapsedRealtime() - startElapsedRealtimeMs
        sensorManager.unregisterListener(this)
        timerHandler.removeCallbacks(timerRunnable)
        updateTimerDisplay()
        speedGauge?.setSpeedKmh(0f)
    }

    private fun resetAll() {
        if (isRunning) stopTracking()
        accumulatedTimerMs = 0L
        distanceMeters = 0.0
        velocity[0] = 0f; velocity[1] = 0f; velocity[2] = 0f
        hasRotation = false
        hasGravityEstimate = false
        lastAccelTimestampNs = 0L
        lowAccelDurationSec = 0f
        updateTimerDisplay()
        distanceText = "0.00 m"
        speedGauge?.setSpeedKmh(0f)
        statusText = "Stopped"
    }

    private fun updateTimerDisplay() {
        val elapsedMs = accumulatedTimerMs + if (isRunning) SystemClock.elapsedRealtime() - startElapsedRealtimeMs else 0L
        val totalSeconds = elapsedMs / 1000.0
        val minutes = (totalSeconds / 60).toInt()
        val seconds = totalSeconds % 60
        timerText = String.format("%02d:%04.1f", minutes, seconds)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                hasRotation = true
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                if (usingLinearAccelSensor) processAcceleration(event)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                if (!usingLinearAccelSensor) {
                    val linear = removeGravity(event.values)
                    processAcceleration(event, overrideValues = linear)
                }
            }
        }
    }

    /** Manual gravity removal for devices without TYPE_LINEAR_ACCELERATION: a slow low-pass tracks gravity. */
    private fun removeGravity(raw: FloatArray): FloatArray {
        if (!hasGravityEstimate) {
            gravityEstimate[0] = raw[0]; gravityEstimate[1] = raw[1]; gravityEstimate[2] = raw[2]
            hasGravityEstimate = true
        } else {
            gravityEstimate[0] = gravityFilterAlpha * raw[0] + (1 - gravityFilterAlpha) * gravityEstimate[0]
            gravityEstimate[1] = gravityFilterAlpha * raw[1] + (1 - gravityFilterAlpha) * gravityEstimate[1]
            gravityEstimate[2] = gravityFilterAlpha * raw[2] + (1 - gravityFilterAlpha) * gravityEstimate[2]
        }
        return floatArrayOf(
            raw[0] - gravityEstimate[0],
            raw[1] - gravityEstimate[1],
            raw[2] - gravityEstimate[2]
        )
    }

    private fun processAcceleration(event: SensorEvent, overrideValues: FloatArray? = null) {
        if (!hasRotation || !isRunning) return

        val nowNs = event.timestamp
        if (lastAccelTimestampNs == 0L) {
            lastAccelTimestampNs = nowNs
            return
        }
        var dt = (nowNs - lastAccelTimestampNs) / 1_000_000_000f
        lastAccelTimestampNs = nowNs
        if (dt <= 0f) return
        if (dt > maxDtSec) dt = maxDtSec

        val deviceAccel = overrideValues ?: event.values

        // Rotate device-frame acceleration into the fixed world frame using the
        // latest rotation matrix (row-major 3x3: worldVector = R * deviceVector).
        val worldX = rotationMatrix[0] * deviceAccel[0] + rotationMatrix[1] * deviceAccel[1] + rotationMatrix[2] * deviceAccel[2]
        val worldY = rotationMatrix[3] * deviceAccel[0] + rotationMatrix[4] * deviceAccel[1] + rotationMatrix[5] * deviceAccel[2]
        val worldZ = rotationMatrix[6] * deviceAccel[0] + rotationMatrix[7] * deviceAccel[1] + rotationMatrix[8] * deviceAccel[2]

        val accelMagnitude = sqrt(worldX * worldX + worldY * worldY + worldZ * worldZ)

        if (accelMagnitude < zuptThreshold) {
            lowAccelDurationSec += dt
        } else {
            lowAccelDurationSec = 0f
        }

        if (lowAccelDurationSec >= zuptTimeThresholdSec) {
            // Zero-velocity update: assume genuinely at rest, wipe out accumulated drift.
            velocity[0] = 0f; velocity[1] = 0f; velocity[2] = 0f
        } else {
            velocity[0] += worldX * dt
            velocity[1] += worldY * dt
            velocity[2] += worldZ * dt
        }

        val speedMs = sqrt(velocity[0] * velocity[0] + velocity[1] * velocity[1] + velocity[2] * velocity[2])
        distanceMeters += speedMs * dt

        updateReadouts(speedMs)
    }

    private fun updateReadouts(speedMs: Float) {
        speedGauge?.setSpeedKmh(speedMs * 3.6f)
        distanceText = if (distanceMeters >= 1000.0) {
            String.format("%.3f km", distanceMeters / 1000.0)
        } else {
            String.format("%.2f m", distanceMeters)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // no-op
    }
}

@Composable
fun OdometerScreen() {
    val context = LocalContext.current
    val controller = remember { OdometerController(context.getSystemService(SensorManager::class.java)) }

    // Fragment.onPause only (no onResume work): stop tracking when backgrounded / leaving the screen.
    ResumePauseEffect(
        onResume = { },
        onPause = { controller.onPause() }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MP.Background)
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "⚠ Experimental: accelerometer-only distance drifts significantly after short use. For real distance/speed tracking, use the GPS-based Speed tab instead.",
            modifier = Modifier
                .fillMaxWidth()
                .toolTileBackground()
                .padding(10.dp),
            color = MP.Warning,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )

        Text3D(text = controller.timerText, fontSize = 40f, modifier = Modifier.padding(top = 14.dp), color = MP.TextPrimary, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)

        Row(modifier = Modifier.padding(top = 10.dp)) {
            RealButton(
                text = controller.startStopLabel,
                onClick = { controller.onStartStopClicked() },
                modifier = Modifier.padding(end = 12.dp),
                primary = true,
                enabled = controller.startStopEnabled,
                textColor = MP.TextPrimary
            )
            RealButton(
                text = "Reset",
                onClick = { controller.onResetClicked() },
                primary = false,
                textColor = MP.TextPrimary
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    SpeedGaugeView(ctx).also {
                        it.maxSpeedKmh = 20f // walking/running pace range, not vehicle speed
                        controller.speedGauge = it
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(20f / 9f)
            )
        }

        Text3D(text = controller.distanceText, fontSize = 24f, modifier = Modifier.padding(top = 4.dp), color = MP.Accent)
        Text(
            text = controller.statusText,
            modifier = Modifier.padding(bottom = 8.dp),
            color = MP.TextSecondary,
            fontSize = 12.sp
        )
    }
}
