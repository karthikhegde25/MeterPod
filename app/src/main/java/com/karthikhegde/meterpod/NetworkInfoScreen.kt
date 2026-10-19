package com.karthikhegde.meterpod

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.core.content.ContextCompat
import com.karthikhegde.meterpod.compose.MP
import com.karthikhegde.meterpod.compose.RealButton
import com.karthikhegde.meterpod.compose.ResumePauseEffect
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface
import java.util.Collections

/** One line of the info list: a blue section heading or a label/value row. */
private sealed class NetworkInfoItem {
    class Section(val title: String) : NetworkInfoItem()
    class Row(val label: String, val value: String) : NetworkInfoItem()
}

/**
 * Reads and displays everything about the device's current network and
 * Wi-Fi connection that Android's public APIs expose: connection status,
 * Wi-Fi details (SSID/BSSID/frequency/link speed/signal/standard/MAC),
 * IP configuration, DHCP lease info, an estimated bandwidth, device Wi-Fi
 * capabilities, and all network interfaces on the device.
 *
 * SSID/BSSID are only populated with real values once ACCESS_FINE_LOCATION
 * is granted - that's an Android platform restriction, not a bug here.
 */
@Composable
fun NetworkInfoScreen() {
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(hasLocationPermission(context)) }
    var items by remember { mutableStateOf<List<NetworkInfoItem>>(emptyList()) }

    fun refresh() {
        permissionGranted = hasLocationPermission(context)
        items = populate(context)
    }

    val requestLocationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh() }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission(context)) {
            requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        refresh()
    }

    ResumePauseEffect(onResume = { refresh() }, onPause = { })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MP.Background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!permissionGranted) {
                Text(
                    text = "Grant location permission to see SSID / BSSID details.",
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF9AA5B1),
                    fontSize = 12.sp
                )
            }
            RealButton(
                text = "Refresh",
                onClick = { refresh() },
                modifier = Modifier.padding(start = 12.dp),
                primary = true,
                textColor = Color(0xFFFFFFFF),
                compact = true
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, bottom = 24.dp)
        ) {
            for (item in items) {
                when (item) {
                    is NetworkInfoItem.Section -> Text(
                        text = item.title,
                        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
                        color = Color(0xFF3A7BD5),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    is NetworkInfoItem.Row -> Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Text(
                            text = item.label,
                            modifier = Modifier.weight(1f),
                            color = Color(0xFF9AA5B1),
                            fontSize = 13.sp
                        )
                        Text(
                            text = item.value,
                            modifier = Modifier.weight(1.2f),
                            color = Color(0xFFFFFFFF),
                            fontSize = 13.sp,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

private fun hasLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

private fun populate(context: Context): List<NetworkInfoItem> {
    val out = mutableListOf<NetworkInfoItem>()
    fun addSection(title: String) { out.add(NetworkInfoItem.Section(title)) }
    fun addRow(label: String, value: String) { out.add(NetworkInfoItem.Row(label, value)) }

    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    val network: Network? = connectivityManager.activeNetwork
    val capabilities: NetworkCapabilities? =
        network?.let { connectivityManager.getNetworkCapabilities(it) }
    val linkProperties: LinkProperties? =
        network?.let { connectivityManager.getLinkProperties(it) }

    addSection("Connection Status")
    val isConnected = capabilities != null
    addRow("Status", if (isConnected) "Connected" else "Not connected")
    if (capabilities != null) {
        addRow("Transport", transportName(capabilities))
        addRow("Internet validated", if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) "Yes" else "No")
        addRow("Metered", if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) "Yes" else "No")
    }

    if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
        addSection("Wi-Fi Network")
        @Suppress("DEPRECATION")
        val info = wifiManager.connectionInfo
        if (info != null) {
            val ssid = info.ssid?.removePrefix("\"")?.removeSuffix("\"") ?: "Unknown"
            addRow("SSID", if (ssid == "<unknown ssid>") "Unavailable (grant location)" else ssid)
            addRow("BSSID", info.bssid ?: "Unavailable (grant location)")
            addRow("Frequency", "${info.frequency} MHz (${bandName(info.frequency)})")
            addRow("Link speed", "${info.linkSpeed} Mbps")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                addRow("RX link speed", "${info.rxLinkSpeedMbps} Mbps")
                addRow("TX link speed", "${info.txLinkSpeedMbps} Mbps")
            }
            addRow("Signal (RSSI)", "${info.rssi} dBm")
            @Suppress("DEPRECATION")
            val signalLevel = WifiManager.calculateSignalLevel(info.rssi, 5)
            addRow("Signal level", "$signalLevel / 4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                addRow("Wi-Fi standard", wifiStandardName(info.wifiStandard))
            }
            addRow("MAC address", info.macAddress ?: "Unavailable")
            @Suppress("DEPRECATION")
            addRow("Hidden network", if (info.hiddenSSID) "Yes" else "No")
        }
    }

    if (linkProperties != null) {
        addSection("IP Configuration")
        addRow("Interface", linkProperties.interfaceName ?: "Unknown")
        val addresses = linkProperties.linkAddresses.joinToString("\n") { it.address.hostAddress ?: "" }
        if (addresses.isNotBlank()) addRow("IP address(es)", addresses)
        val dns = linkProperties.dnsServers.joinToString(", ") { it.hostAddress ?: "" }
        if (dns.isNotBlank()) addRow("DNS servers", dns)
        linkProperties.domains?.let { if (it.isNotBlank()) addRow("Domains", it) }
    }

    @Suppress("DEPRECATION")
    val dhcpInfo = wifiManager.dhcpInfo
    if (dhcpInfo != null && capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
        addSection("DHCP Lease")
        addRow("Gateway", ipToString(dhcpInfo.gateway))
        addRow("Netmask", ipToString(dhcpInfo.netmask))
        addRow("DHCP server", ipToString(dhcpInfo.serverAddress))
        addRow("Lease duration", "${dhcpInfo.leaseDuration} s")
    }

    addSection("Device Wi-Fi Capabilities")
    addRow("Wi-Fi enabled", if (wifiManager.isWifiEnabled) "Yes" else "No")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        addRow("5 GHz support", if (wifiManager.is5GHzBandSupported) "Yes" else "No")
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        addRow("6 GHz support", if (wifiManager.is6GHzBandSupported) "Yes" else "No")
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val supportsWifi6 = wifiManager.isWifiStandardSupported(android.net.wifi.ScanResult.WIFI_STANDARD_11AX)
        addRow("Wi-Fi 6 (802.11ax) support", if (supportsWifi6) "Yes" else "No")
    }

    addSection("All Network Interfaces")
    try {
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
        for (iface in interfaces) {
            val addrs = Collections.list(iface.inetAddresses)
                .filter { !it.isLoopbackAddress }
                .mapNotNull { addr ->
                    when (addr) {
                        is Inet4Address -> addr.hostAddress
                        is Inet6Address -> addr.hostAddress
                        else -> null
                    }
                }
            if (addrs.isNotEmpty()) {
                addRow(iface.displayName, addrs.joinToString("\n"))
            }
        }
    } catch (e: Exception) {
        addRow("Interfaces", "Unavailable (${e.message})")
    }

    return out
}

private fun transportName(capabilities: NetworkCapabilities): String = when {
    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
    else -> "Unknown"
}

private fun bandName(frequencyMhz: Int): String = when {
    frequencyMhz in 2400..2500 -> "2.4 GHz"
    frequencyMhz in 4900..5900 -> "5 GHz"
    frequencyMhz in 5925..7125 -> "6 GHz"
    else -> "Unknown band"
}

private fun wifiStandardName(standard: Int): String = when (standard) {
    1 -> "802.11a/b/g (Legacy)"
    4 -> "802.11n (Wi-Fi 4)"
    5 -> "802.11ac (Wi-Fi 5)"
    6 -> "802.11ax (Wi-Fi 6)"
    7 -> "802.11ad"
    8 -> "802.11be (Wi-Fi 7)"
    else -> "Unknown"
}

private fun ipToString(ip: Int): String {
    return "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
}
