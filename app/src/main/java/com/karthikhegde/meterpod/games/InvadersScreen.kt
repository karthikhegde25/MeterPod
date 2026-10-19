package com.karthikhegde.meterpod.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karthikhegde.meterpod.compose.MP
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val STARS = run {
    val r = Random(11)
    List(60) { Triple(r.nextFloat() * InvadersGame.W, r.nextFloat() * InvadersGame.H, r.nextFloat()) }
}

// 8-row pixel sprites, two animation frames each ('1' = pixel)
private val SQUID = arrayOf(
    arrayOf("00011000", "00111100", "01111110", "11011011", "11111111", "00100100", "01011010", "10100101"),
    arrayOf("00011000", "00111100", "01111110", "11011011", "11111111", "01011010", "10000001", "01000010")
)
private val CRAB = arrayOf(
    arrayOf("00100000100", "00010001000", "00111111100", "01101110110", "11111111111", "10111111101", "10100000101", "00011011000"),
    arrayOf("00100000100", "10010001001", "10111111101", "11101110111", "11111111111", "01111111110", "00100000100", "01000000010")
)
private val OCTO = arrayOf(
    arrayOf("000011110000", "011111111110", "111111111111", "111001100111", "111111111111", "000110011000", "001101101100", "110000000011"),
    arrayOf("000011110000", "011111111110", "111111111111", "111001100111", "111111111111", "001110011100", "011001100110", "001100001100")
)

/** Space Invaders: drag or use the arrows to move, hold Shoot to fire; clear waves of aliens. */
@Composable
fun InvadersScreen(onProgress: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val game = remember { InvadersGame() }
    var frame by remember { mutableLongStateOf(0L) }
    var best by remember { mutableIntStateOf(GameStore.getInt(ctx, "best_invaders")) }
    var state by remember { mutableStateOf(game.state) }
    var score by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(game.lives) }
    var wave by remember { mutableIntStateOf(game.wave) }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { n ->
                if (last != 0L) game.update((n - last) / 1_000_000_000f)
                last = n
                frame = n
                if (game.state != state) state = game.state
                if (game.score != score) score = game.score
                if (game.lives != lives) lives = game.lives
                if (game.wave != wave) wave = game.wave
                if (game.score > best) { best = game.score; GameStore.putInt(ctx, "best_invaders", best) }
            }
        }
    }
    LaunchedEffect(state) { onProgress(state == InvState.PLAYING) }

    Column(Modifier.fillMaxSize().background(MP.Background).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ScoreBox("SCORE", score.toString(), Modifier.weight(1.2f))
            ScoreBox("LIVES", "♥".repeat(lives.coerceAtLeast(0)).ifEmpty { "-" }, Modifier.weight(1f), Color(0xFFFF5A7A))
            ScoreBox("WAVE", wave.toString(), Modifier.weight(0.8f), Color(0xFF4CD964))
            ScoreBox("BEST", best.toString(), Modifier.weight(1f), Color(0xFFFFC940))
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            val ratio = InvadersGame.W / InvadersGame.H
            val w = minOf(maxWidth, maxHeight * ratio)
            Box(
                Modifier.size(w, w / ratio).clip(RoundedCornerShape(14.dp))
                    .pointerInput(Unit) { detectTapGestures(onTap = { game.shootOnce() }) }
                    .pointerInput(Unit) {
                        detectDragGestures(onDragStart = { game.start() }) { change, _ ->
                            change.consume()
                            game.moveTo(change.position.x / (size.width / InvadersGame.W))
                        }
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    frame
                    val sc = size.width / InvadersGame.W
                    withTransform({ scale(sc, sc, Offset.Zero) }) { drawInvaders(game) }
                }
                when (state) {
                    InvState.READY -> GameOverlay("Space Invaders", "Drag or use ◀ ▶ to move, hold SHOOT to fire", tint = Color(0x88000000)) {
                        SmallButton("Start", onClick = { game.start(); state = game.state }, primary = true)
                    }
                    InvState.WAVE_CLEAR -> GameOverlay("Wave $wave cleared!", "Score $score   Best $best", tint = Color(0x99000000)) {
                        SmallButton("Next wave", onClick = { game.nextWave(); state = game.state; wave = game.wave }, primary = true)
                    }
                    InvState.GAME_OVER -> GameOverlay(
                        if (lives <= 0) "Game over" else "They landed!",
                        "Score $score   Wave $wave   Best $best", tint = Color(0xAA000000)
                    ) { SmallButton("Play again", onClick = { game.restart(); state = game.state; score = 0; lives = game.lives; wave = game.wave }, primary = true) }
                    else -> {}
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HoldKey("◀", Modifier.weight(1f)) { game.moveDir = if (it) -1 else if (game.moveDir == -1) 0 else game.moveDir }
            HoldKey("SHOOT", Modifier.weight(2f)) { game.firing = it; if (it) game.shootOnce() }
            HoldKey("▶", Modifier.weight(1f)) { game.moveDir = if (it) 1 else if (game.moveDir == 1) 0 else game.moveDir }
        }
    }
}

