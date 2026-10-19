package com.karthikhegde.meterpod.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/* ---------- Text styles shared by the screens (styles.xml: SectionLabel / FieldLabel) ---------- */

/** style SectionLabel: accent, 14sp bold, 20dp above / 8dp below. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text = text,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
        style = TextStyle(color = MP.Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    )
}

/** style FieldLabel: secondary text, 12sp, 12dp above / 4dp below. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text = text,
        modifier = modifier.padding(top = 12.dp, bottom = 4.dp),
        style = TextStyle(color = MP.TextSecondary, fontSize = 12.sp)
    )
}

/* ---------- Lifecycle ---------- */

/**
 * Replacement for Fragment.onResume()/onPause(): [onResume] runs when the screen is shown while the
 * activity is RESUMED (and again each time the app comes back), [onPause] when the app goes to the
 * background OR the screen leaves composition (user navigated away) - exactly the points where the
 * old fragments registered/unregistered their sensor listeners.
 */
@Composable
fun ResumePauseEffect(onResume: () -> Unit, onPause: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentResume by rememberUpdatedState(onResume)
    val currentPause by rememberUpdatedState(onPause)
    DisposableEffect(lifecycleOwner) {
        var active = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (!active) { active = true; currentResume() }
                Lifecycle.Event.ON_PAUSE -> if (active) { active = false; currentPause() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (active) { active = false; currentPause() }
        }
    }
}

/* ---------- Surfaces ---------- */

/** bg_tool_tile as a Modifier: the raised 3D card. Pass [pressed] for the sunken state. */
fun Modifier.toolTileBackground(pressed: Boolean = false): Modifier =
    this.key3DBackground(KeyGeometries.Tile, KeyPalettes.Tile, pressed = pressed)

/** A card with the bg_tool_tile look (used for stat panels etc). Non-clickable unless [onClick] given. */
@Composable
fun TileCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable BoxScope.() -> Unit
) {
    if (onClick == null) {
        Box(modifier.toolTileBackground().padding(contentPadding), content = content)
    } else {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        Box(
            modifier
                .toolTileBackground(pressed)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(contentPadding),
            content = content
        )
    }
}

/** bg_about_card: gradient card with a 1dp outline, radius 18dp. */
fun Modifier.aboutCardBackground(): Modifier = this
    .background(
        Brush.linearGradient(listOf(Color(0xFF16213F), Color(0xFF1C2D58))),
        RoundedCornerShape(22.dp)
    )
    .border(1.dp, Color(0xFF34497E), RoundedCornerShape(22.dp))

/** bg_tool_icon_backdrop: recessed round well behind a tile icon. */
fun Modifier.iconBackdrop(): Modifier = this.drawBehind {
    val sw1 = 1.5.dp.toPx()
    drawOval(Brush.verticalGradient(listOf(Color(0xFF2B4C9A), Color(0xFF16295A))))
    drawOval(
        Color(0xFF0A1330), Offset(sw1 / 2, sw1 / 2),
        Size(size.width - sw1, size.height - sw1), style = Stroke(sw1)
    )
    val inset = 2.dp.toPx()
    drawOval(
        Brush.verticalGradient(listOf(Color(0xFF3A5CA8), Color(0xFF223A78))),
        Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2)
    )
    val rim = 1.5.dp.toPx()
    val sw2 = 1.dp.toPx()
    drawOval(
        Color(0xFF5B80CC), Offset(rim + sw2 / 2, rim + sw2 / 2),
        Size(size.width - (rim + sw2 / 2) * 2, size.height - (rim + sw2 / 2) * 2), style = Stroke(sw2)
    )
}

/** bg_badge_sphere: glossy sphere. [base] is the sphere colour (default accent). */
fun Modifier.badgeSphere(base: Color = MP.Accent): Modifier = this.drawBehind {
    drawOval(base)
    val sw = 1.dp.toPx()
    drawOval(
        Color(0x66FFFFFF), Offset(sw / 2, sw / 2),
        Size(size.width - sw, size.height - sw), style = Stroke(sw)
    )
    val l = 3.dp.toPx(); val t = 2.dp.toPx(); val b = 5.dp.toPx()
    drawOval(
        Brush.verticalGradient(
            listOf(Color(0x00FFFFFF), Color(0x55FFFFFF)), startY = t, endY = size.height - b
        ),
        Offset(l, t), Size(size.width - l * 2, size.height - t - b)
    )
}

/** bg_color_swatch: round colour chip with a 2dp grey outline. */
fun Modifier.colorSwatch(fill: Color): Modifier = this
    .background(fill, CircleShape)
    .border(2.dp, MP.TextSecondary, CircleShape)

/* ---------- Inputs ---------- */

/**
 * Text field with the bg_input_field look: outlined at rest, glowing accent border + brighter
 * fill while focused. [contentPadding] is the EditText's android:padding.
 */
@Composable
fun MeterTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    textSize: Float = 15f,
    textColor: Color = MP.TextPrimary,
    fontFamily: FontFamily? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    enabled: Boolean = true,
    readOnly: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val style = TextStyle(color = textColor, fontSize = textSize.sp, fontFamily = fontFamily)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .drawBehind {
                val cr = CornerRadius(14.dp.toPx())
                drawRoundRect(if (focused) Color(0xFF111C3D) else Color(0xFF0E1630), cornerRadius = cr)
                val sw = (if (focused) 2.dp else 1.dp).toPx()
                drawRoundRect(
                    if (focused) MP.Accent else Color(0xFF2A3A62), Offset(sw / 2, sw / 2),
                    Size(size.width - sw, size.height - sw), CornerRadius(14.dp.toPx() - sw / 2), style = Stroke(sw)
                )
            }
            .padding(contentPadding),
        enabled = enabled,
        readOnly = readOnly,
        textStyle = style,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        cursorBrush = SolidColor(MP.Accent),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty() && hint != null) {
                    BasicText(hint, style = style.copy(color = MP.TextSecondary))
                }
                inner()
            }
        }
    )
}

/**
 * Replacement for the XML Spinner: a field showing the selected option that opens a dropdown.
 * Selection index semantics match Spinner (position in [options]).
 */
@Composable
fun DropdownField(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    textSize: Float = 15f
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF0E1630), RoundedCornerShape(12.dp))
                .border(1.5.dp, Color(0xFF3A4F82), RoundedCornerShape(12.dp))
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            BasicText(
                text = options.getOrNull(selectedIndex) ?: "",
                modifier = Modifier.align(Alignment.CenterStart),
                style = TextStyle(color = MP.TextPrimary, fontSize = textSize.sp)
            )
            BasicText(
                text = "▾",
                modifier = Modifier.align(Alignment.CenterEnd),
                style = TextStyle(color = MP.TextSecondary, fontSize = textSize.sp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MP.SurfaceElevated)
        ) {
            options.forEachIndexed { index, label ->
                DropdownMenuItem(
                    text = { Text(label, color = MP.TextPrimary) },
                    onClick = { expanded = false; onSelected(index) }
                )
            }
        }
    }
}

/** A thin 1dp divider line. */
@Composable
fun HairlineDivider(modifier: Modifier = Modifier, color: Color = MP.Background) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}
