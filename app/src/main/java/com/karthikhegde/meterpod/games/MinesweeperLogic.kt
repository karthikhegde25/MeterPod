package com.karthikhegde.meterpod.games

import java.util.ArrayDeque
import java.util.Random

/**
 * Minesweeper board. Mines are placed after the first reveal (first click and its neighbours are always
 * safe), zero cells flood-fill outwards, and a revealed number can be "chorded" when the right number of
 * flags surround it.
 */
class Minesweeper(val rows: Int, val cols: Int, val mines: Int, private val rnd: Random = Random()) {
    companion object { const val HIDDEN = 0; const val REVEALED = 1; const val FLAGGED = 2 }

    val mine = BooleanArray(rows * cols)
    val adj = IntArray(rows * cols)
    val state = IntArray(rows * cols)
    var placed = false; private set
    var lost = false; private set
    var won = false; private set
    var explodedAt = -1; private set
    private var revealedCount = 0

    val finished get() = lost || won
    val flagsUsed get() = state.count { it == FLAGGED }
    val minesLeft get() = mines - flagsUsed

    private fun idx(r: Int, c: Int) = r * cols + c

    private fun neighbours(i: Int, f: (Int) -> Unit) {
        val r = i / cols; val c = i % cols
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val nr = r + dr; val nc = c + dc
            if (nr in 0 until rows && nc in 0 until cols) f(idx(nr, nc))
        }
    }

    private fun place(safe: Int) {
        val banned = HashSet<Int>()
        banned.add(safe); neighbours(safe) { banned.add(it) }
        var pool = (0 until rows * cols).filter { it !in banned }
        if (pool.size < mines) pool = (0 until rows * cols).filter { it != safe }
        val shuffled = pool.shuffled(rnd)
        for (k in 0 until mines) mine[shuffled[k]] = true
        for (i in 0 until rows * cols) { var n = 0; neighbours(i) { if (mine[it]) n++ }; adj[i] = n }
        placed = true
    }

    fun toggleFlag(i: Int) {
        if (finished || state[i] == REVEALED) return
        state[i] = if (state[i] == FLAGGED) HIDDEN else FLAGGED
    }

    fun reveal(i: Int) {
        if (finished || state[i] != HIDDEN) return
        if (!placed) place(i)
        if (mine[i]) { state[i] = REVEALED; lost = true; explodedAt = i; return }
        flood(i)
        checkWin()
    }

    private fun flood(start: Int) {
        val q = ArrayDeque<Int>()
        q.add(start)
        while (q.isNotEmpty()) {
            val i = q.poll()
            if (state[i] == REVEALED) continue
            if (state[i] == FLAGGED) continue
            state[i] = REVEALED; revealedCount++
            if (adj[i] == 0) neighbours(i) { if (state[it] == HIDDEN && !mine[it]) q.add(it) }
        }
    }

    /** Tap on a revealed number whose adjacent flags equal its value: reveals the other neighbours. */
    fun chord(i: Int) {
        if (finished || state[i] != REVEALED || adj[i] == 0) return
        var flags = 0
        neighbours(i) { if (state[it] == FLAGGED) flags++ }
        if (flags != adj[i]) return
        neighbours(i) {
            if (state[it] == HIDDEN) {
                if (mine[it]) { state[it] = REVEALED; lost = true; explodedAt = it } else flood(it)
            }
        }
        if (!lost) checkWin()
    }

    private fun checkWin() {
        if (!lost && revealedCount == rows * cols - mines) {
            won = true
            for (i in 0 until rows * cols) if (mine[i]) state[i] = FLAGGED
        }
    }
}
