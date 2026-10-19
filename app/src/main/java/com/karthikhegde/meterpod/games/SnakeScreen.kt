package com.karthikhegde.meterpod.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.karthikhegde.meterpod.compose.MP
import kotlinx.coroutines.delay

private const val SN_COLS = 15
private const val SN_ROWS = 19

/** Snake: swipe or use the pad, eat apples, grow, don't hit the walls or yourself. */
@Composable
fun SnakeScreen(onProgress: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val game = remember { SnakeGame(SN_COLS, SN_ROWS) }
    var tick by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var best by remember { mutableIntStateOf(GameStore.getInt(ctx, "best_snake")) }

    val alive = remember(tick) { game.alive }
    val started = remember(tick) { game.started }
    val score = remember(tick) { game.score }

    LaunchedEffect(tick) { onProgress(started && alive) }

    LaunchedEffect(started, paused, alive) {
        while (started && !paused && game.alive) {
            delay(game.intervalMs())
            game.step()
            if (game.score > best) { best = game.score; GameStore.putInt(ctx, "best_snake", best) }
            tick++
        }
    }

    fun steer(d: Dir) {
        if (!game.started) game.started = true
        game.turn(d.dc, d.dr)
        paused = false
        tick++
    }
    fun restart() { game.reset(); paused = false; tick++ }

    Column(Modifier.fillMaxSize().background(MP.Background).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ScoreBox("SCORE", score.toString(), Modifier.weight(1f))
            ScoreBox("BEST", best.toString(), Modifier.weight(1f), Color(0xFFFFC940))
            SmallButton(if (paused) "Resume" else "Pause", onClick = { if (started && alive) paused = !paused }, enabled = started && alive)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            val cell = minOf(maxWidth / SN_COLS, maxHeight / SN_ROWS)
            Box(Modifier.size(cell * SN_COLS, cell * SN_ROWS).swipeInput(threshold = 18.dp, repeatable = true) { steer(it) }) {
                Canvas(Modifier.fillMaxSize()) {
                    tick
                    val cs = size.width / SN_COLS
                    drawRoundRect(Color(0xFFBFE3A0), size = size, cornerRadius = CornerRadius(12.dp.toPx()))
                    for (r in 0 until SN_ROWS) for (c in 0 until SN_COLS) {
                        if ((r + c) % 2 == 0) drawRect(Color(0x26FFFFFF), Offset(c * cs, r * cs), Size(cs, cs))
                    }
                    // food (apple)
                    if (game.food >= 0) {
                        val fc = Offset((game.food % SN_COLS + 0.5f) * cs, (game.food / SN_COLS + 0.5f) * cs)
                        drawCircle(Color(0xFFE53935), cs * 0.38f, fc)
                        drawCircle(Color(0x55FFFFFF), cs * 0.11f, Offset(fc.x - cs * 0.13f, fc.y - cs * 0.13f))
                        drawLine(Color(0xFF6D4C41), Offset(fc.x, fc.y - cs * 0.34f), Offset(fc.x + cs * 0.05f, fc.y - cs * 0.5f), cs * 0.07f)
                        drawCircle(Color(0xFF66BB6A), cs * 0.1f, Offset(fc.x + cs * 0.14f, fc.y - cs * 0.42f))
                    }
                    // body, tail to head so the head is on top
                    val n = game.body.size
                    val cells = game.body.toList()
                    for (k in n - 1 downTo 0) {
                        val i = cells[k]
                        val t = if (n > 1) k / (n - 1f) else 0f
                        val col = lerp(Color(0xFF3A6BFF), Color(0xFF1B2F8A), t)
                        val x = (i % SN_COLS) * cs; val y = (i / SN_COLS) * cs
                        val inset = cs * (0.06f + 0.06f * t)
                        drawRoundRect(col, Offset(x + inset, y + inset), Size(cs - inset * 2, cs - inset * 2), CornerRadius(cs * 0.3f))
                        // bridge to next segment for a continuous look
                        if (k + 1 < n) {
                            val j = cells[k + 1]
                            val bx = ((i % SN_COLS) + (j % SN_COLS)) / 2f * cs + cs / 2; val by = ((i / SN_COLS) + (j / SN_COLS)) / 2f * cs + cs / 2
                            drawCircle(col, cs * 0.3f, Offset(bx, by))
                        }
                    }
                    if (n > 0) {
                        val h = cells[0]
                        val hx = (h % SN_COLS + 0.5f) * cs; val hy = (h / SN_COLS + 0.5f) * cs
                        val fx = game.dx.toFloat(); val fy = game.dy.toFloat()
                        val px = -fy; val py = fx
                        for (s in intArrayOf(-1, 1)) {
                            val ex = hx + fx * cs * 0.14f + px * s * cs * 0.2f; val ey = hy + fy * cs * 0.14f + py * s * cs * 0.2f
                            drawCircle(Color.White, cs * 0.14f, Offset(ex, ey))
                            drawCircle(Color(0xFF111111), cs * 0.07f, Offset(ex + fx * cs * 0.04f, ey + fy * cs * 0.04f))
                        }
                    }
                }
                if (!started) {
                    GameOverlay("Snake", "Swipe or press an arrow to start", tint = Color(0x99000000)) {}
                } else if (paused && alive) {
                    GameOverlay("Paused", null, tint = Color(0x99000000)) { SmallButton("Resume", onClick = { paused = false }, primary = true) }
                } else if (!alive) {
                    GameOverlay(if (game.won) "You filled the board!" else "Game over", "Score $score", tint = Color(0xAA000000)) {
                        SmallButton("Play again", onClick = { restart() }, primary = true)
                    }
                }
            }
        }
        DPad(onDir = { steer(it) }, keySize = 52.dp)
    }
}
