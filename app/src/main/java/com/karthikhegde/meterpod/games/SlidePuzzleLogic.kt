package com.karthikhegde.meterpod.games

import java.util.Random

/**
 * Sliding number puzzle (3x3 = "8 puzzle", 4x4 = "15 puzzle", ...). The board is shuffled by playing
 * random legal moves backwards from the solved state, so every puzzle is guaranteed solvable.
 */
class SlidePuzzle(val n: Int, private val rnd: Random = Random()) {
    val cells = IntArray(n * n)     // 0 = empty, otherwise the tile number
    var moves = 0; private set

    init { shuffle() }

    fun shuffle() {
        for (i in cells.indices) cells[i] = (i + 1) % (n * n)
        var empty = n * n - 1
        var prev = -1
        repeat(n * n * 40) {
            val options = neighbours(empty).filter { it != prev }
            val pick = options[rnd.nextInt(options.size)]
            cells[empty] = cells[pick]; cells[pick] = 0
            prev = empty; empty = pick
        }
        if (isSolved()) shuffle()
        moves = 0
    }

    private fun neighbours(i: Int): List<Int> {
        val r = i / n; val c = i % n
        val out = ArrayList<Int>(4)
        if (r > 0) out.add(i - n); if (r < n - 1) out.add(i + n)
        if (c > 0) out.add(i - 1); if (c < n - 1) out.add(i + 1)
        return out
    }

    fun emptyIndex() = cells.indexOf(0)

    fun isSolved(): Boolean {
        for (i in 0 until n * n - 1) if (cells[i] != i + 1) return false
        return cells[n * n - 1] == 0
    }

    /** Taps tile at [i]; tiles in the same row/column as the gap slide together. @return true if something moved. */
    fun tap(i: Int): Boolean {
        val e = emptyIndex()
        if (i == e) return false
        val r = i / n; val c = i % n; val er = e / n; val ec = e % n
        if (r != er && c != ec) return false
        val step = if (r == er) (if (ec > c) 1 else -1) else (if (er > r) n else -n)
        var pos = e
        while (pos != i) { cells[pos] = cells[pos - step]; pos -= step }
        cells[i] = 0
        moves++
        return true
    }
}
