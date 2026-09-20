package com.karthikhegde.meterpod.tools

import android.content.Context
import android.view.View
import androidx.fragment.app.Fragment
import com.karthikhegde.meterpod.AngleFragment
import com.karthikhegde.meterpod.CompassFragment
import com.karthikhegde.meterpod.CalculatorFragment
import com.karthikhegde.meterpod.CoinTossFragment
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
import com.karthikhegde.meterpod.ui.AngleDialView
import com.karthikhegde.meterpod.ui.BubbleLevelView
import com.karthikhegde.meterpod.ui.CoinView
import com.karthikhegde.meterpod.ui.CompassView
import com.karthikhegde.meterpod.ui.HsvPickerView
import com.karthikhegde.meterpod.ui.MetalDetectorView
import com.karthikhegde.meterpod.ui.ProtractorView
import com.karthikhegde.meterpod.ui.RulerView
import com.karthikhegde.meterpod.ui.SpeedGaugeView
import com.karthikhegde.meterpod.ui.StepHistoryChartView
import com.karthikhegde.meterpod.ui.WaveformView

/**
 * One entry in the home-screen tool grid.
 *
 * [createIconView] builds a small, non-interactive instance of the tool's
 * own custom view (no sensors attached, just a preset display value) to use
 * as its "logo" — reusing the real widget rather than drawing a separate
 * icon keeps every tile visually consistent with what the tool actually
 * looks like once opened.
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
            createIconView = { ctx -> AngleDialView(ctx).apply { setAngle(42f) } },
            createFragment = { AngleFragment() }
        ),
        ToolItem(
            id = "level",
            title = "Digital Level",
            createIconView = { ctx -> BubbleLevelView(ctx).apply { setTilt(4f, -3f) } },
            createFragment = { LevelFragment() }
        ),
        ToolItem(
            id = "compass",
            title = "Compass",
            createIconView = { ctx -> CompassView(ctx).apply { setHeading(35f) } },
            createFragment = { CompassFragment() }
        ),
        ToolItem(
            id = "metal_detector",
            title = "Metal Detector",
            createIconView = { ctx -> MetalDetectorView(ctx).apply { setIntensity(0.4f) } },
            createFragment = { MetalDetectorFragment() }
        ),
        ToolItem(
            id = "speed",
            title = "Speed",
            createIconView = { ctx -> SpeedGaugeView(ctx).apply { setSpeedKmh(70f) } },
            createFragment = { SpeedFragment() }
        ),
        ToolItem(
            id = "sound_level",
            title = "Sound Level",
            createIconView = { ctx ->
                WaveformView(ctx).apply {
                    // A static preset "waveform" shape for the tile - a plausible-looking
                    // sound wave, distinct at a glance from Metal Detector's plain bar.
                    val samples = FloatArray(60) { i ->
                        val t = i / 60f
                        (kotlin.math.sin(t * Math.PI * 6).toFloat() * (0.3f + 0.5f * kotlin.math.sin(t * Math.PI).toFloat()))
                    }
                    setSamples(samples)
                }
            },
            createFragment = { SoundLevelFragment() }
        ),
        ToolItem(
            id = "protractor",
            title = "Protractor",
            createIconView = { ctx -> ProtractorView(ctx).apply { isIconPreview = true; setAngle(60f) } },
            createFragment = { ProtractorFragment() }
        ),
        ToolItem(
            id = "ruler",
            title = "Ruler",
            createIconView = { ctx -> RulerView(ctx).apply { isIconPreview = true } },
            createFragment = { RulerFragment() }
        ),
        ToolItem(
            id = "pedometer",
            title = "Pedometer",
            createIconView = { ctx ->
                StepHistoryChartView(ctx).apply {
                    setEntries(
                        listOf(
                            StepHistoryChartView.DayEntry("M", 3200, false),
                            StepHistoryChartView.DayEntry("T", 5400, false),
                            StepHistoryChartView.DayEntry("W", 4100, false),
                            StepHistoryChartView.DayEntry("T", 6000, false),
                            StepHistoryChartView.DayEntry("F", 4800, true)
                        )
                    )
                }
            },
            createFragment = { PedometerFragment() }
        ),
        ToolItem(
            id = "odometer",
            title = "Odometer",
            createIconView = { ctx ->
                SpeedGaugeView(ctx).apply {
                    maxSpeedKmh = 20f
                    setSpeedKmh(8f)
                }
            },
            createFragment = { OdometerFragment() }
        ),
        ToolItem(
            id = "light_detector",
            title = "Light Detector",
            createIconView = { ctx ->
                android.widget.TextView(ctx).apply {
                    text = "☀"
                    textSize = 28f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(android.graphics.Color.parseColor("#FFC940"))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }
            },
            createFragment = { LightDetectorFragment() }
        ),
        ToolItem(
            id = "magnifier",
            title = "Magnifier",
            createIconView = { ctx -> HsvPickerView(ctx).apply { isIconPreview = true; setColor(android.graphics.Color.parseColor("#3A7BD5")) } },
            createFragment = { MagnifierFragment() }
        ),
        ToolItem(
            id = "unit_converter",
            title = "Unit Converter",
            createIconView = { ctx ->
                android.widget.TextView(ctx).apply {
                    text = "⇄"
                    textSize = 30f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(android.graphics.Color.parseColor("#3A7BD5"))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }
            },
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
            createIconView = { ctx ->
                android.widget.TextView(ctx).apply {
                    text = "π÷√"
                    textSize = 18f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(android.graphics.Color.parseColor("#3A7BD5"))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }
            },
            createFragment = { CalculatorFragment() }
        ),
        ToolItem(
            id = "word_counter",
            title = "Word & Character Counter",
            createIconView = { ctx ->
                android.widget.TextView(ctx).apply {
                    text = "Aa"
                    textSize = 22f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(android.graphics.Color.parseColor("#3A7BD5"))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }
            },
            createFragment = { WordCounterFragment() }
        ),
        ToolItem(
            id = "screen_info",
            title = "Screen Info",
            createIconView = { ctx ->
                android.widget.TextView(ctx).apply {
                    text = "▭"
                    textSize = 26f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(android.graphics.Color.parseColor("#3A7BD5"))
                }
            },
            createFragment = { ScreenInfoFragment() }
        ),
        ToolItem(
            id = "network_info",
            title = "Network & Wi‑Fi Info",
            createIconView = { ctx ->
                android.widget.TextView(ctx).apply {
                    text = "▂▄█"
                    textSize = 20f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(android.graphics.Color.parseColor("#3A7BD5"))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }
            },
            createFragment = { NetworkInfoFragment() }
        ),
        ToolItem(
            id = "qr_generator",
            title = "QR Generator",
            createIconView = { ctx ->
                android.widget.TextView(ctx).apply {
                    text = "⊞"
                    textSize = 26f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(android.graphics.Color.parseColor("#3A7BD5"))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }
            },
            createFragment = { QrGeneratorFragment() }
        ),
        ToolItem(
            id = "sensor_tester",
            title = "Sensor Tester",
            createIconView = { ctx ->
                android.widget.TextView(ctx).apply {
                    text = "◎"
                    textSize = 26f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(android.graphics.Color.parseColor("#3A7BD5"))
                }
            },
            createFragment = { SensorTesterListFragment() }
        )
    )
}
