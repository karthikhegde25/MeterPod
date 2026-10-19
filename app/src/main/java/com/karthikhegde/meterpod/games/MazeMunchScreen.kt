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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.karthikhegde.meterpod.compose.MP
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sin

private val ghostColors = listOf(Color(0xFFFF4D4D), Color(0xFFFF8AD8), Color(0xFF3FE0E8), Color(0xFFFFA83A))
private val wallEdge = Color(0xFF3D6BFF)
private val wallFill = Color(0xFF0B1030)

/** Maze Munch: an original Pac-Man style game with four mazes, chasing monsters, power pellets and three lives. */
@Composable
fun MazeMunchScreen(onProgress: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val game = remember { MazeMunch() }
    var frame by remember { mutableLongStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var best by remember { mutableIntStateOf(GameStore.getInt(ctx, "best_maze")) }
    var started by remember { mutableStateOf(false) }
    var animT by remember { mutableStateOf(0f) }

    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                if (started) game.update(dt)
                animT += dt
                if (game.score > best) { best = game.score; GameStore.putInt(ctx, "best_maze", best) }
                frame++
            }
        }
    }

    val phase = game.phase
    val over = phase == MazePhase.GAME_OVER
    LaunchedEffect(started, over) { onProgress(started && !over) }

    fun steer(d: Dir) {
        if (!started) started = true
        paused = false
        game.setDirection(d.dc, d.dr)
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF05060F)).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        frame
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ScoreBox("SCORE", game.score.toString(), Modifier.weight(1f), Color(0xFFFFE14D))
            ScoreBox("LEVEL", game.level.toString(), Modifier.weight(1f))
            ScoreBox("BEST", best.toString(), Modifier.weight(1f), Color(0xFFFF8AD8))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(width = 92.dp, height = 24.dp)) {
                frame
                for (i in 0 until maxOf(0, game.lives)) {
                    drawPac(Offset(size.height / 2 + i * size.height * 1.3f, size.height / 2), size.height * 0.42f, 1, 0, 0.7f)
                }
            }
            Box(Modifier.weight(1f))
            SmallButton(if (paused) "Resume" else "Pause", onClick = { if (started && !over) paused = !paused }, enabled = started && !over)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            val cols = game.cols; val rows = game.rows
            val cell = minOf(maxWidth / cols, maxHeight / rows)
            Box(Modifier.size(cell * cols, cell * rows).swipeInput(threshold = 14.dp, repeatable = true) { steer(it) }) {
                Canvas(Modifier.fillMaxSize()) {
                    frame
                    drawMaze(game, animT)
                }
                when {
                    !started -> GameOverlay("Maze Munch", "Swipe or press an arrow to start", tint = Color(0x99000000)) {}
                    paused && !over -> GameOverlay("Paused", null, tint = Color(0x99000000)) {
                        SmallButton("Resume", onClick = { paused = false }, primary = true)
                    }
                    over -> GameOverlay("Game over", "Score ${game.score}", tint = Color(0xB3000000)) {
                        SmallButton("Play again", onClick = { game.restart(); started = false }, primary = true)
                    }
                    phase == MazePhase.READY -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(Modifier.padding(top = cell * 5)) {
                            androidx.compose.foundation.text.BasicText(
                                if (game.phaseTimer > 1.7f || game.levelClearedCount > 0 && game.level > 1) "LEVEL ${game.level}" else "READY!",
                                style = androidx.compose.ui.text.TextStyle(
                                    color = Color(0xFFFFE14D), fontSize = 20.mmSp(),
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }
        DPad(onDir = { steer(it) }, keySize = 48.dp)
    }
}

private fun Int.mmSp() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)

private fun DrawScope.drawPac(center: Offset, r: Float, fx: Int, fy: Int, mouth: Float) {
    val ang = Math.toDegrees(atan2(fy.toDouble(), fx.toDouble())).toFloat()
    val open = 38f * mouth
    drawArc(
        Color(0xFFFFE14D), ang + open, 360f - open * 2, true,
        Offset(center.x - r, center.y - r), Size(r * 2, r * 2)
    )
}

private fun DrawScope.drawMaze(g: MazeMunch, t: Float) {
    val cs = size.width / g.cols
    val cols = g.cols; val rows = g.rows
    fun wall(c: Int, r: Int): Boolean {
        if (r < 0 || r >= rows) return true
        if (c < 0 || c >= cols) return false      // tunnel openings
        return g.cell(c, r) == '#'
    }
    val th = cs * 0.09f
    for (r in 0 until rows) for (c in 0 until cols) {
        val ch = g.cell(c, r)
        val x = c * cs; val y = r * cs
        if (ch == '#') {
            drawRect(wallFill, Offset(x, y), Size(cs, cs))
            val e = wallEdge
            if (!wall(c, r - 1)) drawLine(e, Offset(x, y + th / 2), Offset(x + cs, y + th / 2), th)
            if (!wall(c, r + 1)) drawLine(e, Offset(x, y + cs - th / 2), Offset(x + cs, y + cs - th / 2), th)
            if (!wall(c - 1, r)) drawLine(e, Offset(x + th / 2, y), Offset(x + th / 2, y + cs), th)
            if (!wall(c + 1, r)) drawLine(e, Offset(x + cs - th / 2, y), Offset(x + cs - th / 2, y + cs), th)
        } else if (ch == '=') {
            drawRect(Color(0xFFFF8AD8), Offset(x, y + cs * 0.42f), Size(cs, cs * 0.16f))
        }
        if (g.dots[r][c]) drawCircle(Color(0xFFFFDDB0), cs * 0.1f, Offset(x + cs / 2, y + cs / 2))
        if (g.pellets[r][c]) {
            val pulse = if (((t * 3f).toInt()) % 2 == 0) 0.3f else 0.22f
            drawCircle(Color(0xFFFFF3C4), cs * pulse, Offset(x + cs / 2, y + cs / 2))
        }
    }
    // player
    val dying = g.phase == MazePhase.DYING
    val px = (g.player.x + 0.5f) * cs; val py = (g.player.y + 0.5f) * cs
    val pr = cs * 0.42f
    if (dying) {
        val k = (1f - g.phaseTimer / 1.2f).coerceIn(0f, 1f)
        if (k < 1f) {
            val sweep = 360f * (1f - k)
            drawArc(Color(0xFFFFE14D), -90f + (360f - sweep) / 2f * 0 + k * 180f, sweep, true, Offset(px - pr, py - pr), Size(pr * 2, pr * 2))
        }
    } else {
        val moving = g.player.dx != 0 || g.player.dy != 0
        val mouth = if (moving && g.phase == MazePhase.PLAYING) abs(sin(t * 16f)) else 0.45f
        drawPac(Offset(px, py), pr, g.faceDx, g.faceDy, mouth.coerceIn(0.05f, 1f))
    }
    // ghosts
    if (!dying) for (gh in g.ghosts) {
        val gx = (gh.x + 0.5f) * cs; val gy = (gh.y + 0.5f) * cs
        val flash = g.scaredLeft in 0.01f..2f && ((t * 6).toInt() % 2 == 0)
        val body = when (gh.state) {
            GhostState.SCARED -> if (flash) Color(0xFFFFFFFF) else Color(0xFF2F4BFF)
            else -> ghostColors[gh.kind % 4]
        }
        drawGhost(gx, gy, cs * 0.44f, body, gh.state, gh.dx, gh.dy, t, flash)
    }
}

private fun DrawScope.drawGhost(cx: Float, cy: Float, r: Float, color: Color, st: GhostState, dx: Int, dy: Int, t: Float, flash: Boolean) {
    if (st != GhostState.EYES) {
        val p = Path()
        p.moveTo(cx - r, cy + r)
        p.lineTo(cx - r, cy)
        p.arcTo(Rect(cx - r, cy - r, cx + r, cy + r), 180f, 180f, false)
        p.lineTo(cx + r, cy + r)
        val n = 3; val w = 2 * r / n
        val phase = if (((t * 8).toInt() % 2) == 0) 0f else 1f
        for (i in 0 until n) {
            val x1 = cx + r - w * i
            val up = if ((i + phase.toInt()) % 2 == 0) -r * 0.28f else 0f
            p.lineTo(x1 - w / 2, cy + r + up)
            p.lineTo(x1 - w, cy + r)
        }
        p.close()
        drawPath(p, color)
    }
    if (st == GhostState.SCARED) {
        val fc = if (flash) Color(0xFFFF4D4D) else Color(0xFFFFE0C0)
        drawCircle(fc, r * 0.12f, Offset(cx - r * 0.35f, cy - r * 0.15f))
        drawCircle(fc, r * 0.12f, Offset(cx + r * 0.35f, cy - r * 0.15f))
        val m = Path()
        val my = cy + r * 0.38f
        m.moveTo(cx - r * 0.55f, my)
        for (i in 1..4) m.lineTo(cx - r * 0.55f + r * 0.275f * i, my + if (i % 2 == 1) -r * 0.14f else 0f)
        drawPath(m, fc, style = Stroke(r * 0.09f))
    } else {
        val ex = dx.toFloat() * r * 0.1f; val ey = dy.toFloat() * r * 0.1f
        for (s in intArrayOf(-1, 1)) {
            val e = Offset(cx + s * r * 0.38f, cy - r * 0.18f)
            drawOval(Color.White, Offset(e.x - r * 0.24f, e.y - r * 0.3f), Size(r * 0.48f, r * 0.6f))
            drawCircle(Color(0xFF1B2A8A), r * 0.15f, Offset(e.x + ex * 1.6f, e.y + ey * 1.6f))
        }
    }
}
