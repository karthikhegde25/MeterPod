package com.karthikhegde.meterpod.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karthikhegde.meterpod.compose.MP
import java.util.Random

private val XColor = Color(0xFFFF5C5C)
private val OColor = Color(0xFF4C8DFF)

/** Tic-Tac-Toe: two players on one phone, or against the computer (Easy = mistakes, Hard = unbeatable). */
@Composable
fun TicTacToeScreen(onProgress: (Boolean) -> Unit) {
    val board = remember { IntArray(9) }
    val rnd = remember { Random() }
    var tick by remember { mutableIntStateOf(0) }
    var mode by remember { mutableIntStateOf(1) }          // 0 = two players, 1 = vs computer
    var hard by remember { mutableIntStateOf(0) }          // 0 easy, 1 hard
    var turn by remember { mutableIntStateOf(1) }          // 1 = X, 2 = O
    var starter by remember { mutableIntStateOf(1) }
    var xWins by remember { mutableIntStateOf(0) }
    var oWins by remember { mutableIntStateOf(0) }
    var draws by remember { mutableIntStateOf(0) }

    val line = remember(tick) { TicTacToe.winningLine(board) }
    val full = remember(tick) { TicTacToe.isFull(board) }
    val finished = line != null || full
    val winner = if (line != null) board[line[0]] else 0

    LaunchedEffect(tick) { onProgress(!finished && board.any { it != 0 }) }

    fun apply(i: Int) {
        if (finished || board[i] != 0) return
        board[i] = turn
        val l = TicTacToe.winningLine(board)
        if (l != null) { if (board[l[0]] == 1) xWins++ else oWins++ }
        else if (TicTacToe.isFull(board)) draws++
        else turn = 3 - turn
        tick++
    }

    fun newRound() {
        board.fill(0); starter = 3 - starter; turn = starter; tick++
    }

    fun resetAll(newMode: Int = mode) {
        mode = newMode; board.fill(0); xWins = 0; oWins = 0; draws = 0; starter = 1; turn = 1; tick++
    }

    // computer plays O
    LaunchedEffect(tick, mode, hard) {
        if (mode == 1 && turn == 2 && !finished) {
            kotlinx.coroutines.delay(550)
            val mv = if (hard == 1) TicTacToe.bestMove(board, 2) else TicTacToe.easyMove(board, 2, rnd)
            apply(mv)
        }
    }

    Column(
        Modifier.fillMaxSize().background(MP.Background).verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("2 Players", onClick = { if (mode != 0) resetAll(0) }, primary = mode == 0)
            SmallButton("vs Computer", onClick = { if (mode != 1) resetAll(1) }, primary = mode == 1)
        }
        if (mode == 1) {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("Easy", onClick = { if (hard != 0) { hard = 0; resetAll() } }, primary = hard == 0)
                SmallButton("Hard", onClick = { if (hard != 1) { hard = 1; resetAll() } }, primary = hard == 1)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScoreBox(if (mode == 1) "YOU (X)" else "PLAYER X", xWins.toString(), Modifier.weight(1f), XColor)
            ScoreBox("DRAWS", draws.toString(), Modifier.weight(1f), MP.TextSecondary)
            ScoreBox(if (mode == 1) "COMPUTER (O)" else "PLAYER O", oWins.toString(), Modifier.weight(1f), OColor)
        }
        val status = when {
            winner == 1 -> if (mode == 1) "You win!" else "X wins!"
            winner == 2 -> if (mode == 1) "Computer wins" else "O wins!"
            full -> "It's a draw"
            mode == 1 && turn == 2 -> "Computer is thinking…"
            mode == 1 -> "Your turn (X)"
            else -> if (turn == 1) "X's turn" else "O's turn"
        }
        BasicText(
            status, modifier = Modifier.padding(top = 14.dp, bottom = 10.dp),
            style = TextStyle(
                color = when { winner == 1 -> XColor; winner == 2 -> OColor; else -> MP.TextPrimary },
                fontSize = 20.sp, fontWeight = FontWeight.Bold
            )
        )

        val tap by rememberUpdatedState({ i: Int -> if (mode == 0 || turn == 1) apply(i) })
        Canvas(
            Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .pointerInput(Unit) {
                    detectTapGestures { off ->
                        val cw = size.width / 3f; val ch = size.height / 3f
                        val c = (off.x / cw).toInt().coerceIn(0, 2); val r = (off.y / ch).toInt().coerceIn(0, 2)
                        tap(r * 3 + c)
                    }
                }
        ) {
            tick
            drawRoundRect(MP.Surface, size = size, cornerRadius = CornerRadius(16.dp.toPx()))
            val cw = size.width / 3f; val ch = size.height / 3f
            val gridStroke = 5.dp.toPx(); val pad = 14.dp.toPx()
            for (k in 1..2) {
                drawLine(MP.SurfaceOutline, Offset(cw * k, pad), Offset(cw * k, size.height - pad), gridStroke, StrokeCap.Round)
                drawLine(MP.SurfaceOutline, Offset(pad, ch * k), Offset(size.width - pad, ch * k), gridStroke, StrokeCap.Round)
            }
            for (i in 0 until 9) {
                val cx = (i % 3 + 0.5f) * cw; val cy = (i / 3 + 0.5f) * ch
                val r = minOf(cw, ch) * 0.26f
                val dim = line != null && i !in line
                when (board[i]) {
                    1 -> {
                        val col = if (dim) XColor.copy(alpha = 0.3f) else XColor
                        drawLine(col, Offset(cx - r, cy - r), Offset(cx + r, cy + r), 12.dp.toPx(), StrokeCap.Round)
                        drawLine(col, Offset(cx + r, cy - r), Offset(cx - r, cy + r), 12.dp.toPx(), StrokeCap.Round)
                    }
                    2 -> drawCircle(if (dim) OColor.copy(alpha = 0.3f) else OColor, r * 1.1f, Offset(cx, cy), style = Stroke(12.dp.toPx()))
                }
            }
            if (line != null) {
                val a = line[0]; val b = line[2]
                drawLine(
                    Color(0xFFFFC107),
                    Offset((a % 3 + 0.5f) * cw, (a / 3 + 0.5f) * ch), Offset((b % 3 + 0.5f) * cw, (b / 3 + 0.5f) * ch),
                    7.dp.toPx(), StrokeCap.Round
                )
            }
        }
        Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallButton("New round", onClick = { newRound() }, primary = finished)
            SmallButton("Reset scores", onClick = { resetAll() })
        }
    }
}
