package com.karthikhegde.meterpod

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
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
class OdometerFragment : Fragment(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var rotationVectorSensor: Sensor? = null
    private var linearAccelSensor: Sensor? = null
    private var rawAccelerometer: Sensor? = null
    private var usingLinearAccelSensor = false

    private lateinit var timerText: TextView
    private lateinit var distanceText: TextView
    private lateinit var statusText: TextView
    private lateinit var startStopButton: Button
    private lateinit var speedGauge: SpeedGaugeView

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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_odometer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        timerText = view.findViewById(R.id.timerText)
        distanceText = view.findViewById(R.id.distanceText)
        statusText = view.findViewById(R.id.statusText)
        startStopButton = view.findViewById(R.id.startStopButton)
        speedGauge = view.findViewById(R.id.speedGauge)
        speedGauge.maxSpeedKmh = 20f // walking/running pace range, not vehicle speed

        sensorManager = requireContext().getSystemService(SensorManager::class.java)
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        rawAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        usingLinearAccelSensor = linearAccelSensor != null

        if (rotationVectorSensor == null || (linearAccelSensor == null && rawAccelerometer == null)) {
            startStopButton.isEnabled = false
            statusText.text = "Missing sensors needed for this tool on this device."
        }

        startStopButton.setOnClickListener {
            if (isRunning) stopTracking() else startTracking()
        }
        view.findViewById<Button>(R.id.resetButton).setOnClickListener {
            resetAll()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isRunning) stopTracking()
    }

    private fun startTracking() {
        isRunning = true
        startStopButton.text = "Stop"
        statusText.text = "Tracking… hold the phone still to reset drift (ZUPT)"
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
        startStopButton.text = "Start"
        statusText.text = "Stopped"
        accumulatedTimerMs += SystemClock.elapsedRealtime() - startElapsedRealtimeMs
        sensorManager.unregisterListener(this)
        timerHandler.removeCallbacks(timerRunnable)
        updateTimerDisplay()
        speedGauge.setSpeedKmh(0f)
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
        distanceText.text = "0.00 m"
        speedGauge.setSpeedKmh(0f)
        statusText.text = "Stopped"
    }

    private fun updateTimerDisplay() {
        val elapsedMs = accumulatedTimerMs + if (isRunning) SystemClock.elapsedRealtime() - startElapsedRealtimeMs else 0L
        val totalSeconds = elapsedMs / 1000.0
        val minutes = (totalSeconds / 60).toInt()
        val seconds = totalSeconds % 60
        timerText.text = String.format("%02d:%04.1f", minutes, seconds)
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
        speedGauge.setSpeedKmh(speedMs * 3.6f)
        distanceText.text = if (distanceMeters >= 1000.0) {
            String.format("%.3f km", distanceMeters / 1000.0)
        } else {
            String.format("%.2f m", distanceMeters)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // no-op
    }
}
