package com.karthikhegde.meterpod.tools

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import com.karthikhegde.meterpod.FileConverterScreen
import com.karthikhegde.meterpod.MagneticFieldScreen
import com.karthikhegde.meterpod.RecorderScreen
import com.karthikhegde.meterpod.TimeScreen
import com.karthikhegde.meterpod.FileCreatorScreen
import com.karthikhegde.meterpod.AngleScreen
import com.karthikhegde.meterpod.CompassScreen
import com.karthikhegde.meterpod.CalculatorScreen
import com.karthikhegde.meterpod.CoinTossScreen
import com.karthikhegde.meterpod.LevelScreen
import com.karthikhegde.meterpod.LightDetectorScreen
import com.karthikhegde.meterpod.MagnifierScreen
import com.karthikhegde.meterpod.MetalDetectorScreen
import com.karthikhegde.meterpod.NetworkInfoScreen
import com.karthikhegde.meterpod.OdometerScreen
import com.karthikhegde.meterpod.PedometerScreen
import com.karthikhegde.meterpod.ProtractorScreen
import com.karthikhegde.meterpod.QrGeneratorScreen
import com.karthikhegde.meterpod.RulerScreen
import com.karthikhegde.meterpod.ScreenInfoScreen
import com.karthikhegde.meterpod.SoundLevelScreen
import com.karthikhegde.meterpod.SpeedScreen
import com.karthikhegde.meterpod.WordCounterScreen
import com.karthikhegde.meterpod.MorseScreen
import com.karthikhegde.meterpod.UuidScreen
import com.karthikhegde.meterpod.ui.CoinView
import com.karthikhegde.meterpod.ui.ToolGlyph
import com.karthikhegde.meterpod.ui.ToolGlyphView
import com.karthikhegde.meterpod.ui.CompassIconView
import com.karthikhegde.meterpod.ui.LevelIconView
import com.karthikhegde.meterpod.ui.PedometerIconView
import com.karthikhegde.meterpod.ui.FileIconView
import com.karthikhegde.meterpod.ui.FileConverterIconView
import com.karthikhegde.meterpod.ui.MagneticFieldIconView
import com.karthikhegde.meterpod.ui.AccelerometerIconView
import com.karthikhegde.meterpod.AccelerometerScreen
import com.karthikhegde.meterpod.ui.RecorderIconView
import com.karthikhegde.meterpod.ui.TimeIconView
import com.karthikhegde.meterpod.ui.GamesIconView
import com.karthikhegde.meterpod.games.GamesScreen
import com.karthikhegde.meterpod.ui.FileCreatorIconView

/**
 * One entry in the home-screen tool grid.
 *
 * [createIconView] builds a small, non-interactive "logo" view for the tile
 * (glossy glyphs from GlossyToolIcons.kt / ToolGlyphs.kt, plus the coin and
 * file icons); HomeScreen hosts it with AndroidView.
 *
 * [content] is the tool's Compose screen. Tools that need navigation arguments
 * (Unit Converter, Sensor Tester, File Viewer) have an empty [content]; AppRoot
 * hosts those directly.
 */
data class ToolItem(
    val id: String,
    val title: String,
    val createIconView: (Context) -> View,
    val content: @Composable () -> Unit
)

