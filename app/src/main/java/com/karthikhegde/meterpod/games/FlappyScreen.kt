package com.karthikhegde.meterpod.games

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.karthikhegde.meterpod.compose.MP
import com.karthikhegde.meterpod.compose.Text3D
import kotlin.math.PI
import kotlin.math.sin

/** Flappy Bird: tap to flap through the gaps between pipes. Best score is saved. */
@Composable
fun FlappyScreen(onProgress: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val game = remember { FlappyGame() }
    var frame by remember { mutableLongStateOf(0L) }
    var best by remember { mutableIntStateOf(GameStore.getInt(ctx, "best_flappy")) }
    var state by remember { mutableStateOf(game.state) }
    var score by remember { mutableIntStateOf(0) }

    // One update per display frame: position, rotation, pipes and parallax all advance every frame.
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { n ->
                if (last != 0L) game.update((n - last) / 1_000_000_000f)
                last = n
                frame = n
                if (game.state != state) state = game.state
                if (game.score != score) score = game.score
                if (game.score > best) { best = game.score; GameStore.putInt(ctx, "best_flappy", best) }
            }
        }
    }
    LaunchedEffect(state) { onProgress(state == FlappyState.PLAYING) }

    Column(Modifier.fillMaxSize().background(MP.Background).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScoreBox("SCORE", score.toString(), Modifier.weight(1f))
            ScoreBox("BEST", best.toString(), Modifier.weight(1f), Color(0xFFFFC940))
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
            val ratio = FlappyGame.W / FlappyGame.H
            val w = minOf(maxWidth, maxHeight * ratio)
            Box(
                Modifier.size(w, w / ratio).clip(RoundedCornerShape(14.dp))
                    .pointerInput(Unit) { detectTapGestures(onPress = { game.flap() }) }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    frame
                    val sc = size.width / FlappyGame.W
                    withTransform({ scale(sc, sc, Offset.Zero) }) { drawWorld(game) }
                }
                if (state != FlappyState.DEAD) Box(Modifier.fillMaxWidth().padding(top = 18.dp), contentAlignment = Alignment.TopCenter) {
                    Text3D(score.toString(), 54f, color = Color.White)
                }
                if (state == FlappyState.READY) {
                    GameOverlay("Flappy Bird", "Tap to flap and start", tint = Color(0x33000000)) {}
                } else if (state == FlappyState.DEAD) {
                    GameOverlay("Game over", "Score $score   Best $best", tint = Color(0x99000000)) {
                        SmallButton("Restart", onClick = { game.reset(); score = 0; state = game.state }, primary = true)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawWorld(g: FlappyGame) {
    val W = FlappyGame.W; val H = FlappyGame.H; val gy = FlappyGame.GROUND_Y
    // sky
    drawRect(Brush.verticalGradient(listOf(Color(0xFF3FB5F0), Color(0xFF9ADFFA), Color(0xFFDDF5FF)), 0f, gy), size = Size(W, gy))
    // far clouds (slowest layer), looping seamlessly
    val loopC = W + 160f
    for (i in 0 until 5) {
        val base = i * (loopC / 5f)
        val x = ((base - g.cloudOff) % loopC + loopC) % loopC - 80f
        val y = 70f + (i * 53 % 160)
        cloud(x, y, 0.8f + (i % 3) * 0.25f)
    }
    // hills (medium layer)
    val tile = 210f
    val ho = g.hillOff % tile
    var hx = -ho - tile
    while (hx < W + tile) {
        drawOval(Color(0xFF8ED081), Offset(hx, gy - 84f), Size(tile * 1.1f, 160f))
        drawOval(Color(0xFFA6DE96), Offset(hx + tile * 0.55f, gy - 56f), Size(tile * 0.9f, 120f))
        hx += tile
    }
    // pipes
    for (p in g.pipes) {
        val gap = g.gap
        pipe(p.x, 0f, FlappyGame.PIPE_W, p.gapTop, capAtBottom = true)
        pipe(p.x, p.gapTop + gap, FlappyGame.PIPE_W, gy - (p.gapTop + gap), capAtBottom = false)
    }
    // ground (scrolls at pipe speed)
    drawRect(Color(0xFFDED895), Offset(0f, gy), Size(W, H - gy))
    drawRect(Color(0xFF73BF2E), Offset(0f, gy), Size(W, 14f))
    drawRect(Color(0xFFA8E061), Offset(0f, gy), Size(W, 4f))
    val stripe = 28f
    var sx = -(g.groundOff % stripe) - stripe
    while (sx < W + stripe) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(sx, gy + 14f); lineTo(sx + stripe / 2f, gy + 14f); lineTo(sx + stripe / 2f - 10f, gy + 28f); lineTo(sx - 10f, gy + 28f); close()
        }
        drawPath(path, Color(0xFFC9C277))
        sx += stripe
    }
    drawRect(Color(0xFFB5AE63), Offset(0f, gy + 28f), Size(W, 4f))
    // bird, tilted with its vertical velocity
    bird(g)
}

private fun DrawScope.cloud(x: Float, y: Float, s: Float) {
    val c = Color(0xCCFFFFFF)
    drawCircle(c, 20f * s, Offset(x, y)); drawCircle(c, 26f * s, Offset(x + 26f * s, y - 8f * s))
    drawCircle(c, 20f * s, Offset(x + 54f * s, y)); drawRoundRect(c, Offset(x - 12f * s, y), Size(80f * s, 20f * s), CornerRadius(10f * s))
}

private fun DrawScope.pipe(x: Float, y: Float, w: Float, h: Float, capAtBottom: Boolean) {
    if (h <= 0f) return
    val body = Brush.horizontalGradient(listOf(Color(0xFF4F9F1F), Color(0xFF9BE35A), Color(0xFF5BB02A), Color(0xFF3E8A17)), x, x + w)
    drawRect(body, Offset(x + 4f, y), Size(w - 8f, h))
    val capH = 26f
    val capY = if (capAtBottom) y + h - capH else y
    drawRoundRect(body, Offset(x, capY), Size(w, capH), CornerRadius(4f))
    drawRoundRect(Color(0xFF2F6B10), Offset(x, capY), Size(w, capH), CornerRadius(4f), style = androidx.compose.ui.graphics.drawscope.Stroke(2.5f))
    drawRect(Color(0x33FFFFFF), Offset(x + 8f, capY + 3f), Size(6f, capH - 6f))
}

private fun DrawScope.bird(g: FlappyGame) {
    val cx = FlappyGame.BIRD_X; val cy = g.birdY; val r = FlappyGame.BIRD_R
    rotate(g.rotation, Offset(cx, cy)) {
        // body
        drawOval(Color(0xFFF7C531), Offset(cx - r * 1.2f, cy - r), Size(r * 2.4f, r * 2f))
        drawOval(Color(0xFFFFE27A), Offset(cx - r * 1.0f, cy - r * 0.95f), Size(r * 1.6f, r * 1.0f))
        drawOval(Color(0xFFE59F1E), Offset(cx - r * 1.2f, cy - r), Size(r * 2.4f, r * 2f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.6f))
        // wing flaps with time
        val flap = sin(g.time * 18f * PI.toFloat()) * 0.5f + 0.5f
        drawOval(Color(0xFFFFFFFF), Offset(cx - r * 0.95f, cy - r * 0.1f + flap * r * 0.25f), Size(r * 1.1f, r * (0.7f - flap * 0.25f)))
        drawOval(Color(0xFFE0B14A), Offset(cx - r * 0.95f, cy - r * 0.1f + flap * r * 0.25f), Size(r * 1.1f, r * (0.7f - flap * 0.25f)), style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f))
        // eye
        drawCircle(Color.White, r * 0.42f, Offset(cx + r * 0.5f, cy - r * 0.35f))
        drawCircle(Color(0xFF222222), r * 0.2f, Offset(cx + r * 0.62f, cy - r * 0.35f))
        // beak
        drawOval(Color(0xFFFF7A2F), Offset(cx + r * 0.7f, cy - r * 0.05f), Size(r * 0.95f, r * 0.55f))
        drawOval(Color(0xFFD9541A), Offset(cx + r * 0.7f, cy + r * 0.22f), Size(r * 0.85f, r * 0.3f))
    }
}
