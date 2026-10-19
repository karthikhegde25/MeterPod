package com.karthikhegde.meterpod.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karthikhegde.meterpod.compose.MP

private class TileSnap(val id: Int, val value: Int, val row: Int, val col: Int, val merged: Boolean, val isNew: Boolean)

private fun tileColor(v: Int): Color = when (v) {
    2 -> Color(0xFFEEE4DA); 4 -> Color(0xFFEDE0C8); 8 -> Color(0xFFF2B179); 16 -> Color(0xFFF59563)
    32 -> Color(0xFFF67C5F); 64 -> Color(0xFFF65E3B); 128 -> Color(0xFFEDCF72); 256 -> Color(0xFFEDCC61)
    512 -> Color(0xFFEDC850); 1024 -> Color(0xFFEDC53F); 2048 -> Color(0xFFEDC22E)
    else -> Color(0xFF3C3A32)
}

private fun tileText(v: Int): Color = if (v <= 4) Color(0xFF776E65) else Color(0xFFF9F6F2)

/** 2048: swipe to slide the tiles, equal tiles merge, reach 2048. The best score is stored on the device. */
@Composable
fun Game2048Screen(onProgress: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val game = remember { Game2048() }
    var tick by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(GameStore.getInt(ctx, "best_2048")) }
    var keepGoing by remember { mutableStateOf(false) }
    val tiles = remember(tick) { game.tiles.map { TileSnap(it.id, it.value, it.row, it.col, it.merged, it.isNew) } }

    LaunchedEffect(tick) { onProgress(game.score > 0 && !game.over) }

    fun doMove(d: Dir) {
        if (game.won && !keepGoing) return
        if (game.move(d)) {
            tick++
            if (game.score > best) { best = game.score; GameStore.putInt(ctx, "best_2048", best) }
        }
    }

    Column(Modifier.fillMaxSize().background(MP.Background).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.background(Color(0xFFEDC22E), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 8.dp)
            ) { BasicText("2048", style = TextStyle(color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)) }
            Box(Modifier.weight(1f))
            ScoreBox("SCORE", game.score.toString())
            ScoreBox("BEST", best.toString(), accent = Color(0xFFEDC22E))
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("Join the tiles and reach 2048!", modifier = Modifier.weight(1f),
                style = TextStyle(color = MP.TextSecondary, fontSize = 13.sp))
            SmallButton("New game", onClick = { game.reset(); keepGoing = false; tick++ }, primary = true)
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(top = 14.dp), contentAlignment = Alignment.Center) {
            val side = if (maxWidth < maxHeight) maxWidth else maxHeight
            val n = game.boardSize()
            val gap = side * 0.028f
            val cell = (side - gap * (n + 1)) / n
            Box(Modifier.size(side).swipeInput(repeatable = false) { doMove(it) }) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRoundRect(Color(0xFFBBADA0), size = size, cornerRadius = CornerRadius(12.dp.toPx()))
                    val g = gap.toPx(); val c = cell.toPx()
                    for (r in 0 until n) for (col in 0 until n) {
                        drawRoundRect(Color(0xFFCDC1B4), Offset(g + col * (c + g), g + r * (c + g)), Size(c, c), CornerRadius(c * 0.08f))
                    }
                }
                tiles.forEach { t ->
                    androidx.compose.runtime.key(t.id) {
                        TileView(t, cell, gap, tick)
                    }
                }
                if (game.over) {
                    GameOverlay("Game over!", "Score ${game.score}", tint = Color(0xB3EEE4DA)) {
                        SmallButton("Try again", onClick = { game.reset(); keepGoing = false; tick++ }, primary = true)
                    }
                } else if (game.won && !keepGoing) {
                    GameOverlay("You win!", "You made 2048. Keep going for a higher score?", tint = Color(0xB3EDC22E)) {
                        SmallButton("Keep going", onClick = { keepGoing = true; tick++ }, primary = true)
                        SmallButton("New game", onClick = { game.reset(); keepGoing = false; tick++ })
                    }
                }
            }
        }
        BasicText("Swipe anywhere on the board", modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            style = TextStyle(color = MP.TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center))
    }
}

@Composable
private fun TileView(t: TileSnap, cell: Dp, gap: Dp, stamp: Int) {
    val x by animateDpAsState(gap + (cell + gap) * t.col, tween(110), label = "x")
    val y by animateDpAsState(gap + (cell + gap) * t.row, tween(110), label = "y")
    val scale = remember { Animatable(if (t.isNew) 0f else 1f) }
    LaunchedEffect(stamp) {
        if (t.isNew) scale.animateTo(1f, tween(160))
        else if (t.merged) { scale.snapTo(1.2f); scale.animateTo(1f, tween(130)) }
    }
    val digits = t.value.toString().length
    val fs = cell.value * (if (digits <= 2) 0.46f else if (digits == 3) 0.37f else if (digits == 4) 0.30f else 0.24f)
    Box(
        Modifier
            .offset(x, y)
            .size(cell)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .background(tileColor(t.value), RoundedCornerShape(cell * 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        BasicText(t.value.toString(), style = TextStyle(color = tileText(t.value), fontSize = fs.sp, fontWeight = FontWeight.Bold))
    }
}