object ToolCatalog {
    fun all(): List<ToolItem> = listOf(
        ToolItem(
            id = "angle",
            title = "Angle",
            createIconView = { ctx -> ToolGlyphView(ctx, ToolGlyph.ANGLE) },
            content = { AngleScreen() }
        ),
        ToolItem(
            id = "level",
            title = "Digital Level",
            createIconView = { ctx -> LevelIconView(ctx) },
            content = { LevelScreen() }
        ),
        ToolItem(
            id = "compass",
            title = "Compass",
            createIconView = { ctx -> CompassIconView(ctx) },
            content = { CompassScreen() }
        ),
        ToolItem(
            id = "metal_detector",
            title = "Metal Detector",
            createIconView = { ctx -> ToolGlyphView(ctx, ToolGlyph.METAL) },
            content = { MetalDetectorScreen() }
        ),
        ToolItem(
            id = "speed",
            title = "Speed",
            createIconView = { ctx -> ToolGlyphView(ctx, ToolGlyph.SPEED) },
            content = { SpeedScreen() }
        ),
        ToolItem(
            id = "sound_level",
            title = "Sound Level",
            createIconView = { ctx -> ToolGlyphView(ctx, ToolGlyph.SOUND) },
            content = { SoundLevelScreen() }
        ),
        ToolItem(
            id = "time",
            title = "Time",
            createIconView = { ctx -> TimeIconView(ctx) },
            content = { TimeScreen() }
        ),
        ToolItem(
            id = "recorder",
            title = "Recorder",
            createIconView = { ctx -> RecorderIconView(ctx) },
            content = { RecorderScreen() }
        ),
        ToolItem(
            id = "protractor",
            title = "Protractor",
            createIconView = { ctx -> ToolGlyphView(ctx, ToolGlyph.PROTRACTOR) },
            content = { ProtractorScreen() }
        ),
        ToolItem(
            id = "ruler",
            title = "Ruler",
            createIconView = { ctx -> ToolGlyphView(ctx, ToolGlyph.RULER) },
            content = { RulerScreen() }
        ),
        ToolItem(
            id = "pedometer",
            title = "Pedometer",
            createIconView = { ctx -> PedometerIconView(ctx) },
            content = { PedometerScreen() }
        ),
        ToolItem(
            id = "odometer",
            title = "Odometer",
            createIconView = { ctx -> ToolGlyphView(ctx, ToolGlyph.ODOMETER) },
            content = { OdometerScreen() }
        ),
        ToolItem(
            id = "light_detector",
            title = "Light Detector",
            createIconView = { ctx -> ToolGlyphView(ctx, ToolGlyph.LIGHT) },
            content = { LightDetectorScreen() }
        ),
        ToolItem(
            id = "magnetic_field",
            title = "Magnetic Field",
            createIconView = { ctx -> MagneticFieldIconView(ctx) },
            content = { MagneticFieldScreen() }
        ),
        ToolItem(
            id = "accelerometer",
            title = "Accelerometer",
            createIconView = { ctx -> AccelerometerIconView(ctx) },
            content = { AccelerometerScreen() }
        ),
        ToolItem(
            id = "magnifier",
            title = "Magnifier",
            createIconView = { ctx -> ToolGlyphView(ctx, ToolGlyph.MAGNIFIER) },
            content = { MagnifierScreen() }
        ),
        ToolItem(
            id = "unit_converter",
            title = "Unit Converter",
            createIconView = { ctx -> ToolGlyphView(ctx, ToolGlyph.CONVERTER) },
            content = { /* needs navigation callbacks: wired in AppRoot */ }
        ),
        ToolItem(
            id = "coin_toss",
            title = "Coin Toss",
            createIconView = { ctx -> CoinView(ctx) },
            content = { CoinTossScreen() }
        ),
        ToolItem(
            id = "calculator",
            title = "Calculator",
            createIconView = { ctx -> ToolGlyphView(ctx, ToolGlyph.CALCULATOR) },
            content = { CalculatorScreen() }
        ),
        ToolItem(
            id = "word_counter",
            title = "Word & Character Counter",
            createIconView = { ctx -> ToolGlyphView(ctx, ToolGlyph.WORDS) },
            content = { WordCounterScreen() }
        ),
        ToolItem(
            id = "morse",
            title = "Morse Code",
            createIconView = { ctx -> ToolGlyphView(ctx, ToolGlyph.MORSE) },
            content = { MorseScreen() }
        ),
        ToolItem(
            id = "uuid",
            title = "UUID Generator",
            createIconView = { ctx -> ToolGlyphView(ctx, ToolGlyph.UUID) },
            content = { UuidScreen() }
        ),
        ToolItem(
            id = "screen_info",
            title = "Screen Info",
            createIconView = { ctx -> ToolGlyphView(ctx, ToolGlyph.SCREEN) },
            content = { ScreenInfoScreen() }
        ),
        ToolItem(
            id = "sensor_tester",
            title = "Sensor Tester",
            createIconView = { ctx -> ToolGlyphView(ctx, ToolGlyph.SENSOR) },
            content = { /* needs navigation callbacks: wired in AppRoot */ }
        ),
        ToolItem(
            id = "network_info",
            title = "Network & Wi‑Fi Info",
            createIconView = { ctx -> ToolGlyphView(ctx, ToolGlyph.NETWORK) },
            content = { NetworkInfoScreen() }
        ),
        ToolItem(
            id = "qr_generator",
            title = "QR Generator",
            createIconView = { ctx -> ToolGlyphView(ctx, ToolGlyph.QR) },
            content = { QrGeneratorScreen() }
        ),
        ToolItem(
            id = "games",
            title = "Games",
            createIconView = { ctx -> GamesIconView(ctx) },
            content = { GamesScreen() }
        ),
        ToolItem(
            id = "file_creator",
            title = "File Creator",
            createIconView = { ctx -> FileCreatorIconView(ctx) },
            content = { FileCreatorScreen() }
        ),
        ToolItem(
            id = "file_converter",
            title = "File Converter",
            createIconView = { ctx -> FileConverterIconView(ctx) },
            content = { FileConverterScreen() }
        ),
        ToolItem(
            id = "file_viewer",
            title = "File Viewer",
            createIconView = { ctx -> FileIconView(ctx) },
            content = { /* needs navigation callbacks: wired in AppRoot */ }
        )
    )
}
