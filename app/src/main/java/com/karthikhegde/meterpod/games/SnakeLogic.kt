package com.karthikhegde.meterpod.games

import java.util.ArrayDeque
import java.util.Random

/** Classic snake on a fixed grid: walls and your own body are fatal, food grows the snake. */
class SnakeGame(val cols: Int, val rows: Int, private val rnd: Random = Random()) {
    val body = ArrayDeque<Int>()          // head first; cell index = r * cols + c
    var dx = 1; private set
    var dy = 0; private set
    private val pending = ArrayDeque<IntArray>()
    var food = -1; private set
    var score = 0; private set
    var eaten = 0; private set
    var alive = true; private set
    var won = false; private set
    var started = false

    init { reset() }

    fun reset() {
        body.clear(); pending.clear()
        val r = rows / 2
        for (k in 0 until 3) body.addLast(r * cols + (cols / 3 - k))
        dx = 1; dy = 0; score = 0; eaten = 0; alive = true; won = false; started = false
        spawnFood()
    }

    /** Milliseconds per step: speeds up as the snake eats. */
    fun intervalMs(): Long = maxOf(75L, 190L - eaten * 4L)

    fun turn(ndx: Int, ndy: Int) {
        if (!alive) return
        // compare with the direction the snake will have after already queued turns
        val last = pending.peekLast()
        val cdx = last?.get(0) ?: dx; val cdy = last?.get(1) ?: dy
        if (ndx == -cdx && ndy == -cdy) return      // no 180 degree turns
        if (ndx == cdx && ndy == cdy) return
        if (pending.size < 3) pending.addLast(intArrayOf(ndx, ndy))
        started = true
    }

    private fun spawnFood() {
        val occupied = HashSet(body)
        val free = (0 until cols * rows).filter { it !in occupied }
        if (free.isEmpty()) { food = -1; won = true; alive = false; return }
        food = free[rnd.nextInt(free.size)]
    }

    fun step() {
        if (!alive) return
        pending.pollFirst()?.let { dx = it[0]; dy = it[1] }
        val head = body.peekFirst()!!
        val nc = head % cols + dx; val nr = head / cols + dy
        if (nc < 0 || nc >= cols || nr < 0 || nr >= rows) { alive = false; return }
        val n = nr * cols + nc
        val growing = n == food
        val tail = body.peekLast()!!
        if (body.contains(n) && !(n == tail && !growing)) { alive = false; return }
        body.addFirst(n)
        if (growing) { score += 10; eaten++; spawnFood() } else body.pollLast()
    }
}