/** A key that reports press and release (so it can be held), drawn like the app's blue keys. */
@Composable
private fun HoldKey(label: String, modifier: Modifier, onHold: (Boolean) -> Unit) {
    var down by remember { mutableStateOf(false) }
    Box(
        modifier.height(58.dp).clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(if (down) listOf(Color(0xFF2F68E0), Color(0xFF1F55E0)) else listOf(Color(0xFF5E9BFF), Color(0xFF2F6BF0))))
            .border(1.dp, Color(0xFF8FB6FF), RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    down = true; onHold(true)
                    try { tryAwaitRelease() } finally { down = false; onHold(false) }
                })
            },
        contentAlignment = Alignment.Center
    ) {
        BasicText(label, style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold))
    }
}

private fun DrawScope.sprite(rows: Array<String>, x: Float, y: Float, w: Float, h: Float, color: Color) {
    val cw = w / rows[0].length; val ch = h / rows.size
    for (r in rows.indices) for (c in rows[r].indices) if (rows[r][c] == '1') drawRect(color, Offset(x + c * cw, y + r * ch), Size(cw + 0.4f, ch + 0.4f))
}

private fun DrawScope.drawInvaders(g: InvadersGame) {
    val W = InvadersGame.W; val H = InvadersGame.H
    drawRect(Brush.verticalGradient(listOf(Color(0xFF05070F), Color(0xFF0B1030), Color(0xFF111A44))), size = Size(W, H))
    for ((x, y, t) in STARS) {
        val tw = 0.5f + 0.5f * sin(g.time * 2f + t * 20f)
        drawCircle(Color.White.copy(alpha = 0.25f + 0.55f * tw * t), 0.8f + t, Offset(x, y))
    }
    // invasion line
    drawLine(Color(0x55FF5A7A), Offset(0f, InvadersGame.INVASION_Y + 10f), Offset(W, InvadersGame.INVASION_Y + 10f), 1f)

    // shields
    for (i in 0 until 4) {
        val sx = g.shieldX(i)
        for (r in 0 until InvadersGame.SHIELD_ROWS) for (c in 0 until InvadersGame.SHIELD_COLS) {
            if (g.shields[i][r * InvadersGame.SHIELD_COLS + c])
                drawRect(if (r < 2) Color(0xFF7DFFA0) else Color(0xFF3DDC6E), Offset(sx + c * InvadersGame.SHIELD_CELL, InvadersGame.SHIELD_Y + r * InvadersGame.SHIELD_CELL),
                    Size(InvadersGame.SHIELD_CELL + 0.3f, InvadersGame.SHIELD_CELL + 0.3f))
        }
    }

    // aliens
    for (a in g.aliens) {
        if (!a.alive) continue
        val x = g.alienX(a); val y = g.alienY(a)
        when (a.row) {
            0 -> sprite(SQUID[g.animFrame], x + 2f, y, InvadersGame.ALIEN_W - 4f, InvadersGame.ALIEN_H, Color(0xFFFF7AD9))
            1, 2 -> sprite(CRAB[g.animFrame], x, y, InvadersGame.ALIEN_W, InvadersGame.ALIEN_H, Color(0xFF5CE1FF))
            else -> sprite(OCTO[g.animFrame], x, y, InvadersGame.ALIEN_W, InvadersGame.ALIEN_H, Color(0xFF9DFF7A))
        }
    }

    // shots
    for (s in g.playerShots) drawRect(Color.White, Offset(s.x - 1.2f, s.y - 6f), Size(2.4f, 10f))
    for (s in g.enemyShots) {
        val wob = sin(s.y * 0.5f) * 2f
        drawRect(Color(0xFFFFD84D), Offset(s.x - 1.5f + wob, s.y - 5f), Size(3f, 10f))
    }

    // ship (blinks while invulnerable after a hit)
    if (g.state != InvState.GAME_OVER || g.lives > 0) {
        val blink = g.invulnerable > 0f && ((g.time * 12f).toInt() % 2 == 0)
        if (!blink) {
            val x = g.shipX; val y = InvadersGame.SHIP_Y; val hw = InvadersGame.SHIP_W / 2f
            val body = Path().apply {
                moveTo(x - hw, y + InvadersGame.SHIP_H); lineTo(x - hw, y + 7f); lineTo(x - 5f, y + 5f); lineTo(x - 2f, y - 2f)
                lineTo(x + 2f, y - 2f); lineTo(x + 5f, y + 5f); lineTo(x + hw, y + 7f); lineTo(x + hw, y + InvadersGame.SHIP_H); close()
            }
            drawPath(body, Brush.verticalGradient(listOf(Color(0xFF8CFFA8), Color(0xFF2FB85A)), y - 2f, y + InvadersGame.SHIP_H))
            drawRect(Color(0xFFFFFFFF), Offset(x - 1.5f, y - 8f), Size(3f, 8f))
        }
    }

    // explosions: expanding burst of rays
    for (b in g.booms) {
        val p = b.t / 0.4f
        val r = (if (b.big) 26f else 14f) * (0.3f + p)
        val a = (1f - p).coerceIn(0f, 1f)
        drawCircle(Color(0xFFFFD84D).copy(alpha = a * 0.55f), r * 0.6f, Offset(b.x, b.y))
        for (k in 0 until 8) {
            val ang = k * PI.toFloat() / 4f
            drawLine(Color(0xFFFF8A3D).copy(alpha = a), Offset(b.x + cos(ang) * r * 0.45f, b.y + sin(ang) * r * 0.45f),
                Offset(b.x + cos(ang) * r, b.y + sin(ang) * r), 2f)
        }
    }
}
