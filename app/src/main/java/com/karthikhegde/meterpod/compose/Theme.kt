package com.karthikhegde.meterpod.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * App colours - 1:1 with res/values/colors.xml so the Compose UI matches the
 * old XML UI. (colors.xml is still there for the few places that need an
 * Android resource: the window background and the legacy Canvas views.)
 */
object MP {
    val Background = Color(0xFF080D1A)
    val Surface = Color(0xFF111A2E)
    val SurfaceElevated = Color(0xFF1A2542)
    val SurfaceOutline = Color(0xFF2A3A62)
    val Accent = Color(0xFF4C8DFF)
    val AccentDark = Color(0xFF2F6FE0)
    val AccentSoft = Color(0xFF22335A)
    val Needle = Color(0xFFFF5252)
    val TextPrimary = Color(0xFFF5F7FA)
    val TextSecondary = Color(0xFF93A1BC)
    val Warning = Color(0xFFFFC940)

    // Calculator key colours (res/values/colors.xml)
    val CalcKeyFill = Color(0xFF1F4797)
    val CalcKeyPressed = Color(0xFF173A7E)
    val CalcKeyNumberBorder = Color(0xFF6C97E6)
    val CalcKeyFunctionBorder = Color(0xFF86AEEA)
    val CalcKeyOperatorBorder = Color(0xFF4C8DFF)
}

/**
 * The app is dark-only (the XML theme hard-coded dark colours), so this
 * always supplies a dark scheme regardless of the system setting.
 */
@Composable
fun MeterPodTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = MP.Accent,
        onPrimary = Color.White,
        secondary = MP.Needle,
        background = MP.Background,
        onBackground = MP.TextPrimary,
        surface = MP.SurfaceElevated,
        onSurface = MP.TextPrimary,
        surfaceVariant = MP.Surface,
        onSurfaceVariant = MP.TextSecondary,
        outline = MP.SurfaceOutline
    )
    MaterialTheme(colorScheme = scheme) {
        // Material3's default body style adds a 24sp line height and 0.5sp letter spacing, which would
        // change the text metrics of the old XML TextViews (e.g. a 72sp number). Reset to plain text.
        ProvideTextStyle(TextStyle.Default, content)
    }
}
