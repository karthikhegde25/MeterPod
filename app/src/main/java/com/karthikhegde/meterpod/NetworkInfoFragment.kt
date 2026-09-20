package com.karthikhegde.meterpod

import android.Manifest
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface
import java.net.SocketException

/**
 * Reads and displays everything about the device's network and Wi‑Fi state
 * that Android exposes to a normal app: which network is active and how
 * it's validated/metered, the connected Wi‑Fi network's SSID/BSSID/signal/
 * speed, full IP configuration (v4 + v6, DNS, gateway, private DNS), the
 * legacy DHCP lease, link bandwidth estimates, the device's Wi‑Fi hardware
 * capabilities, and a raw dump of every network interface on the device.
 *
 * Android has required location permission to read the connected SSID/BSSID
 * since Android 8 (a Wi‑Fi network name can reveal where you are), and
 * requires location services to be turned on since Android 9. The app
 * already asks for ACCESS_FINE_LOCATION for the Speed tool, and that same
 * grant unlocks the real values here — without it we show what Android
 * gives an unprivileged caller instead ("<unknown ssid>", masked BSSID).
 */
class NetworkInfoFragment : Fragment() {

    private lateinit var infoContainer: LinearLayout
    private lateinit var permissionHintText: TextView

    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> populate() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_network_info, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        infoContainer = view.findViewById(R.id.infoContainer)
        permissionHintText = view.findViewById(R.id.permissionHintText)
        view.findViewById<Button>(R.id.refreshButton).setOnClickListener { populate() }
    }

    override fun onResume() {
        super.onResume()
        if (!hasLocationPermission()) {
            requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        populate()
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun populate() {
        infoContainer.removeAllViews()
        val context = requireContext()
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val wifiManager = context.applicationContext
            .getSystemService(android.content.Context.WIFI_SERVICE) as? WifiManager

        permissionHintText.text = if (hasLocationPermission()) {
            "Live network and Wi‑Fi data from the system."
        } else {
            "Grant location access to see the connected network's name (SSID) and BSSID."
        }

        val activeNetwork: Network? = cm?.activeNetwork
        val capabilities: NetworkCapabilities? = activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val linkProperties: LinkProperties? = activeNetwork?.let { cm.getLinkProperties(it) }

        addConnectionSection(infoContainer, capabilities)
        addWifiSection(infoContainer, wifiManager, capabilities)
        addIpConfigSection(infoContainer, linkProperties)
        addDhcpSection(infoContainer, wifiManager)
        addBandwidthSection(infoContainer, capabilities)
        addWifiCapabilitiesSection(infoContainer, context, wifiManager)
        addInterfacesSection(infoContainer)
    }

    // ---------------------------------------------------------------------
    // Connection status
    // ---------------------------------------------------------------------

    private fun addConnectionSection(container: LinearLayout, caps: NetworkCapabilities?) {
        addSection(container, "Connection Status")
        if (caps == null) {
            addRow(container, "Active network", "None")
            return
        }
        addRow(container, "Transport", transportName(caps))
        addRow(container, "Has internet capability", boolText(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)))
        addRow(container, "Internet validated", boolText(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)))
        addRow(container, "Metered connection", boolText(!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)))
        addRow(container, "Behind VPN", boolText(caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)))
        addRow(container, "Captive portal", boolText(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)))
        addRow(container, "Roaming", boolText(!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            addRow(container, "Temporarily unmetered", boolText(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED)))
        }
    }

    private fun transportName(caps: NetworkCapabilities): String = when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi‑Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE) -> "Wi‑Fi Aware"
        else -> "Unknown"
    }

    // ---------------------------------------------------------------------
    // Wi-Fi network
    // ---------------------------------------------------------------------

    private fun addWifiSection(container: LinearLayout, wifiManager: WifiManager?, caps: NetworkCapabilities?) {
        addSection(container, "Wi‑Fi Adapter")
        if (wifiManager == null) {
            addRow(container, "Wi‑Fi", "Not available on this device")
            return
        }
        addRow(container, "Wi‑Fi enabled", boolText(wifiManager.isWifiEnabled))
        addRow(container, "Adapter state", wifiStateName(wifiManager.wifiState))

        val isWifiActive = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        if (!isWifiActive) {
            addRow(container, "Connected via Wi‑Fi", "No")
            return
        }

        @Suppress("DEPRECATION")
        val info: WifiInfo? = wifiManager.connectionInfo
        if (info == null) {
            addRow(container, "Wi‑Fi network", "Unavailable")
            return
        }

        addSection(container, "Wi‑Fi Network")
        addRow(container, "SSID", cleanSsid(info.ssid))
        addRow(container, "BSSID", info.bssid ?: "Unavailable")
        addRow(container, "Hidden network", boolText(info.hiddenSSID))
        addRow(container, "Network ID", info.networkId.toString())

        val freq = info.frequency
        addRow(container, "Frequency", "$freq MHz  (${frequencyBand(freq)}, channel ${frequencyToChannel(freq)})")
        addRow(container, "Link speed", "${info.linkSpeed} Mbps")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            addRow(container, "TX link speed", "${info.txLinkSpeedMbps} Mbps")
            addRow(container, "RX link speed", "${info.rxLinkSpeedMbps} Mbps")
        }
        addRow(container, "Signal strength (RSSI)", "${info.rssi} dBm")
        val signalLevel = WifiManager.calculateSignalLevel(info.rssi, 5)
        addRow(container, "Signal level", "$signalLevel / 4 bars")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            addRow(container, "Wi‑Fi standard", wifiStandardName(info.wifiStandard))
        }
        @Suppress("DEPRECATION")
        addRow(container, "IP address (Wi‑Fi)", intToIp(info.ipAddress))
        @Suppress("DEPRECATION")
        addRow(container, "MAC address", info.macAddress ?: "Unavailable")
        if (info.macAddress == "02:00:00:00:00:00") {
            addRow(container, "", "(Android masks the real MAC address from apps since Android 6)")
        }
    }

    private fun cleanSsid(rawSsid: String?): String {
        if (rawSsid == null) return "Unavailable"
        return rawSsid.removeSurrounding("\"")
    }

    private fun wifiStateName(state: Int): String = when (state) {
        WifiManager.WIFI_STATE_DISABLED -> "Disabled"
        WifiManager.WIFI_STATE_DISABLING -> "Disabling"
        WifiManager.WIFI_STATE_ENABLED -> "Enabled"
        WifiManager.WIFI_STATE_ENABLING -> "Enabling"
        else -> "Unknown"
    }

    private fun frequencyBand(freqMhz: Int): String = when {
        freqMhz in 2400..2500 -> "2.4 GHz"
        freqMhz in 4900..5900 -> "5 GHz"
        freqMhz in 5925..7125 -> "6 GHz"
        else -> "Unknown band"
    }

    private fun frequencyToChannel(freqMhz: Int): Int = when {
        freqMhz == 2484 -> 14
        freqMhz in 2412..2472 -> (freqMhz - 2407) / 5
        freqMhz in 5170..5825 -> (freqMhz - 5000) / 5
        freqMhz in 5955..7115 -> (freqMhz - 5950) / 5
        else -> -1
    }

    private fun wifiStandardName(standard: Int): String = when (standard) {
        1 -> "802.11 (legacy a/b/g)"
        4 -> "802.11n (Wi‑Fi 4)"
        5 -> "802.11ac (Wi‑Fi 5)"
        6 -> "802.11ax (Wi‑Fi 6)"
        7 -> "802.11ad"
        8 -> "802.11be (Wi‑Fi 7)"
        else -> "Unknown"
    }

    // ---------------------------------------------------------------------
    // IP configuration (works for whichever network is active, not just Wi-Fi)
    // ---------------------------------------------------------------------

    private fun addIpConfigSection(container: LinearLayout, link: LinkProperties?) {
        addSection(container, "IP Configuration")
        if (link == null) {
            addRow(container, "IP configuration", "Unavailable")
            return
        }
        addRow(container, "Interface", link.interfaceName ?: "Unavailable")

        val v4 = link.linkAddresses.filter { it.address is Inet4Address }
        val v6 = link.linkAddresses.filter { it.address is Inet6Address }
        if (v4.isEmpty()) {
            addRow(container, "IPv4 address", "None")
        } else {
            v4.forEach { addRow(container, "IPv4 address", "${it.address.hostAddress}/${it.prefixLength}") }
        }
        if (v6.isEmpty()) {
            addRow(container, "IPv6 address", "None")
        } else {
            v6.forEach { addRow(container, "IPv6 address", "${it.address.hostAddress}/${it.prefixLength}") }
        }

        val gateways = link.routes.mapNotNull { it.gateway?.hostAddress }.distinct()
        addRow(container, "Gateway", if (gateways.isEmpty()) "Unavailable" else gateways.joinToString(", "))

        val dns = link.dnsServers.mapNotNull { it.hostAddress }
        addRow(container, "DNS servers", if (dns.isEmpty()) "None" else dns.joinToString(", "))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && link.domains != null) {
            addRow(container, "Search domains", link.domains ?: "None")
        }
        addRow(container, "MTU", link.mtu.toString())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            addRow(container, "Private DNS active", boolText(link.isPrivateDnsActive))
            link.privateDnsServerName?.let { addRow(container, "Private DNS server", it) }
        }
    }

    // ---------------------------------------------------------------------
    // Legacy DHCP lease info (Wi-Fi only)
    // ---------------------------------------------------------------------

    private fun addDhcpSection(container: LinearLayout, wifiManager: WifiManager?) {
        @Suppress("DEPRECATION")
        val dhcp = wifiManager?.dhcpInfo ?: return
        addSection(container, "DHCP Lease")
        addRow(container, "IP address", intToIp(dhcp.ipAddress))
        addRow(container, "Gateway", intToIp(dhcp.gateway))
        addRow(container, "Netmask", intToIp(dhcp.netmask))
        addRow(container, "DNS 1", intToIp(dhcp.dns1))
        addRow(container, "DNS 2", intToIp(dhcp.dns2))
        addRow(container, "DHCP server", intToIp(dhcp.serverAddress))
        addRow(container, "Lease duration", "${dhcp.leaseDuration} s")
    }

    private fun intToIp(value: Int): String {
        if (value == 0) return "Unavailable"
        return Formatter.formatIpAddress(value)
    }

    // ---------------------------------------------------------------------
    // Bandwidth estimate
    // ---------------------------------------------------------------------

    private fun addBandwidthSection(container: LinearLayout, caps: NetworkCapabilities?) {
        addSection(container, "Bandwidth Estimate")
        if (caps == null) {
            addRow(container, "Bandwidth", "Unavailable")
            return
        }
        addRow(container, "Downstream", "${caps.linkDownstreamBandwidthKbps / 1000.0} Mbps (system estimate)")
        addRow(container, "Upstream", "${caps.linkUpstreamBandwidthKbps / 1000.0} Mbps (system estimate)")
    }

    // ---------------------------------------------------------------------
    // Device Wi-Fi hardware capabilities
    // ---------------------------------------------------------------------

    private fun addWifiCapabilitiesSection(container: LinearLayout, context: android.content.Context, wifiManager: WifiManager?) {
        addSection(container, "Device Wi‑Fi Capabilities")
        val pm = context.packageManager
        addRow(container, "Wi‑Fi hardware", boolText(pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_WIFI)))
        addRow(container, "Wi‑Fi Direct", boolText(pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_WIFI_DIRECT)))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            addRow(container, "Wi‑Fi Aware", boolText(pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_WIFI_AWARE)))
        }
        if (wifiManager == null) return
        addRow(container, "5 GHz band supported", boolText(safeCall { wifiManager.is5GHzBandSupported }))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            addRow(container, "Easy Connect (DPP) supported", boolText(safeCall { wifiManager.isEasyConnectSupported }))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            addRow(container, "6 GHz band supported", boolText(safeCall { wifiManager.is6GHzBandSupported }))
            addRow(container, "WPA3-SAE supported", boolText(safeCall { wifiManager.isWpa3SaeSupported }))
            addRow(container, "Enhanced Open (OWE) supported", boolText(safeCall { wifiManager.isEnhancedOpenSupported }))
            addRow(container, "WPA3 Suite-B supported", boolText(safeCall { wifiManager.isWpa3SuiteBSupported }))
            addRow(container, "STA + AP concurrency", boolText(safeCall { wifiManager.isStaApConcurrencySupported }))
        }
    }

    private inline fun safeCall(block: () -> Boolean): Boolean = try { block() } catch (t: Throwable) { false }

    // ---------------------------------------------------------------------
    // Raw interface dump
    // ---------------------------------------------------------------------

    private fun addInterfacesSection(container: LinearLayout) {
        addSection(container, "All Network Interfaces")
        val interfaces = try {
            val enumeration = NetworkInterface.getNetworkInterfaces()
            if (enumeration == null) null else java.util.Collections.list(enumeration)
        } catch (e: SocketException) {
            null
        }
        if (interfaces.isNullOrEmpty()) {
            addRow(container, "Interfaces", "Unavailable")
            return
        }
        for (iface in interfaces) {
            val addresses = java.util.Collections.list(iface.inetAddresses).mapNotNull { it.hostAddress }
            if (addresses.isEmpty() && !iface.isUp) continue
            addRow(
                container,
                iface.displayName,
                "${if (iface.isUp) "up" else "down"}${if (iface.isLoopback) ", loopback" else ""}, MTU ${iface.mtu}"
            )
            if (addresses.isNotEmpty()) {
                addRow(container, "", addresses.joinToString(", "))
            }
        }
    }

    // ---------------------------------------------------------------------
    // Shared row/section helpers (mirrors the style used by ScreenInfoFragment)
    // ---------------------------------------------------------------------

    private fun boolText(value: Boolean): String = if (value) "Yes" else "No"

    private fun addSection(container: LinearLayout, title: String) {
        val textView = TextView(requireContext()).apply {
            text = title
            setTextColor(android.graphics.Color.parseColor("#3A7BD5"))
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(20), 0, dp(6))
        }
        container.addView(textView)
    }

    private fun addRow(container: LinearLayout, label: String, value: String) {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(6))
        }
        val labelView = TextView(requireContext()).apply {
            text = label
            setTextColor(android.graphics.Color.parseColor("#9AA5B1"))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val valueView = TextView(requireContext()).apply {
            text = value
            setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
            textSize = 13f
            gravity = android.view.Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(labelView)
        row.addView(valueView)
        container.addView(row)
    }

    private fun dp(value: Int): Int {
        val density = resources.displayMetrics.density
        return (value * density).toInt()
    }
}
