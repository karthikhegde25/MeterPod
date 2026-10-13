package com.karthikhegde.meterpod

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
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
import java.util.Collections

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
class NetworkInfoFragment : Fragment() {

    private lateinit var infoContainer: LinearLayout
    private lateinit var permissionHintText: TextView

    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refresh()
        }

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

        view.findViewById<Button>(R.id.refreshButton).setOnClickListener { refresh() }

        if (!hasLocationPermission()) {
            requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun refresh() {
        val granted = hasLocationPermission()
        permissionHintText.visibility = if (granted) View.GONE else View.VISIBLE
        infoContainer.removeAllViews()
        populate(infoContainer)
    }

    private fun populate(container: LinearLayout) {
        val context = requireContext()
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        val network: Network? = connectivityManager.activeNetwork
        val capabilities: NetworkCapabilities? =
            network?.let { connectivityManager.getNetworkCapabilities(it) }
        val linkProperties: LinkProperties? =
            network?.let { connectivityManager.getLinkProperties(it) }

        addSection(container, "Connection Status")
        val isConnected = capabilities != null
        addRow(container, "Status", if (isConnected) "Connected" else "Not connected")
        if (capabilities != null) {
            addRow(container, "Transport", transportName(capabilities))
            addRow(container, "Internet validated", if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) "Yes" else "No")
            addRow(container, "Metered", if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) "Yes" else "No")
        }

        if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            addSection(container, "Wi-Fi Network")
            @Suppress("DEPRECATION")
            val info = wifiManager.connectionInfo
            if (info != null) {
                val ssid = info.ssid?.removePrefix("\"")?.removeSuffix("\"") ?: "Unknown"
                addRow(container, "SSID", if (ssid == "<unknown ssid>") "Unavailable (grant location)" else ssid)
                addRow(container, "BSSID", info.bssid ?: "Unavailable (grant location)")
                addRow(container, "Frequency", "${info.frequency} MHz (${bandName(info.frequency)})")
                addRow(container, "Link speed", "${info.linkSpeed} Mbps")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    addRow(container, "RX link speed", "${info.rxLinkSpeedMbps} Mbps")
                    addRow(container, "TX link speed", "${info.txLinkSpeedMbps} Mbps")
                }
                addRow(container, "Signal (RSSI)", "${info.rssi} dBm")
                @Suppress("DEPRECATION")
                val signalLevel = WifiManager.calculateSignalLevel(info.rssi, 5)
                addRow(container, "Signal level", "$signalLevel / 4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    addRow(container, "Wi-Fi standard", wifiStandardName(info.wifiStandard))
                }
                addRow(container, "MAC address", info.macAddress ?: "Unavailable")
                @Suppress("DEPRECATION")
                addRow(container, "Hidden network", if (info.hiddenSSID) "Yes" else "No")
            }
        }

        if (linkProperties != null) {
            addSection(container, "IP Configuration")
            addRow(container, "Interface", linkProperties.interfaceName ?: "Unknown")
            val addresses = linkProperties.linkAddresses.joinToString("\n") { it.address.hostAddress ?: "" }
            if (addresses.isNotBlank()) addRow(container, "IP address(es)", addresses)
            val dns = linkProperties.dnsServers.joinToString(", ") { it.hostAddress ?: "" }
            if (dns.isNotBlank()) addRow(container, "DNS servers", dns)
            linkProperties.domains?.let { if (it.isNotBlank()) addRow(container, "Domains", it) }
        }

        @Suppress("DEPRECATION")
        val dhcpInfo = wifiManager.dhcpInfo
        if (dhcpInfo != null && capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            addSection(container, "DHCP Lease")
            addRow(container, "Gateway", ipToString(dhcpInfo.gateway))
            addRow(container, "Netmask", ipToString(dhcpInfo.netmask))
            addRow(container, "DHCP server", ipToString(dhcpInfo.serverAddress))
            addRow(container, "Lease duration", "${dhcpInfo.leaseDuration} s")
        }

        addSection(container, "Device Wi-Fi Capabilities")
        addRow(container, "Wi-Fi enabled", if (wifiManager.isWifiEnabled) "Yes" else "No")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            addRow(container, "5 GHz support", if (wifiManager.is5GHzBandSupported) "Yes" else "No")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            addRow(container, "6 GHz support", if (wifiManager.is6GHzBandSupported) "Yes" else "No")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val supportsWifi6 = wifiManager.isWifiStandardSupported(android.net.wifi.ScanResult.WIFI_STANDARD_11AX)
            addRow(container, "Wi-Fi 6 (802.11ax) support", if (supportsWifi6) "Yes" else "No")
        }

        addSection(container, "All Network Interfaces")
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
                    addRow(container, iface.displayName, addrs.joinToString("\n"))
                }
            }
        } catch (e: Exception) {
            addRow(container, "Interfaces", "Unavailable (${e.message})")
        }
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

    private fun addSection(container: LinearLayout, title: String) {
        val textView = TextView(requireContext()).apply {
            text = title
            setTextColor(Color.parseColor("#3A7BD5"))
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
            setTextColor(Color.parseColor("#9AA5B1"))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val valueView = TextView(requireContext()).apply {
            text = value
            setTextColor(Color.parseColor("#FFFFFF"))
            textSize = 13f
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f)
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
