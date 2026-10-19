package com.karthikhegde.meterpod.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karthikhegde.meterpod.compose.MP
import com.karthikhegde.meterpod.compose.RealButton
import com.karthikhegde.meterpod.compose.RoundKey
import kotlin.math.abs

/** Small label + value box used for SCORE / BEST / TIME style read-outs. */
@Composable
fun ScoreBox(label: String, value: String, modifier: Modifier = Modifier, accent: Color = MP.Accent) {
    Column(
        modifier
            .background(MP.SurfaceElevated, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BasicText(label, style = TextStyle(color = MP.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp))
        BasicText(value, style = TextStyle(color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold))
    }
}

/** Dimmed panel drawn over a board for "Game over", "You win" and similar states. */
@Composable
fun BoxScope.GameOverlay(
    title: String,
    subtitle: String? = null,
    tint: Color = Color(0xCC0D1117),
    content: @Composable () -> Unit
) {
    Box(Modifier.matchParentSize().background(tint, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
            BasicText(title, style = TextStyle(color = MP.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
            if (subtitle != null) BasicText(subtitle, modifier = Modifier.padding(top = 6.dp),
                style = TextStyle(color = MP.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center))
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { content() }
        }
    }
}

/**
 * Detects swipes. With [repeatable] false one swipe is reported per gesture (2048); with true the
 * accumulated distance resets after each report so a long drag can steer repeatedly (Snake, Maze Munch).
 */
@Composable
fun Modifier.swipeInput(threshold: Dp = 22.dp, repeatable: Boolean = false, onSwipe: (Dir) -> Unit): Modifier {
    val cb by rememberUpdatedState(onSwipe)
    return this.pointerInput(repeatable) {
        var ax = 0f; var ay = 0f; var fired = false
        val th = threshold.toPx()
        detectDragGestures(
            onDragStart = { ax = 0f; ay = 0f; fired = false },
            onDragEnd = { },
            onDragCancel = { },
            onDrag = { change, amount ->
                change.consume()
                ax += amount.x; ay += amount.y
                if ((!fired || repeatable) && (abs(ax) > th || abs(ay) > th)) {
                    cb(if (abs(ax) > abs(ay)) (if (ax > 0) Dir.RIGHT else Dir.LEFT) else (if (ay > 0) Dir.DOWN else Dir.UP))
                    ax = 0f; ay = 0f; fired = true
                }
            }
        )
    }
}

/** On-screen direction pad (cross of four round keys). */
@Composable
fun DPad(onDir: (Dir) -> Unit, modifier: Modifier = Modifier, keySize: Dp = 56.dp) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PadKey("▲", keySize) { onDir(Dir.UP) }
        Row(horizontalArrangement = Arrangement.Center) {
            PadKey("◀", keySize) { onDir(Dir.LEFT) }
            Spacer(Modifier.width(keySize))
            PadKey("▶", keySize) { onDir(Dir.RIGHT) }
        }
        PadKey("▼", keySize) { onDir(Dir.DOWN) }
    }
}

@Composable
private fun PadKey(symbol: String, size: Dp, onClick: () -> Unit) {
    RoundKey(onClick = onClick, primary = false, modifier = Modifier.size(size).padding(3.dp)) {
        BasicText(symbol, modifier = Modifier.align(Alignment.Center),
            style = TextStyle(color = MP.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
fun SmallButton(text: String, onClick: () -> Unit, primary: Boolean = false, enabled: Boolean = true) {
    RealButton(text, onClick = onClick, compact = true, primary = primary, enabled = enabled)
}
