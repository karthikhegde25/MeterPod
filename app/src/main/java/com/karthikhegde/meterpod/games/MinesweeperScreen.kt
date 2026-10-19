package com.karthikhegde.meterpod.games

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karthikhegde.meterpod.compose.MP
import kotlinx.coroutines.delay

private class MsLevel(val name: String, val rows: Int, val cols: Int, val mines: Int)
private val msLevels = listOf(MsLevel("Easy", 10, 8, 10), MsLevel("Medium", 14, 10, 24), MsLevel("Hard", 18, 12, 44))
private val numColors = intArrayOf(0, 0xFF4C8DFF.toInt(), 0xFF4CD964.toInt(), 0xFFFF5252.toInt(), 0xFFB07CFF.toInt(),
    0xFFFF9500.toInt(), 0xFF2CD3C8.toInt(), 0xFFEEEEEE.toInt(), 0xFF9AA5B1.toInt())

private fun fmtTime(s: Int) = "%d:%02d".format(s / 60, s % 60)

/** Minesweeper with dig / flag modes, long-press flagging, chording and best times per difficulty. */
@Composable
fun MinesweeperScreen(onProgress: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    var levelIdx by remember { mutableIntStateOf(0) }
    var game by remember { mutableStateOf(Minesweeper(msLevels[0].rows, msLevels[0].cols, msLevels[0].mines)) }
    var tick by remember { mutableIntStateOf(0) }
    var flagMode by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }
    var bestTick by remember { mutableIntStateOf(0) }
    var newBest by remember { mutableStateOf(false) }

    val g = game
    val started = remember(tick, g) { g.placed }
    val finished = remember(tick, g) { g.finished }
    val won = remember(tick, g) { g.won }
    val lost = remember(tick, g) { g.lost }
    val minesLeft = remember(tick, g) { g.minesLeft }

    LaunchedEffect(tick, g) { onProgress(started && !finished) }

    LaunchedEffect(started, finished, g) {
        if (started && !finished) {
            while (true) { delay(1000); seconds++ }
        }
    }
    LaunchedEffect(won) {
        if (won) {
            val key = "best_ms_$levelIdx"
            val old = GameStore.getInt(ctx, key, 0)
            if (old == 0 || seconds < old) { GameStore.putInt(ctx, key, seconds); newBest = true; bestTick++ }
        }
    }

    fun restart(idx: Int = levelIdx) {
        levelIdx = idx
        val l = msLevels[idx]
        game = Minesweeper(l.rows, l.cols, l.mines)
        seconds = 0; newBest = false; tick++
    }

    val bestTime = remember(levelIdx, bestTick) { GameStore.getInt(ctx, "best_ms_$levelIdx", 0) }

    Column(Modifier.fillMaxSize().background(MP.Background).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            msLevels.forEachIndexed { i, l -> SmallButton(l.name, onClick = { if (i != levelIdx) restart(i) }, primary = i == levelIdx) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            ScoreBox("MINES", minesLeft.toString(), accent = Color(0xFFFF5252))
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(MP.SurfaceElevated).clickable { restart() },
                contentAlignment = Alignment.Center
            ) { BasicText(if (lost) "😵" else if (won) "😎" else "🙂", style = TextStyle(fontSize = 28.sp)) }
            ScoreBox("TIME", fmtTime(seconds), accent = MP.Warning)
        }
        BasicText(
            if (bestTime > 0) "Best time (${msLevels[levelIdx].name}): ${fmtTime(bestTime)}" else "No best time yet",
            modifier = Modifier.padding(top = 6.dp), style = TextStyle(color = MP.TextSecondary, fontSize = 12.sp)
        )

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            val l = msLevels[levelIdx]
            val cellDp = minOf(maxWidth / l.cols, maxHeight / l.rows)
            Box(Modifier.size(cellDp * l.cols, cellDp * l.rows)) {
                val textPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER } }
                Canvas(
                    Modifier.fillMaxSize().pointerInput(g, flagMode) {
                        val cw = size.width / g.cols.toFloat(); val ch = size.height / g.rows.toFloat()
                        fun idx(o: Offset): Int {
                            val c = (o.x / cw).toInt().coerceIn(0, g.cols - 1); val r = (o.y / ch).toInt().coerceIn(0, g.rows - 1)
                            return r * g.cols + c
                        }
                        detectTapGestures(
                            onTap = { o ->
                                if (!g.finished) {
                                    val i = idx(o)
                                    when {
                                        g.state[i] == Minesweeper.REVEALED -> g.chord(i)
                                        flagMode -> g.toggleFlag(i)
                                        else -> g.reveal(i)
                                    }
                                    tick++
                                }
                            },
                            onLongPress = { o ->
                                if (!g.finished) { g.toggleFlag(idx(o)); tick++ }
                            }
                        )
                    }
                ) {
                    tick
                    val cw = size.width / g.cols; val ch = size.height / g.rows
                    val pad = cw * 0.04f
                    textPaint.textSize = cw * 0.62f
                    for (r in 0 until g.rows) for (c in 0 until g.cols) {
                        val i = r * g.cols + c
                        val x = c * cw; val y = r * ch
                        val st = g.state[i]
                        val tl = Offset(x + pad, y + pad); val sz = Size(cw - pad * 2, ch - pad * 2)
                        val cr = CornerRadius(cw * 0.14f)
                        val showMine = g.lost && g.mine[i]
                        if (st == Minesweeper.REVEALED || showMine) {
                            drawRoundRect(if (i == g.explodedAt) Color(0xFFB71C1C) else Color(0xFF151B22), tl, sz, cr)
                            if (g.mine[i]) {
                                val m = Offset(x + cw / 2, y + ch / 2); val rr = cw * 0.22f
                                drawCircle(Color(0xFF0A0A0A), rr, m)
                                for (a in 0 until 4) {
                                    val ang = Math.PI * a / 4
                                    val dx = (Math.cos(ang) * rr * 1.5).toFloat(); val dy = (Math.sin(ang) * rr * 1.5).toFloat()
                                    drawLine(Color(0xFF0A0A0A), Offset(m.x - dx, m.y - dy), Offset(m.x + dx, m.y + dy), cw * 0.06f)
                                }
                                drawCircle(Color.White, rr * 0.28f, Offset(m.x - rr * 0.3f, m.y - rr * 0.3f))
                            } else if (g.adj[i] > 0) {
                                textPaint.color = numColors[g.adj[i]]
                                drawIntoCanvas { it.nativeCanvas.drawText(g.adj[i].toString(), x + cw / 2, y + ch / 2 + textPaint.textSize * 0.35f, textPaint) }
                            }
                        } else {
                            // raised hidden cell
                            drawRoundRect(Color(0xFF3A4654), tl, sz, cr)
                            drawRoundRect(Color(0xFF55657A), tl, Size(sz.width, sz.height * 0.45f), cr)
                            drawRoundRect(Color(0xFF445366), Offset(tl.x + cw * 0.07f, tl.y + cw * 0.07f), Size(sz.width - cw * 0.14f, sz.height - cw * 0.14f), cr)
                            if (st == Minesweeper.FLAGGED) {
                                val px = x + cw * 0.42f
                                drawLine(Color(0xFFDDDDDD), Offset(px, y + ch * 0.22f), Offset(px, y + ch * 0.78f), cw * 0.07f)
                                val p = Path().apply {
                                    moveTo(px, y + ch * 0.2f); lineTo(px + cw * 0.34f, y + ch * 0.36f); lineTo(px, y + ch * 0.52f); close()
                                }
                                drawPath(p, Color(0xFFFF5252))
                                drawLine(Color(0xFFDDDDDD), Offset(x + cw * 0.28f, y + ch * 0.78f), Offset(x + cw * 0.62f, y + ch * 0.78f), cw * 0.08f)
                            }
                        }
                        if (g.lost && st == Minesweeper.FLAGGED && !g.mine[i]) {
                            drawLine(Color(0xFFFF5252), Offset(x + cw * 0.2f, y + ch * 0.2f), Offset(x + cw * 0.8f, y + ch * 0.8f), cw * 0.08f)
                            drawLine(Color(0xFFFF5252), Offset(x + cw * 0.8f, y + ch * 0.2f), Offset(x + cw * 0.2f, y + ch * 0.8f), cw * 0.08f)
                        }
                    }
                }
                if (finished) {
                    GameOverlay(
                        if (won) "Cleared!" else "Boom!",
                        if (won) (if (newBest) "New best time: ${fmtTime(seconds)}" else "Time ${fmtTime(seconds)}") else "You hit a mine",
                        tint = Color(0x990D1117)
                    ) { SmallButton("Play again", onClick = { restart() }, primary = true) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SmallButton("⛏ Dig", onClick = { flagMode = false }, primary = !flagMode)
            SmallButton("🚩 Flag", onClick = { flagMode = true }, primary = flagMode)
        }
        BasicText("Long-press flags. Tap a number to clear around it.", modifier = Modifier.padding(top = 6.dp),
            style = TextStyle(color = MP.TextSecondary, fontSize = 11.sp))
    }
}
