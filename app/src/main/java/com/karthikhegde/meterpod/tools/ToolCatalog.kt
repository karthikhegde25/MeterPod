package com.karthikhegde.meterpod.tools

import android.content.Context
import android.view.View
import androidx.fragment.app.Fragment
import com.karthikhegde.meterpod.AngleFragment
import com.karthikhegde.meterpod.CompassFragment
import com.karthikhegde.meterpod.CalculatorFragment
import com.karthikhegde.meterpod.CoinTossFragment
import com.karthikhegde.meterpod.FileViewerFragment
import com.karthikhegde.meterpod.LevelFragment
import com.karthikhegde.meterpod.LightDetectorFragment
import com.karthikhegde.meterpod.MagnifierFragment
import com.karthikhegde.meterpod.MetalDetectorFragment
import com.karthikhegde.meterpod.NetworkInfoFragment
import com.karthikhegde.meterpod.OdometerFragment
import com.karthikhegde.meterpod.PedometerFragment
import com.karthikhegde.meterpod.ProtractorFragment
import com.karthikhegde.meterpod.QrGeneratorFragment
import com.karthikhegde.meterpod.RulerFragment
import com.karthikhegde.meterpod.ScreenInfoFragment
import com.karthikhegde.meterpod.SensorTesterListFragment
import com.karthikhegde.meterpod.SoundLevelFragment
import com.karthikhegde.meterpod.SpeedFragment
import com.karthikhegde.meterpod.UnitConverterHomeFragment
import com.karthikhegde.meterpod.WordCounterFragment
import com.karthikhegde.meterpod.ui.CoinView
import com.karthikhegde.meterpod.ui.ToolGlyph
import com.karthikhegde.meterpod.ui.ToolGlyphView
import com.karthikhegde.meterpod.ui.CompassIconView
import com.karthikhegde.meterpod.ui.LevelIconView
import com.karthikhegde.meterpod.ui.PedometerIconView
import com.karthikhegde.meterpod.ui.FileIconView

/**
 * One entry in the home-screen tool grid.
 *
 * [createIconView] builds a small, non-interactive "logo" view for the tile
 * (glossy glyphs from GlossyToolIcons.kt / ToolGlyphs.kt, plus the coin and
 * file icons). The real tool screens use their own views and are unaffected.
 */
data class ToolItem(
    val id: String,
    val title: String,
    val createIconView: (Context) -> View,
    val createFragment: () -> Fragment
)

object ToolCatalog {
    fun all(): List<ToolItem> = listOf(
        ToolItem(
            id = "angle",
            title = "Angle",
            createIconView = { ctx -> ToolGlyphView(ctx, ToolGlyph.ANGLE) },
            createFragment = { AngleFragment() }
        ),
        ToolItem(
            id = "level",
            title = "Digital Level",
            createIconView = { ctx -> LevelIconView(ctx) },
            createFragment = { LevelFragment() }
        ),
        ToolItem(
            id = "compass",
            title = "Compass",
            createIconView = { ctx -> CompassIconView(ctx) },
            createFragment = { CompassFragment() }
        ),
        ToolItem(
            id = "metal_detector",
            title = "Metal Detector",
            createIconView = { ctx -> ToolGlyphView(ctx, ToolGlyph.METAL) },
            createFragment = { MetalDetectorFragment() }
        ),
        ToolItem(
            id = "speed",
            title = "Speed",
            createIconView = { ctx -> ToolGlyphView(ctx, ToolGlyph.SPEED) },
            createFragment = { SpeedFragment() }
        ),
        ToolItem(
            id = "sound_level",
            title = "Sound Level",
            createIconView = { ctx -> ToolGlyphView(ctx, ToolGlyph.SOUND) },
            createFragment = { SoundLevelFragment() }
        ),
        ToolItem(
            id = "protractor",
            title = "Protractor",
            createIconView = { ctx -> ToolGlyphView(ctx, ToolGlyph.PROTRACTOR) },
            createFragment = { ProtractorFragment() }
        ),
        ToolItem(
            id = "ruler",
            title = "Ruler",
            createIconView = { ctx -> ToolGlyphView(ctx, ToolGlyph.RULER) },
            createFragment = { RulerFragment() }
        ),
        ToolItem(
            id = "pedometer",
            title = "Pedometer",
            createIconView = { ctx -> PedometerIconView(ctx) },
            createFragment = { PedometerFragment() }
        ),
        ToolItem(
            id = "odometer",
            title = "Odometer",
            createIconView = { ctx -> ToolGlyphView(ctx, ToolGlyph.ODOMETER) },
            createFragment = { OdometerFragment() }
        ),
        ToolItem(
            id = "light_detector",
            title = "Light Detector",
            createIconView = { ctx -> ToolGlyphView(ctx, ToolGlyph.LIGHT) },
            createFragment = { LightDetectorFragment() }
        ),
        ToolItem(
            id = "magnifier",
            title = "Magnifier",
            createIconView = { ctx -> ToolGlyphView(ctx, ToolGlyph.MAGNIFIER) },
            createFragment = { MagnifierFragment() }
        ),
        ToolItem(
            id = "unit_converter",
            title = "Unit Converter",
            createIconView = { ctx -> ToolGlyphView(ctx, ToolGlyph.CONVERTER) },
            createFragment = { UnitConverterHomeFragment() }
        ),
        ToolItem(
            id = "coin_toss",
            title = "Coin Toss",
            createIconView = { ctx -> CoinView(ctx) },
            createFragment = { CoinTossFragment() }
        ),
        ToolItem(
            id = "calculator",
            title = "Calculator",
            createIconView = { ctx -> ToolGlyphView(ctx, ToolGlyph.CALCULATOR) },
            createFragment = { CalculatorFragment() }
        ),
        ToolItem(
            id = "word_counter",
            title = "Word & Character Counter",
            createIconView = { ctx -> ToolGlyphView(ctx, ToolGlyph.WORDS) },
            createFragment = { WordCounterFragment() }
        ),
        ToolItem(
            id = "screen_info",
            title = "Screen Info",
            createIconView = { ctx -> ToolGlyphView(ctx, ToolGlyph.SCREEN) },
            createFragment = { ScreenInfoFragment() }
        ),
        ToolItem(
            id = "sensor_tester",
            title = "Sensor Tester",
            createIconView = { ctx -> ToolGlyphView(ctx, ToolGlyph.SENSOR) },
            createFragment = { SensorTesterListFragment() }
        ),
        ToolItem(
            id = "network_info",
            title = "Network & Wi‑Fi Info",
            createIconView = { ctx -> ToolGlyphView(ctx, ToolGlyph.NETWORK) },
            createFragment = { NetworkInfoFragment() }
        ),
        ToolItem(
            id = "qr_generator",
            title = "QR Generator",
            createIconView = { ctx -> ToolGlyphView(ctx, ToolGlyph.QR) },
            createFragment = { QrGeneratorFragment() }
        ),
        ToolItem(
            id = "file_viewer",
            title = "File Viewer",
            createIconView = { ctx -> FileIconView(ctx) },
            createFragment = { FileViewerFragment.newInstance() }
        )
    )
}
