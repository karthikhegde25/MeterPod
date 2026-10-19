package com.karthikhegde.meterpod

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.core.content.ContextCompat
import com.karthikhegde.meterpod.compose.MP
import com.karthikhegde.meterpod.compose.Text3D
import com.karthikhegde.meterpod.compose.RealButton
import com.karthikhegde.meterpod.compose.ResumePauseEffect
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
private class SpeedController(private val context: Context) {

    var speedText by mutableStateOf("0")
    var speedUnitText by mutableStateOf("km/h  ·  0.0 mph")
    var maxSpeedText by mutableStateOf("0 km/h")
    var gpsStatusText by mutableStateOf("Waiting for GPS fix…")

    var speedGauge: SpeedGaugeView? = null

    private val locationManager: LocationManager = context.getSystemService(LocationManager::class.java)
    private var lastLocation: Location? = null
    private var lastDisplayedLocation: Location? = null
    private var maxSpeedKmh = 0f
    private var activeProviders = mutableSetOf<String>()

    private val locationListener = LocationListener { location ->
        onNewLocation(location)
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            startLocationUpdates()
        } else {
            gpsStatusText = "Location permission is required to measure speed."
        }
    }

    fun resetMax() {
        maxSpeedKmh = 0f
        maxSpeedText = "0 km/h"
    }

    fun onResume(requestLocationPermission: ActivityResultLauncher<String>) {
        if (!hasLocationPermission()) {
            gpsStatusText = "Tap to allow location access for speed tracking"
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
            gpsStatusText = "Location is turned off — enable it in device settings"
            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }

        startLocationUpdates()
    }

    fun onPause() {
        if (activeProviders.isNotEmpty()) {
            locationManager.removeUpdates(locationListener)
            activeProviders.clear()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
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
            gpsStatusText = "Location permission is required to measure speed."
            return
        }

        gpsStatusText = "Waiting for a location fix…"
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

        speedText = String.format("%.0f", speedKmh)
        speedUnitText = String.format("km/h  ·  %.1f mph", speedMph)
        speedGauge?.setSpeedKmh(speedKmh)

        if (speedKmh > maxSpeedKmh) {
            maxSpeedKmh = speedKmh
            maxSpeedText = String.format("%.0f km/h", maxSpeedKmh)
        }

        val providerLabel = if (location.provider == LocationManager.GPS_PROVIDER) "GPS" else "Network"
        val accuracy = location.accuracy
        gpsStatusText = if (accuracy <= 30f) {
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

@Composable
fun SpeedScreen() {
    val context = LocalContext.current
    val controller = remember { SpeedController(context) }

    val requestLocationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> controller.onPermissionResult(granted) }

    ResumePauseEffect(
        onResume = { controller.onResume(requestLocationPermission) },
        onPause = { controller.onPause() }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MP.Background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text3D(text = controller.speedText, fontSize = 64f, modifier = Modifier.padding(top = 8.dp), color = MP.TextPrimary)
        BasicText(
            text = controller.speedUnitText,
            style = TextStyle(color = MP.TextSecondary, fontSize = 14.sp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx -> SpeedGaugeView(ctx).also { controller.speedGauge = it } },
                modifier = Modifier.aspectRatio(16f / 9f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BasicText(
                    text = controller.maxSpeedText,
                    style = TextStyle(color = MP.Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                )
                BasicText(
                    text = "Max this session",
                    style = TextStyle(color = MP.TextSecondary, fontSize = 12.sp)
                )
            }
            RealButton(
                text = "Reset max",
                onClick = { controller.resetMax() },
                modifier = Modifier.align(Alignment.CenterVertically),
                primary = false,
                textColor = MP.TextPrimary,
                compact = true
            )
            // XML used weightSum=2 with a single weight=1 child: the other half stays empty.
            Spacer(Modifier.weight(1f))
        }

        BasicText(
            text = controller.gpsStatusText,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 0.dp, bottom = 8.dp),
            style = TextStyle(color = MP.TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
        )
    }
}
