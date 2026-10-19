package com.karthikhegde.meterpod.games

import java.util.Random

enum class Dir(val dr: Int, val dc: Int) { UP(-1, 0), DOWN(1, 0), LEFT(0, -1), RIGHT(0, 1) }

/** A tile on the 2048 board. [id] stays the same while the tile slides so the UI can animate it. */
class Tile2048(val id: Int, var value: Int, var row: Int, var col: Int) {
    var merged = false
    var isNew = false
}

/**
 * 2048 rules: 4x4 board, slide all tiles one way; equal neighbours merge once per move into their sum (which
 * is added to the score); after every move that changed the board a 2 (90%) or 4 (10%) appears in a random
 * empty cell; reaching 2048 wins (play can continue); no empty cell and no equal neighbours ends the game.
 */
class Game2048(private val size: Int = 4, private val rnd: Random = Random()) {
    val tiles = ArrayList<Tile2048>()
    var score = 0; private set
    var won = false; private set
    var over = false; private set
    private var nextId = 0

    init { reset() }

    fun reset() {
        tiles.clear(); score = 0; won = false; over = false
        spawn(); spawn()
    }

    fun boardSize() = size

    private fun spawn() {
        val free = ArrayList<Int>()
        for (r in 0 until size) for (c in 0 until size) if (tiles.none { it.row == r && it.col == c }) free.add(r * size + c)
        if (free.isEmpty()) return
        val cell = free[rnd.nextInt(free.size)]
        val t = Tile2048(nextId++, if (rnd.nextInt(10) == 0) 4 else 2, cell / size, cell % size)
        t.isNew = true
        tiles.add(t)
    }

    /** @return true if anything moved or merged. */
    fun move(dir: Dir): Boolean {
        if (over) return false
        tiles.forEach { it.merged = false; it.isNew = false }
        var moved = false
        for (line in 0 until size) {
            // tiles of this line ordered starting from the edge they slide towards
            val lineTiles = tiles.filter { if (dir.dr != 0) it.col == line else it.row == line }
                .sortedBy { t ->
                    when (dir) {
                        Dir.UP -> t.row; Dir.DOWN -> -t.row; Dir.LEFT -> t.col; Dir.RIGHT -> -t.col
                    }
                }
            var slot = 0
            var last: Tile2048? = null
            for (t in lineTiles) {
                val prev = last
                if (prev != null && !prev.merged && prev.value == t.value) {
                    prev.value *= 2; prev.merged = true
                    score += prev.value
                    if (prev.value == 2048) won = true
                    tiles.remove(t)
                    moved = true
                    continue
                }
                val (nr, nc) = when (dir) {
                    Dir.UP -> slot to line
                    Dir.DOWN -> (size - 1 - slot) to line
                    Dir.LEFT -> line to slot
                    Dir.RIGHT -> line to (size - 1 - slot)
                }
                if (nr != t.row || nc != t.col) moved = true
                t.row = nr; t.col = nc
                slot++
                last = t
            }
        }
        if (moved) {
            spawn()
            over = !hasMoves()
        }
        return moved
    }

    private fun hasMoves(): Boolean {
        if (tiles.size < size * size) return true
        val grid = Array(size) { IntArray(size) }
        tiles.forEach { grid[it.row][it.col] = it.value }
        for (r in 0 until size) for (c in 0 until size) {
            if (c + 1 < size && grid[r][c] == grid[r][c + 1]) return true
            if (r + 1 < size && grid[r][c] == grid[r + 1][c]) return true
        }
        return false
    }

    /** Test helper / restore: place an exact board (0 = empty). */
    fun setBoard(values: Array<IntArray>) {
        tiles.clear(); score = 0; won = false; over = false
        for (r in 0 until size) for (c in 0 until size) if (values[r][c] != 0) tiles.add(Tile2048(nextId++, values[r][c], r, c))
        over = !hasMoves()
    }
}
