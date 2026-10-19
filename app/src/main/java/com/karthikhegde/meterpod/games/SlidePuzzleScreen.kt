package com.karthikhegde.meterpod.games

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karthikhegde.meterpod.compose.MP
import kotlinx.coroutines.delay

private fun fmtT(s: Int) = "%d:%02d".format(s / 60, s % 60)

/** Sliding number puzzle for 3x3, 4x4 and 5x5 boards, with move counter, timer and best moves per size. */
@Composable
fun SlidePuzzleScreen(onProgress: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    var size by remember { mutableIntStateOf(4) }
    var puzzle by remember { mutableStateOf(SlidePuzzle(4).also { it.shuffle() }) }
    var tick by remember { mutableIntStateOf(0) }
    var seconds by remember { mutableIntStateOf(0) }
    var bestTick by remember { mutableIntStateOf(0) }
    var newBest by remember { mutableStateOf(false) }
    val p = puzzle

    val moves = remember(tick, p) { p.moves }
    val solved = remember(tick, p) { p.isSolved() && p.moves > 0 }
    val cellsSnap = remember(tick, p) { p.cells.copyOf() }

    LaunchedEffect(tick, p) { onProgress(moves > 0 && !solved) }
    LaunchedEffect(p, moves > 0, solved) {
        if (moves > 0 && !solved) while (true) { delay(1000); seconds++ }
    }
    LaunchedEffect(solved) {
        if (solved) {
            val key = "best_slide_$size"
            val old = GameStore.getInt(ctx, key, 0)
            if (old == 0 || moves < old) { GameStore.putInt(ctx, key, moves); newBest = true; bestTick++ }
        }
    }
    fun fresh(n: Int = size) { size = n; puzzle = SlidePuzzle(n).also { it.shuffle() }; seconds = 0; newBest = false; tick++ }
    val best = remember(size, bestTick) { GameStore.getInt(ctx, "best_slide_$size", 0) }

    Column(Modifier.fillMaxSize().background(MP.Background).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (n in 3..5) SmallButton("${n}×$n", onClick = { if (n != size) fresh(n) }, primary = n == size)
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScoreBox("MOVES", moves.toString(), Modifier.weight(1f))
            ScoreBox("TIME", fmtT(seconds), Modifier.weight(1f), MP.Warning)
            ScoreBox("BEST", if (best > 0) best.toString() else "-", Modifier.weight(1f), Color(0xFF4CD964))
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
            val side = if (maxWidth < maxHeight) maxWidth else maxHeight
            val gap = 6.dp
            val cell = (side - gap * (size + 1)) / size
            Box(Modifier.size(side).background(Color(0xFF151B22), RoundedCornerShape(16.dp))) {
                for (i in 0 until size * size) {
                    val v = cellsSnap[i]
                    if (v != 0) key(v) {
                        PuzzleTile(v, i / size, i % size, cell, gap, size, solved) { if (!solved && p.tap(i)) tick++ }
                    }
                }
                if (solved) {
                    GameOverlay("Solved!", if (newBest) "New best: $moves moves" else "$moves moves in ${fmtT(seconds)}", tint = Color(0x990D1117)) {
                        SmallButton("Play again", onClick = { fresh() }, primary = true)
                    }
                }
            }
        }
        SmallButton("Shuffle", onClick = { fresh() })
        BasicText("Tap a tile next to the gap. Tap further away to slide a whole row.",
            modifier = Modifier.padding(top = 8.dp), style = TextStyle(color = MP.TextSecondary, fontSize = 11.sp))
    }
}

@Composable
private fun PuzzleTile(v: Int, row: Int, col: Int, cell: Dp, gap: Dp, n: Int, solved: Boolean, onTap: () -> Unit) {
    val x by animateDpAsState(gap + (cell + gap) * col, tween(120), label = "x")
    val y by animateDpAsState(gap + (cell + gap) * row, tween(120), label = "y")
    val home = v == row * n + col + 1
    val top = if (home) Color(0xFF4CD9A0) else Color(0xFF5C9BFF)
    val bottom = if (home) Color(0xFF1E9E6A) else Color(0xFF2F6FE0)
    Box(
        Modifier.offset(x, y).size(cell)
            .background(Brush.verticalGradient(listOf(top, bottom)), RoundedCornerShape(cell * 0.14f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTap() },
        contentAlignment = Alignment.Center
    ) {
        BasicText(v.toString(), style = TextStyle(color = Color.White, fontSize = (cell.value * 0.42f).sp, fontWeight = FontWeight.Bold))
    }
}
