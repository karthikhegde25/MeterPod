package com.karthikhegde.meterpod.games

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karthikhegde.meterpod.compose.LeaveConfirmDialog
import com.karthikhegde.meterpod.compose.LocalOnBack
import com.karthikhegde.meterpod.compose.MP

private class GameInfo(val id: String, val title: String, val blurb: String, val top: Color, val bottom: Color, val bestKey: String?)

private val games = listOf(
    GameInfo("2048", "2048", "Merge tiles to reach 2048", Color(0xFFF2B179), Color(0xFFE08A3C), "best_2048"),
    GameInfo("ttt", "Tic-Tac-Toe", "X vs O, friend or computer", Color(0xFF5C9BFF), Color(0xFF2F4FD0), null),
    GameInfo("mines", "Minesweeper", "Find the mines, flag them", Color(0xFF7A8794), Color(0xFF3A4654), null),
    GameInfo("snake", "Snake", "Eat, grow, don't crash", Color(0xFF4CD964), Color(0xFF1E8E3E), "best_snake"),
    GameInfo("slide", "Number Puzzle", "Slide tiles into order", Color(0xFFB07CFF), Color(0xFF6A3FD0), null),
    GameInfo("maze", "Maze Munch", "Dodge monsters, eat dots", Color(0xFF2B3A8F), Color(0xFF0B1030), "best_maze"),
    GameInfo("flappy", "Flappy Bird", "Tap to flap through pipes", Color(0xFF4EC0F5), Color(0xFF2B8FD0), "best_flappy"),
    GameInfo("invaders", "Space Invaders", "Shoot the alien waves", Color(0xFF1A2260), Color(0xFF05070F), "best_invaders")
)

/** The Games hub: a grid of eight games. Back inside a running game asks before leaving; Back on the grid leaves the tool. */
@Composable
fun GamesScreen() {
    val ctx = LocalContext.current
    val leaveTool = LocalOnBack.current
    var current by remember { mutableStateOf<String?>(null) }
    var dirty by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }

    BackHandler(enabled = current != null) {
        if (dirty) confirm = true else { current = null }
    }
    if (confirm) {
        LeaveConfirmDialog(
            title = "Leave this game?",
            message = "Your current game will be lost. Your best scores are kept.",
            confirmText = "Leave game",
            cancelText = "Keep playing",
            onConfirm = { confirm = false; dirty = false; current = null },
            onDismiss = { confirm = false }
        )
    }

    val cur = current
    if (cur == null) {
        Column(Modifier.fillMaxSize().background(MP.Background).verticalScroll(rememberScrollState()).padding(14.dp)) {
            for (rowIdx in 0 until (games.size + 1) / 2) {
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (k in 0 until 2) {
                        val g = games.getOrNull(rowIdx * 2 + k) ?: break
                        val best = g.bestKey?.let { GameStore.getInt(ctx, it) } ?: 0
                        GameCard(g, best, Modifier.weight(1f)) { dirty = false; current = g.id }
                    }
                }
            }
        }
    } else {
        val report: (Boolean) -> Unit = { dirty = it }
        when (cur) {
            "2048" -> Game2048Screen(report)
            "ttt" -> TicTacToeScreen(report)
            "mines" -> MinesweeperScreen(report)
            "snake" -> SnakeScreen(report)
            "slide" -> SlidePuzzleScreen(report)
            "maze" -> MazeMunchScreen(report)
            "flappy" -> FlappyScreen(report)
            "invaders" -> InvadersScreen(report)
        }
    }
}

