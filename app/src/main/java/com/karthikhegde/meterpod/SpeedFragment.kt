package com.karthikhegde.meterpod

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.karthikhegde.meterpod.ui.SpeedGaugeView

/**
 * GPS-based speedometer. Android's Location API reports speed directly
 * (Location.speed, in m/s) whenever the GPS provider has a fix with enough
 * confidence, which is exactly what you get from riding in a moving
 * vehicle. We convert to km/h and mph, smooth slightly, and track a
 * session-max reading like a real speedometer's peak-hold.
 *
 * If Location.hasSpeed() is ever false for a given fix (rare on GPS, more
 * common on network-based fixes), we fall back to computing speed from the
 * distance and time between consecutive fixes.
 */
class SpeedFragment : Fragment() {

    private lateinit var speedText: TextView
    private lateinit var speedUnitText: TextView
    private lateinit var speedGauge: SpeedGaugeView
    private lateinit var maxSpeedText: TextView
    private lateinit var gpsStatusText: TextView

    private lateinit var locationManager: LocationManager
    private var lastLocation: Location? = null
    private var maxSpeedKmh = 0f
    private var isListening = false

    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startLocationUpdates()
        } else {
            gpsStatusText.text = "Location permission is required to measure speed."
        }
    }

    private val locationListener = LocationListener { location ->
        onNewLocation(location)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_speed, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        speedText = view.findViewById(R.id.speedText)
        speedUnitText = view.findViewById(R.id.speedUnitText)
        speedGauge = view.findViewById(R.id.speedGauge)
        maxSpeedText = view.findViewById(R.id.maxSpeedText)
        gpsStatusText = view.findViewById(R.id.gpsStatusText)

        locationManager = requireContext().getSystemService(LocationManager::class.java)

        view.findViewById<Button>(R.id.resetMaxButton).setOnClickListener {
            maxSpeedKmh = 0f
            maxSpeedText.text = "0 km/h"
        }
    }

    override fun onResume() {
        super.onResume()

        if (!hasLocationPermission()) {
            gpsStatusText.text = "Tap to allow location access for speed tracking"
            requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            gpsStatusText.text = "GPS is turned off — enable Location in device settings"
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }

        startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        if (isListening) {
            locationManager.removeUpdates(locationListener)
            isListening = false
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startLocationUpdates() {
        if (!hasLocationPermission() || isListening) return
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L, // minimum 1s between updates
                0f,    // no minimum distance filter
                locationListener
            )
            isListening = true
            gpsStatusText.text = "Waiting for GPS fix…"
        } catch (e: SecurityException) {
            gpsStatusText.text = "Location permission is required to measure speed."
        }
    }

    private fun onNewLocation(location: Location) {
        val speedMs = if (location.hasSpeed()) {
            location.speed
        } else {
            computeFallbackSpeed(location)
        }

        lastLocation = location

        val speedKmh = speedMs * 3.6f
        val speedMph = speedMs * 2.23694f

        speedText.text = String.format("%.0f", speedKmh)
        speedUnitText.text = String.format("km/h  ·  %.1f mph", speedMph)
        speedGauge.setSpeedKmh(speedKmh)

        if (speedKmh > maxSpeedKmh) {
            maxSpeedKmh = speedKmh
            maxSpeedText.text = String.format("%.0f km/h", maxSpeedKmh)
        }

        val accuracy = location.accuracy
        gpsStatusText.text = if (accuracy <= 30f) {
            "Tracking · accuracy ±${accuracy.toInt()} m"
        } else {
            "Improving GPS fix… accuracy ±${accuracy.toInt()} m"
        }
    }

    /** Used only if a fix doesn't directly report speed (rare for GPS_PROVIDER). */
    private fun computeFallbackSpeed(newLocation: Location): Float {
        val previous = lastLocation ?: return 0f
        val elapsedSeconds = (newLocation.time - previous.time) / 1000f
        if (elapsedSeconds < 0.5f) return 0f
        val distanceMeters = previous.distanceTo(newLocation)
        return distanceMeters / elapsedSeconds
    }
}
