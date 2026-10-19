package com.karthikhegde.meterpod.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The "real, simulated key" look of the XML app (bg_app_button_*, bg_calc_key_*,
 * bg_round_button, bg_icon_button_3d, bg_tool_tile): a soft ground shadow, a dark
 * side wall, a glossy gradient face and a highlight on the face's upper half.
 * The face sinks onto the wall when pressed and flattens when disabled.
 *
 * Android's layer-list drawables can't be used from Compose, so the same layers
 * (same dp offsets, radii and colours) are drawn here with a Canvas.
 */

/** Colours of one key family. */
class KeyPalette(
    val wall: Color,
    val faceTop: Color,
    val faceBottom: Color,
    val stroke: Color,
    val facePressedTop: Color = faceTop,
    val facePressedBottom: Color = faceBottom,
    val strokePressed: Color = stroke,
    val wallPressed: Color = wall,
    val glossEnd: Color = Color(0x2EFFFFFF)
)

/** Layer offsets (dp) of one key family; [corner] == null draws an oval. */
class KeyGeometry(
    val corner: Dp?,
    val restWall: Dp,
    val restBottom: Dp,
    val pressTop: Dp,
    val pressBottom: Dp,
    val disabledWall: Dp,
    val disabledBottom: Dp,
    val glossSideInset: Dp = 3.dp
)

object KeyPalettes {
    private val disabledWall = Color(0xFF14181C)
    private val disabledTop = Color(0xFF2C3238)
    private val disabledBottom = Color(0xFF3A4048)
    private val disabledStroke = Color(0xFF2A3036)

    /** Face/wall used by every key when `enabled = false`. */
    val Disabled = KeyPalette(
        wall = disabledWall, faceTop = Color(0xFF34476F), faceBottom = Color(0xFF2E3F63), stroke = Color(0xFF34476F)
    )

    /** bg_app_button_primary / bg_pill_button / bg_calc_key_equals / bg_round_button */
    val Primary = KeyPalette(
        wall = Color(0xFF1D4ED8), faceTop = Color(0xFF4F8DFF), faceBottom = Color(0xFF2F6BF0), stroke = Color(0xFF8FB6FF)
    )

    /** bg_app_button_secondary / bg_icon_button_3d */
    val Secondary = KeyPalette(
        wall = Color(0xFF1D4ED8), faceTop = Color(0xFF4283F5), faceBottom = Color(0xFF2F68E0), stroke = Color(0xFF8FB6FF)
    )

    /** bg_calc_key_number */
    val CalcNumber = KeyPalette(
        wall = Color(0xFF1D4ED8), faceTop = Color(0xFF3F7BEF), faceBottom = Color(0xFF2D62D8), stroke = Color(0xFF8FB6FF)
    )

    /** bg_calc_key_function */
    val CalcFunction = KeyPalette(
        wall = Color(0xFF1D4ED8), faceTop = Color(0xFF5E9BFF), faceBottom = Color(0xFF4A86F2), stroke = Color(0xFFBBD4FF)
    )

    /** bg_calc_key_operator */
    val CalcOperator = KeyPalette(
        wall = Color(0xFF1D4ED8), faceTop = Color(0xFF2B6BFF), faceBottom = Color(0xFF1F55E0), stroke = Color(0xFF8FB6FF)
    )

    /** Destructive actions (Reset, Clear, Discard...): a red key with white text. */
    val Danger = KeyPalette(
        wall = Color(0xFF9B1C1C), faceTop = Color(0xFFFF5A5A), faceBottom = Color(0xFFE03131), stroke = Color(0xFFFFB3B3)
    )

    /** bg_tool_tile */
    val Tile = KeyPalette(
        wall = Color(0xFF060A16), faceTop = Color(0xFF15203E), faceBottom = Color(0xFF111A33), stroke = Color(0xFF2A3A62)
    )
}

object KeyGeometries {
    /** RealButton family: pill, radius 24dp. */
    val Button = KeyGeometry(
        corner = 14.dp, restWall = 2.5.dp, restBottom = 5.dp, pressTop = 3.dp, pressBottom = 2.dp,
        disabledWall = 1.5.dp, disabledBottom = 3.dp
    )

    /** Calculator keys: radius 16dp. */
    val Calc = KeyGeometry(
        corner = 18.dp, restWall = 2.dp, restBottom = 4.dp, pressTop = 2.4.dp, pressBottom = 1.6.dp,
        disabledWall = 1.2.dp, disabledBottom = 2.4.dp
    )

    /** Round +/- stepper and the toolbar icon key. */
    val Round = KeyGeometry(
        corner = null, restWall = 2.dp, restBottom = 4.dp, pressTop = 2.4.dp, pressBottom = 1.6.dp,
        disabledWall = 2.dp, disabledBottom = 4.dp, glossSideInset = 6.dp
    )

    /** Home tile (static look; sinks when pressed). */
    val Tile = KeyGeometry(
        corner = 22.dp, restWall = 1.5.dp, restBottom = 3.dp, pressTop = 2.dp, pressBottom = 1.dp,
        disabledWall = 1.5.dp, disabledBottom = 3.dp, glossSideInset = 3.dp
    )
}