@Composable
private fun GameCard(g: GameInfo, best: Int, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(MP.Surface).clickable { onClick() }
    ) {
        Canvas(Modifier.fillMaxWidth().height(116.dp)) {
            drawRect(Brush.verticalGradient(listOf(g.top, g.bottom)))
            drawArt(g.id)
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            BasicText(g.title, style = TextStyle(color = MP.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold))
            BasicText(g.blurb, modifier = Modifier.padding(top = 2.dp), style = TextStyle(color = MP.TextSecondary, fontSize = 12.sp))
            if (g.bestKey != null) {
                BasicText(if (best > 0) "Best: $best" else "Not played yet", modifier = Modifier.padding(top = 4.dp),
                    style = TextStyle(color = MP.Warning, fontSize = 11.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}

private fun DrawScope.drawArt(id: String) {
    val w = size.width; val h = size.height; val s = minOf(w, h)
    val cx = w / 2; val cy = h / 2
    when (id) {
        "2048" -> {
            val c = s * 0.34f; val gap = s * 0.05f
            val vals = listOf(2, 4, 8, 2048)
            val cols = listOf(Color(0xFFEEE4DA), Color(0xFFEDE0C8), Color(0xFFF2B179), Color(0xFFEDC22E))
            for (i in 0 until 4) {
                val x = cx - c - gap / 2 + (i % 2) * (c + gap); val y = cy - c - gap / 2 + (i / 2) * (c + gap)
                drawRoundRect(cols[i], Offset(x, y), Size(c, c), CornerRadius(c * 0.12f))
                val n = vals[i].toString()
                // tiny digit marks: dots stand in for numerals to avoid text measuring in a DrawScope
                for (d in 0 until n.length) drawCircle(if (i < 2) Color(0xFF776E65) else Color.White, c * 0.07f,
                    Offset(x + c / 2 + (d - (n.length - 1) / 2f) * c * 0.2f, y + c / 2))
            }
        }
        "ttt" -> {
            val a = s * 0.62f; val x0 = cx - a / 2; val y0 = cy - a / 2
            val st = s * 0.035f
            for (k in 1..2) {
                drawLine(Color(0xAAFFFFFF), Offset(x0 + a * k / 3, y0), Offset(x0 + a * k / 3, y0 + a), st, StrokeCap.Round)
                drawLine(Color(0xAAFFFFFF), Offset(x0, y0 + a * k / 3), Offset(x0 + a, y0 + a * k / 3), st, StrokeCap.Round)
            }
            val u = a / 3; val r = u * 0.28f
            fun cell(c: Int, r2: Int) = Offset(x0 + (c + 0.5f) * u, y0 + (r2 + 0.5f) * u)
            for ((c, r2) in listOf(0 to 0, 2 to 1, 1 to 2)) {
                val o = cell(c, r2)
                drawLine(Color(0xFFFF6B6B), Offset(o.x - r, o.y - r), Offset(o.x + r, o.y + r), st * 1.6f, StrokeCap.Round)
                drawLine(Color(0xFFFF6B6B), Offset(o.x + r, o.y - r), Offset(o.x - r, o.y + r), st * 1.6f, StrokeCap.Round)
            }
            for ((c, r2) in listOf(1 to 1, 0 to 2)) drawCircle(Color.White, r, cell(c, r2), style = Stroke(st * 1.6f))
        }
        "mines" -> {
            val a = s * 0.7f; val u = a / 4; val x0 = cx - a / 2; val y0 = cy - a / 2
            for (r in 0 until 4) for (c in 0 until 4) {
                val revealed = (r + c) % 3 == 0
                drawRoundRect(if (revealed) Color(0xFF151B22) else Color(0xFFAAB6C4),
                    Offset(x0 + c * u + 2, y0 + r * u + 2), Size(u - 4, u - 4), CornerRadius(u * 0.14f))
            }
            val m = Offset(x0 + 2.5f * u, y0 + 1.5f * u)
            drawCircle(Color(0xFF0A0A0A), u * 0.28f, m)
            for (a2 in 0 until 4) {
                val ang = Math.PI * a2 / 4
                val dx = (Math.cos(ang) * u * 0.42).toFloat(); val dy = (Math.sin(ang) * u * 0.42).toFloat()
                drawLine(Color(0xFF0A0A0A), Offset(m.x - dx, m.y - dy), Offset(m.x + dx, m.y + dy), u * 0.08f)
            }
            drawCircle(Color(0xFFFF5252), u * 0.14f, Offset(x0 + 0.5f * u, y0 + 2.5f * u))
        }
        "snake" -> {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx - s * 0.42f, cy + s * 0.25f)
                cubicTo(cx - s * 0.2f, cy - s * 0.4f, cx, cy + s * 0.5f, cx + s * 0.2f, cy - s * 0.05f)
            }
            drawPath(path, Color(0xFF1B5E20), style = Stroke(s * 0.17f, cap = StrokeCap.Round))
            drawPath(path, Color(0xFF8CFF98), style = Stroke(s * 0.12f, cap = StrokeCap.Round))
            val head = Offset(cx + s * 0.2f, cy - s * 0.05f)
            drawCircle(Color.White, s * 0.04f, Offset(head.x + s * 0.03f, head.y - s * 0.04f))
            drawCircle(Color(0xFF111111), s * 0.02f, Offset(head.x + s * 0.04f, head.y - s * 0.04f))
            drawCircle(Color(0xFFE53935), s * 0.09f, Offset(cx + s * 0.42f, cy - s * 0.15f))
        }
        "slide" -> {
            val a = s * 0.7f; val u = a / 3; val x0 = cx - a / 2; val y0 = cy - a / 2
            for (i in 0 until 9) {
                if (i == 7) continue
                val r = i / 3; val c = i % 3
                drawRoundRect(Color(0xFFE9DDFF), Offset(x0 + c * u + 2, y0 + r * u + 2), Size(u - 4, u - 4), CornerRadius(u * 0.15f))
                for (d in 0..(i % 3)) drawCircle(Color(0xFF6A3FD0), u * 0.06f,
                    Offset(x0 + (c + 0.5f) * u + (d - (i % 3) / 2f) * u * 0.2f, y0 + (r + 0.5f) * u))
            }
        }
        "flappy" -> {
            val pc = Color(0xFF5DBB2E)
            drawRect(pc, Offset(w * 0.62f, 0f), Size(w * 0.16f, h * 0.34f)); drawRect(pc, Offset(w * 0.62f, h * 0.62f), Size(w * 0.16f, h * 0.38f))
            drawRect(Color(0xFF3E8A17), Offset(w * 0.6f, h * 0.3f), Size(w * 0.2f, h * 0.06f)); drawRect(Color(0xFF3E8A17), Offset(w * 0.6f, h * 0.6f), Size(w * 0.2f, h * 0.06f))
            drawRect(Color(0xFFDED895), Offset(0f, h * 0.88f), Size(w, h * 0.12f))
            drawCircle(Color(0xFFF7C531), s * 0.15f, Offset(w * 0.32f, h * 0.46f))
            drawCircle(Color.White, s * 0.06f, Offset(w * 0.36f, h * 0.42f)); drawCircle(Color(0xFF222222), s * 0.03f, Offset(w * 0.375f, h * 0.42f))
            drawOval(Color(0xFFFF7A2F), Offset(w * 0.37f, h * 0.47f), Size(s * 0.12f, s * 0.07f))
        }
        "invaders" -> {
            val u = s * 0.05f
            fun px(x0: Float, y0: Float, color: Color, pattern: List<String>) {
                for (r in pattern.indices) for (c in pattern[r].indices) if (pattern[r][c] == '1') drawRect(color, Offset(x0 + c * u, y0 + r * u), Size(u, u))
            }
            val alien = listOf("00100100", "00011000", "01111110", "11011011", "11111111", "10111101")
            px(cx - u * 4f - s * 0.28f, cy - s * 0.3f, Color(0xFF5CE1FF), alien)
            px(cx - u * 4f + s * 0.28f, cy - s * 0.3f, Color(0xFFFF7AD9), alien)
            px(cx - u * 4f, cy - s * 0.12f, Color(0xFF9DFF7A), alien)
            drawRect(Color.White, Offset(cx - u * 0.3f, cy + s * 0.08f), Size(u * 0.6f, u * 2.2f))
            drawRect(Color(0xFF4CD964), Offset(cx - u * 2.5f, cy + s * 0.2f), Size(u * 5f, u * 1.6f))
        }
        "maze" -> {
            val wall = Color(0xFF3D6BFF)
            drawRoundRect(wall, Offset(w * 0.1f, h * 0.14f), Size(w * 0.8f, h * 0.72f), CornerRadius(14f), style = Stroke(4f))
            drawRoundRect(wall, Offset(w * 0.22f, h * 0.3f), Size(w * 0.2f, h * 0.14f), CornerRadius(8f), style = Stroke(4f))
            drawRoundRect(wall, Offset(w * 0.58f, h * 0.56f), Size(w * 0.2f, h * 0.14f), CornerRadius(8f), style = Stroke(4f))
            for (i in 0 until 5) drawCircle(Color(0xFFFFDDB0), 4f, Offset(w * (0.5f + i * 0.07f), h * 0.45f))
            drawArc(Color(0xFFFFE14D), 35f, 290f, true, Offset(w * 0.2f, h * 0.56f), Size(h * 0.24f, h * 0.24f))
            val gx = w * 0.62f; val gy = h * 0.2f; val gr = h * 0.12f
            val body = androidx.compose.ui.graphics.Path().apply {
                moveTo(gx - gr, gy + gr); lineTo(gx - gr, gy); arcTo(androidx.compose.ui.geometry.Rect(gx - gr, gy - gr, gx + gr, gy + gr), 180f, 180f, false)
                lineTo(gx + gr, gy + gr); lineTo(gx + gr / 3, gy + gr * 0.7f); lineTo(gx - gr / 3, gy + gr); lineTo(gx - gr, gy + gr * 0.7f); close()
            }
            drawPath(body, Color(0xFFFF4D4D))
            drawCircle(Color.White, gr * 0.28f, Offset(gx - gr * 0.35f, gy - gr * 0.1f))
            drawCircle(Color.White, gr * 0.28f, Offset(gx + gr * 0.35f, gy - gr * 0.1f))
        }
    }
}
