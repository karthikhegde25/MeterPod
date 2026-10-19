package com.karthikhegde.meterpod.games

import java.util.Random

/** Board cells: 0 empty, 1 = X, 2 = O. */
object TicTacToe {
    val LINES = arrayOf(
        intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8),
        intArrayOf(0, 3, 6), intArrayOf(1, 4, 7), intArrayOf(2, 5, 8),
        intArrayOf(0, 4, 8), intArrayOf(2, 4, 6)
    )

    fun winningLine(b: IntArray): IntArray? {
        for (l in LINES) if (b[l[0]] != 0 && b[l[0]] == b[l[1]] && b[l[1]] == b[l[2]]) return l
        return null
    }

    fun isFull(b: IntArray) = b.none { it == 0 }

    /** Easy: usually random, but takes an immediate win or blocks about a third of the time. */
    fun easyMove(b: IntArray, me: Int, rnd: Random): Int {
        val empty = b.indices.filter { b[it] == 0 }
        if (rnd.nextInt(3) == 0) {
            immediate(b, me)?.let { return it }
            immediate(b, 3 - me)?.let { return it }
        }
        return empty[rnd.nextInt(empty.size)]
    }

    private fun immediate(b: IntArray, who: Int): Int? {
        for (l in LINES) {
            val vals = l.map { b[it] }
            if (vals.count { it == who } == 2 && vals.count { it == 0 } == 1) return l[vals.indexOf(0)]
        }
        return null
    }

    /** Unbeatable minimax move for [me]. */
    fun bestMove(b: IntArray, me: Int): Int {
        var best = -1; var bestScore = Int.MIN_VALUE
        for (i in 0 until 9) if (b[i] == 0) {
            b[i] = me
            val s = minimax(b, 3 - me, me, 1)
            b[i] = 0
            if (s > bestScore) { bestScore = s; best = i }
        }
        return best
    }

    private fun minimax(b: IntArray, turn: Int, me: Int, depth: Int): Int {
        val line = winningLine(b)
        if (line != null) return if (b[line[0]] == me) 10 - depth else depth - 10
        if (isFull(b)) return 0
        var best = if (turn == me) Int.MIN_VALUE else Int.MAX_VALUE
        for (i in 0 until 9) if (b[i] == 0) {
            b[i] = turn
            val s = minimax(b, 3 - turn, me, depth + 1)
            b[i] = 0
            best = if (turn == me) maxOf(best, s) else minOf(best, s)
        }
        return best
    }
}