private fun DrawScope.drawKey(
    geo: KeyGeometry,
    palette: KeyPalette,
    pressed: Boolean,
    enabled: Boolean
) {
    // Modern flat key: tonal gradient fill, hairline border, soft darkening when pressed.
    val pal = if (enabled) palette else KeyPalettes.Disabled
    val w = size.width
    val h = size.height
    val oval = geo.corner == null
    val r = (geo.corner ?: 0.dp).toPx().coerceAtMost(h / 2f)
    val brush = Brush.verticalGradient(listOf(pal.faceTop, pal.faceBottom))
    if (oval) drawOval(brush, Offset.Zero, Size(w, h))
    else drawRoundRect(brush, Offset.Zero, Size(w, h), CornerRadius(r))
    val sw = 1.dp.toPx()
    if (enabled) {
        val edge = Color(0x26FFFFFF)
        if (oval) drawOval(edge, Offset(sw / 2, sw / 2), Size(w - sw, h - sw), style = Stroke(sw))
        else drawRoundRect(edge, Offset(sw / 2, sw / 2), Size(w - sw, h - sw), CornerRadius(r - sw / 2), style = Stroke(sw))
    }
    if (pressed && enabled) {
        val dim = Color(0x33000000)
        if (oval) drawOval(dim, Offset.Zero, Size(w, h)) else drawRoundRect(dim, Offset.Zero, Size(w, h), CornerRadius(r))
    }
}

/**
 * Draws the 3D key look behind its content. Use with an explicit [pressed] state when the
 * caller owns the interaction source (see [Key3D] for the all-in-one version).
 */
fun Modifier.key3DBackground(
    geometry: KeyGeometry,
    palette: KeyPalette,
    pressed: Boolean = false,
    enabled: Boolean = true
): Modifier = this.drawBehind { drawKey(geometry, palette, pressed, enabled) }

/**
 * A clickable 3D key. The face sinks while pressed. Content is laid out inside [contentPadding]
 * (the XML buttons used extra bottom padding to centre the label on the raised face).
 */
@Composable
fun Key3D(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    geometry: KeyGeometry = KeyGeometries.Button,
    palette: KeyPalette = KeyPalettes.Secondary,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(),
    content: @Composable BoxScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .key3DBackground(geometry, palette, pressed, enabled)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * Ordinary action button (style RealButton + bg_app_button_primary/secondary):
 * min 88x46dp, label 14sp medium, padding 20/8/20/13dp.
 */
@Composable
fun RealButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    textColor: Color = Color.White,
    compact: Boolean = false,
    fontSize: Float = if (compact) 13f else 14f
) {
    val pad = if (compact) PaddingValues(16.dp, 8.dp, 16.dp, 8.dp) else PaddingValues(20.dp, 10.dp, 20.dp, 10.dp)
    val sizeMod = if (compact) Modifier else Modifier.defaultMinSize(minWidth = 88.dp, minHeight = 46.dp)
    // Buttons that used red text (Reset, Clear, Discard...) are now red buttons with white text.
    val danger = textColor == MP.Needle
    val labelColor = if (danger) Color.White else textColor
    Key3D(
        onClick = onClick,
        modifier = modifier.then(sizeMod),
        geometry = KeyGeometries.Button,
        palette = if (danger) KeyPalettes.Danger else if (primary) KeyPalettes.Primary else KeyPalettes.Secondary,
        enabled = enabled,
        contentPadding = pad
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = labelColor,
                fontSize = fontSize.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        )
    }
}

/** Calculator key families. */
enum class CalcKind { NUMBER, FUNCTION, OPERATOR, EQUALS }

/**
 * One calculator key (style CalcKey + bg_calc_key_*). Size it with Modifier.weight/fillMax*;
 * the label sits 4dp above centre like the XML (paddingBottom 4dp).
 */
@Composable
fun CalcKey(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: CalcKind = CalcKind.NUMBER,
    fontSize: Float = 16f,
    textColor: Color = Color.White
) {
    val palette = when (kind) {
        CalcKind.NUMBER -> KeyPalettes.CalcNumber
        CalcKind.FUNCTION -> KeyPalettes.CalcFunction
        CalcKind.OPERATOR -> KeyPalettes.CalcOperator
        CalcKind.EQUALS -> KeyPalettes.Primary
    }
    Key3D(
        onClick = onClick,
        modifier = modifier,
        geometry = KeyGeometries.Calc,
        palette = palette,
        contentPadding = PaddingValues()
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = textColor,
                fontSize = fontSize.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        )
    }
}

/**
 * Round key (bg_round_button = [primary] blue "+/-" stepper, bg_icon_button_3d = grey icon key).
 * Give it a fixed size via [modifier]; content is centred above the base (4dp bottom padding).
 */
@Composable
fun RoundKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(),
    content: @Composable BoxScope.() -> Unit
) {
    Key3D(
        onClick = onClick,
        modifier = modifier,
        geometry = KeyGeometries.Round,
        palette = if (primary) KeyPalettes.Primary else KeyPalettes.Secondary,
        enabled = enabled,
        contentPadding = contentPadding,
        content = content
    )
}
