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
 * (Location.speed, in m/s) whenever a provider has a fix with enough
 * confidence, which is exactly what you get from riding in a moving
 * vehicle. We convert to km/h and mph, smooth slightly, and track a
 * session-max reading like a real speedometer's peak-hold.
 *
 * If Location.hasSpeed() is ever false for a given fix (rare on GPS, more
 * common on network-based fixes), we fall back to computing speed from the
 * distance and time between consecutive fixes.
 *
 * Listens to BOTH GPS_PROVIDER and NETWORK_PROVIDER, whichever are
 * enabled. GPS_PROVIDER alone can take a long time for a first fix (or
 * never get one at all) without a clear sky view - indoors, in a car,
 * in an urban canyon - which is what made this screen sit on "Waiting
 * for GPS" forever. NETWORK_PROVIDER gets a rough fix almost instantly
 * from cell/Wi-Fi, so updates are shown as soon as either provider
 * reports, and a GPS fix (more accurate) simply replaces a network fix
 * once it arrives, via [isBetterLocation].
 */
class SpeedFragment : Fragment() {

    private lateinit var speedText: TextView
    private lateinit var speedUnitText: TextView
    private lateinit var speedGauge: SpeedGaugeView
    private lateinit var maxSpeedText: TextView
    private lateinit var gpsStatusText: TextView

    private lateinit var locationManager: LocationManager
    private var lastLocation: Location? = null
    private var lastDisplayedLocation: Location? = null
    private var maxSpeedKmh = 0f
    private var activeProviders = mutableSetOf<String>()

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

        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkEnabled = try {
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            false
        }

        if (!gpsEnabled && !networkEnabled) {
            gpsStatusText.text = "Location is turned off — enable it in device settings"
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }

        startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        if (activeProviders.isNotEmpty()) {
            locationManager.removeUpdates(locationListener)
            activeProviders.clear()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startLocationUpdates() {
        if (!hasLocationPermission()) return

        var requestedAny = false

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
            LocationManager.GPS_PROVIDER !in activeProviders
        ) {
            try {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L, // minimum 1s between updates
                    0f,    // no minimum distance filter
                    locationListener
                )
                activeProviders.add(LocationManager.GPS_PROVIDER)
                requestedAny = true
            } catch (e: SecurityException) {
                // Permission was revoked between the check and the call; ignore, handled below.
            }
        }

        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) &&
                LocationManager.NETWORK_PROVIDER !in activeProviders
            ) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1000L,
                    0f,
                    locationListener
                )
                activeProviders.add(LocationManager.NETWORK_PROVIDER)
                requestedAny = true
            }
        } catch (e: SecurityException) {
            // NETWORK_PROVIDER not available on this device/build; GPS alone still works.
        } catch (e: IllegalArgumentException) {
            // Some devices don't expose NETWORK_PROVIDER at all.
        }

        if (!requestedAny && activeProviders.isEmpty()) {
            gpsStatusText.text = "Location permission is required to measure speed."
            return
        }

        gpsStatusText.text = "Waiting for a location fix…"
    }

    private fun onNewLocation(location: Location) {
        lastLocation = location

        val previousDisplayed = lastDisplayedLocation
        if (previousDisplayed != null && !isBetterLocation(location, previousDisplayed)) {
            // A worse/stale fix from the other provider arrived after a better one - ignore it
            // for display purposes rather than letting the readout jump backwards in accuracy.
            return
        }
        lastDisplayedLocation = location

        val speedMs = if (location.hasSpeed()) {
            location.speed
        } else {
            computeFallbackSpeed(previousDisplayed, location)
        }

        val speedKmh = speedMs * 3.6f
        val speedMph = speedMs * 2.23694f

        speedText.text = String.format("%.0f", speedKmh)
        speedUnitText.text = String.format("km/h  ·  %.1f mph", speedMph)
        speedGauge.setSpeedKmh(speedKmh)

        if (speedKmh > maxSpeedKmh) {
            maxSpeedKmh = speedKmh
            maxSpeedText.text = String.format("%.0f km/h", maxSpeedKmh)
        }

        val providerLabel = if (location.provider == LocationManager.GPS_PROVIDER) "GPS" else "Network"
        val accuracy = location.accuracy
        gpsStatusText.text = if (accuracy <= 30f) {
            "Tracking ($providerLabel) · accuracy ±${accuracy.toInt()} m"
        } else {
            "Improving fix ($providerLabel)… accuracy ±${accuracy.toInt()} m"
        }
    }

    /** Used only if a fix doesn't directly report speed (rare for GPS_PROVIDER, common for NETWORK_PROVIDER). */
    private fun computeFallbackSpeed(previous: Location?, newLocation: Location): Float {
        if (previous == null) return 0f
        val elapsedSeconds = (newLocation.time - previous.time) / 1000f
        if (elapsedSeconds < 0.5f) return 0f
        val distanceMeters = previous.distanceTo(newLocation)
        return distanceMeters / elapsedSeconds
    }

    /**
     * Standard Android "is this new location better than the current one"
     * comparison (based on Google's own canonical location-updates sample):
     * newer fixes are generally preferred, but a significantly more
     * accurate fix from a different provider can win even if it's a
     * little older, and a GPS fix is given a slight edge over a network
     * fix of similar accuracy since it's the more trustworthy source.
     */
    private fun isBetterLocation(location: Location, currentBest: Location): Boolean {
        val timeDelta = location.time - currentBest.time
        val isSignificantlyNewer = timeDelta > 1000L * 60
        val isSignificantlyOlder = timeDelta < -1000L * 60
        val isNewer = timeDelta > 0

        if (isSignificantlyNewer) return true
        if (isSignificantlyOlder) return false

        val accuracyDelta = (location.accuracy - currentBest.accuracy).toInt()
        val isLessAccurate = accuracyDelta > 0
        val isMoreAccurate = accuracyDelta < 0
        val isSignificantlyLessAccurate = accuracyDelta > 200

        val isFromSameProvider = location.provider == currentBest.provider

        return when {
            isMoreAccurate -> true
            isNewer && !isLessAccurate -> true
            isNewer && !isSignificantlyLessAccurate && isFromSameProvider -> true
            else -> false
        }
    }
}
