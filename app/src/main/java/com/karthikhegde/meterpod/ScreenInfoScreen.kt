package com.karthikhegde.meterpod

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karthikhegde.meterpod.compose.MP
import kotlin.math.sqrt

/** One line of the info list: a blue section heading, or a label/value row. */
private sealed class InfoItem {
    data class Section(val title: String) : InfoItem()
    data class Row(val label: String, val value: String) : InfoItem()
}

/**
 * Reads and displays everything about the display that Android's APIs
 * expose to a normal app: resolution, density, refresh rate (current +
 * all supported), estimated physical size, HDR/color capabilities, and
 * configuration details (orientation, font scale, dark mode, etc).
 *
 * Some of this comes from different places depending on API level (the
 * Display class deprecated a few methods over time in favor of newer
 * WindowMetrics-based APIs) - each getter below picks the right source
 * for the running device rather than relying on a single deprecated path.
 */
@Composable
fun ScreenInfoScreen() {
    val context = LocalContext.current
    val view = LocalView.current
    val items = remember { buildInfoItems(context) }

    // Notch/cutout info needs the view to be attached and laid out first.
    var cutoutItems by remember { mutableStateOf<List<InfoItem>>(emptyList()) }
    LaunchedEffect(Unit) {
        cutoutItems = buildCutoutItems(view)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MP.Background)
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        (items + cutoutItems).forEach { item ->
            when (item) {
                is InfoItem.Section -> BasicText(
                    text = item.title,
                    modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
                    style = TextStyle(
                        color = Color(0xFF3A7BD5),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                is InfoItem.Row -> Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp)) {
                    BasicText(
                        text = item.label,
                        modifier = Modifier.weight(1f),
                        style = TextStyle(color = Color(0xFF9AA5B1), fontSize = 13.sp)
                    )
                    BasicText(
                        text = item.value,
                        modifier = Modifier.weight(1f),
                        style = TextStyle(
                            color = Color(0xFFFFFFFF),
                            fontSize = 13.sp,
                            textAlign = TextAlign.End
                        )
                    )
                }
            }
        }
    }
}

private fun buildInfoItems(context: Context): List<InfoItem> {
    val out = ArrayList<InfoItem>()
    fun addSection(title: String) { out.add(InfoItem.Section(title)) }
    fun addRow(label: String, value: String) { out.add(InfoItem.Row(label, value)) }

    val metrics = context.resources.displayMetrics
    val display = getDisplay(context)
    val configuration = context.resources.configuration

    addSection("Resolution & Density")
    addRow("Resolution", "${metrics.widthPixels} × ${metrics.heightPixels} px")
    display?.let {
        val realMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        it.getRealMetrics(realMetrics)
        if (realMetrics.widthPixels != metrics.widthPixels || realMetrics.heightPixels != metrics.heightPixels) {
            addRow("Real resolution (incl. system bars)", "${realMetrics.widthPixels} × ${realMetrics.heightPixels} px")
        }
    }
    addRow("Density", "${metrics.densityDpi} dpi (${densityBucketName(metrics.densityDpi)})")
    addRow("Density scale factor", String.format("%.2fx", metrics.density))
    addRow("Font scale", String.format("%.2fx", configuration.fontScale))

    addSection("Refresh Rate")
    if (display != null) {
        addRow("Current refresh rate", String.format("%.1f Hz", display.refreshRate))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val rates = display.supportedModes.map { it.refreshRate }.distinct().sorted()
            addRow("Supported refresh rates", rates.joinToString("  •  ") { String.format("%.0f Hz", it) })
        }
    } else {
        addRow("Refresh rate", "Unavailable")
    }

    addSection("Physical Size (estimated)")
    val widthInches = metrics.widthPixels / metrics.xdpi
    val heightInches = metrics.heightPixels / metrics.ydpi
    val diagonalInches = sqrt(widthInches * widthInches + heightInches * heightInches)
    addRow("Diagonal", String.format("%.1f in (estimated from reported DPI)", diagonalInches))
    addRow("xdpi / ydpi", String.format("%.1f / %.1f", metrics.xdpi, metrics.ydpi))

    addSection("Display Capabilities")
    if (display != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val hdrTypes = display.hdrCapabilities?.supportedHdrTypes
        addRow(
            "HDR support",
            if (hdrTypes == null || hdrTypes.isEmpty()) "Not supported" else hdrTypes.joinToString(", ") { hdrTypeName(it) }
        )
    }
    if (display != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        addRow("Wide color gamut", if (display.isWideColorGamut) "Supported" else "Not supported")
    }

    addSection("Configuration")
    addRow(
        "Orientation",
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) "Landscape" else "Portrait"
    )
    addRow("Screen size (dp)", "${configuration.screenWidthDp} × ${configuration.screenHeightDp} dp")
    addRow("Smallest width", "${configuration.smallestScreenWidthDp} dp")
    addRow("Screen size class", screenSizeClassName(configuration))
    val nightMode = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    addRow(
        "Dark mode",
        when (nightMode) {
            Configuration.UI_MODE_NIGHT_YES -> "On"
            Configuration.UI_MODE_NIGHT_NO -> "Off"
            else -> "Unknown"
        }
    )
    return out
}

private fun buildCutoutItems(view: android.view.View): List<InfoItem> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return emptyList()
    val insets = view.rootWindowInsets ?: return emptyList()
    val cutout = insets.displayCutout

    val out = ArrayList<InfoItem>()
    out.add(InfoItem.Section("Display Cutout / Notch"))
    if (cutout == null) {
        out.add(InfoItem.Row("Cutout", "None detected"))
    } else {
        out.add(
            InfoItem.Row(
                "Safe insets (px)",
                "L${cutout.safeInsetLeft} T${cutout.safeInsetTop} R${cutout.safeInsetRight} B${cutout.safeInsetBottom}"
            )
        )
    }
    return out
}

private fun getDisplay(context: Context): Display? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.display
    } else {
        @Suppress("DEPRECATION")
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        @Suppress("DEPRECATION")
        wm?.defaultDisplay
    }
}

private fun densityBucketName(densityDpi: Int): String = when {
    densityDpi <= DisplayMetrics.DENSITY_LOW -> "ldpi"
    densityDpi <= DisplayMetrics.DENSITY_MEDIUM -> "mdpi"
    densityDpi <= DisplayMetrics.DENSITY_HIGH -> "hdpi"
    densityDpi <= DisplayMetrics.DENSITY_XHIGH -> "xhdpi"
    densityDpi <= DisplayMetrics.DENSITY_XXHIGH -> "xxhdpi"
    else -> "xxxhdpi"
}

private fun screenSizeClassName(configuration: Configuration): String {
    return when (configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) {
        Configuration.SCREENLAYOUT_SIZE_SMALL -> "Small"
        Configuration.SCREENLAYOUT_SIZE_NORMAL -> "Normal"
        Configuration.SCREENLAYOUT_SIZE_LARGE -> "Large"
        Configuration.SCREENLAYOUT_SIZE_XLARGE -> "Extra large"
        else -> "Unknown"
    }
}

private fun hdrTypeName(type: Int): String = when (type) {
    Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
    Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
    Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
    else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && type == Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS) {
        "HDR10+"
    } else {
        "Type $type"
    }
}
