package com.karthikhegde.meterpod.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Big readout numbers with an extruded 3D look: a stack of darker copies offset down-right forms the depth,
 * a soft shadow sits underneath, and the face has a light-to-base gradient so it looks lit from above.
 */
@Composable
fun Text3D(
    text: String,
    fontSize: Float,
    modifier: Modifier = Modifier,
    color: Color = MP.TextPrimary,
    fontWeight: FontWeight = FontWeight.Bold,
    fontFamily: FontFamily = FontFamily.SansSerif
) {
    val depth = (fontSize / 14f).coerceIn(1.2f, 5f)
    fun style(c: Color) = TextStyle(color = c, fontSize = fontSize.sp, fontWeight = fontWeight, fontFamily = fontFamily)
    val side = lerp(color, Color.Black, 0.62f)
    Box(modifier) {
        // soft cast shadow
        BasicText(text, Modifier.offsetDp(depth * 1.6f, depth * 2.2f), style = style(Color(0x66000000)))
        // extruded side wall
        for (i in 4 downTo 1) {
            val k = i / 4f
            BasicText(text, Modifier.offsetDp(depth * k, depth * k * 1.2f), style = style(side))
        }
        // lit face
        BasicText(
            text,
            style = TextStyle(
                brush = Brush.verticalGradient(listOf(lerp(color, Color.White, 0.55f), color, lerp(color, Color.Black, 0.18f))),
                fontSize = fontSize.sp, fontWeight = fontWeight, fontFamily = fontFamily
            )
        )
    }
}

private fun Modifier.offsetDp(x: Float, y: Float): Modifier = this.then(Modifier.offset(x.dp, y.dp))
