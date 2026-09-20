package com.karthikhegde.meterpod

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Display
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import kotlin.math.sqrt

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
class ScreenInfoFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_screen_info, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val infoContainer = view.findViewById<LinearLayout>(R.id.infoContainer)
        populate(infoContainer)
    }

    private fun populate(container: LinearLayout) {
        val context = requireContext()
        val metrics = context.resources.displayMetrics
        val display = getDisplay(context)
        val configuration = context.resources.configuration

        addSection(container, "Resolution & Density")
        addRow(container, "Resolution", "${metrics.widthPixels} × ${metrics.heightPixels} px")
        display?.let {
            val realMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            it.getRealMetrics(realMetrics)
            if (realMetrics.widthPixels != metrics.widthPixels || realMetrics.heightPixels != metrics.heightPixels) {
                addRow(container, "Real resolution (incl. system bars)", "${realMetrics.widthPixels} × ${realMetrics.heightPixels} px")
            }
        }
        addRow(container, "Density", "${metrics.densityDpi} dpi (${densityBucketName(metrics.densityDpi)})")
        addRow(container, "Density scale factor", String.format("%.2fx", metrics.density))
        addRow(container, "Font scale", String.format("%.2fx", configuration.fontScale))

        addSection(container, "Refresh Rate")
        if (display != null) {
            addRow(container, "Current refresh rate", String.format("%.1f Hz", display.refreshRate))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val rates = display.supportedModes.map { it.refreshRate }.distinct().sorted()
                addRow(container, "Supported refresh rates", rates.joinToString("  •  ") { String.format("%.0f Hz", it) })
            }
        } else {
            addRow(container, "Refresh rate", "Unavailable")
        }

        addSection(container, "Physical Size (estimated)")
        val widthInches = metrics.widthPixels / metrics.xdpi
        val heightInches = metrics.heightPixels / metrics.ydpi
        val diagonalInches = sqrt(widthInches * widthInches + heightInches * heightInches)
        addRow(container, "Diagonal", String.format("%.1f in (estimated from reported DPI)", diagonalInches))
        addRow(container, "xdpi / ydpi", String.format("%.1f / %.1f", metrics.xdpi, metrics.ydpi))

        addSection(container, "Display Capabilities")
        if (display != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val hdrTypes = display.hdrCapabilities?.supportedHdrTypes
            addRow(
                container, "HDR support",
                if (hdrTypes == null || hdrTypes.isEmpty()) "Not supported" else hdrTypes.joinToString(", ") { hdrTypeName(it) }
            )
        }
        if (display != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            addRow(container, "Wide color gamut", if (display.isWideColorGamut) "Supported" else "Not supported")
        }

        addSection(container, "Configuration")
        addRow(
            container, "Orientation",
            if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) "Landscape" else "Portrait"
        )
        addRow(container, "Screen size (dp)", "${configuration.screenWidthDp} × ${configuration.screenHeightDp} dp")
        addRow(container, "Smallest width", "${configuration.smallestScreenWidthDp} dp")
        addRow(container, "Screen size class", screenSizeClassName(configuration))
        val nightMode = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        addRow(
            container, "Dark mode",
            when (nightMode) {
                Configuration.UI_MODE_NIGHT_YES -> "On"
                Configuration.UI_MODE_NIGHT_NO -> "Off"
                else -> "Unknown"
            }
        )

        // Notch/cutout info needs the view to be attached and laid out first.
        container.post { addCutoutInfo(container) }
    }

    private fun addCutoutInfo(container: LinearLayout) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val insets = view?.rootWindowInsets ?: return
        val cutout = insets.displayCutout

        addSection(container, "Display Cutout / Notch")
        if (cutout == null) {
            addRow(container, "Cutout", "None detected")
        } else {
            addRow(
                container, "Safe insets (px)",
                "L${cutout.safeInsetLeft} T${cutout.safeInsetTop} R${cutout.safeInsetRight} B${cutout.safeInsetBottom}"
            )
        }
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
