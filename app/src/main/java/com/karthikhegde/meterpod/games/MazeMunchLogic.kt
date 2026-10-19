package com.karthikhegde.meterpod.games

import java.util.ArrayDeque
import java.util.Random

enum class GhostState { HOUSE, NORMAL, SCARED, EYES }
enum class MazePhase { READY, PLAYING, DYING, LEVEL_DONE, GAME_OVER }

open class Mover {
    var c = 0; var r = 0          // tile the mover is on / leaving
    var dx = 0; var dy = 0        // direction of travel (0,0 = standing)
    var t = 0f                    // progress 0..1 towards the next tile
    val x get() = c + dx * t
    val y get() = r + dy * t
}

class Ghost(val kind: Int, val spawnC: Int, val spawnR: Int) : Mover() {
    var state = GhostState.HOUSE
    var releaseIn = 0f
    var reverseNext = false
}

/**
 * Maze Munch engine (an original Pac-Man style game). Ghost behaviour follows the classic design: at every
 * junction a ghost picks the exit whose tile is nearest its target and never turns back on itself; targets
 * differ per ghost (Blinky = the player, Pinky = four tiles ahead, Inky = a point mirrored through Blinky,
 * Clyde = the player unless close), chase and scatter phases alternate, and power pellets turn ghosts blue
 * so they wander randomly and can be eaten (200/400/800/1600 points in a chain).
 */
class MazeMunch(private val rnd: Random = Random()) {
    var level = 1; private set
    var score = 0; private set
    var lives = 3; private set
    var phase = MazePhase.READY; private set
    var phaseTimer = 2f; private set

    var cols = 0; private set
    var rows = 0; private set
    private lateinit var walls: Array<CharArray>
    lateinit var dots: Array<BooleanArray>; private set
    lateinit var pellets: Array<BooleanArray>; private set
    var dotsLeft = 0; private set

    val player = Mover()
    private var startC = 0; private var startR = 0
    private var wantDx = 0; private var wantDy = 0
    var faceDx = -1; private set
    var faceDy = 0; private set
    val ghosts = ArrayList<Ghost>()
    private var penC = 0; private var penR = 0
    private var exitC = 0; private var exitR = 0
    private lateinit var penDist: Array<IntArray>

    var scaredLeft = 0f; private set
    private var chain = 0
    private var modeTimer = 0f
    private var scatter = true

    var levelClearedCount = 0; private set   // how many levels finished this game

    init { loadLevel(1); phase = MazePhase.READY; phaseTimer = 2f }

    // ------------------------------------------------------------- level parameters
    private fun playerSpeed() = 5.4f
    private fun ghostSpeed() = playerSpeed() * minOf(0.95f, 0.70f + 0.045f * (level - 1))
    private fun wander() = maxOf(0.03f, 0.34f - 0.08f * (level - 1))
    private fun scaredTime() = maxOf(2.5f, 8f - 0.9f * (level - 1))
    private fun scatterTime() = maxOf(2.5f, 7f - (level - 1) * 0.8f)
    private fun chaseTime() = 14f + 2f * minOf(level - 1, 6)
    private fun ghostCount() = if (level == 1) 2 else if (level <= 3) 3 else 4

    /** Layout character at a tile ('#', '=', '.', 'o', 'P', 'G' or ' ') for the renderer. */
    fun cell(c: Int, r: Int): Char = walls[r][c]

    fun levelCount() = MazeLevels.layouts.size
    fun layoutIndex() = (level - 1) % MazeLevels.layouts.size

    // ------------------------------------------------------------- setup
    private fun loadLevel(lv: Int) {
        level = lv
        val layout = MazeLevels.layouts[layoutIndex()]
        rows = layout.size; cols = layout[0].length
        walls = Array(rows) { r -> layout[r].toCharArray() }
        dots = Array(rows) { BooleanArray(cols) }
        pellets = Array(rows) { BooleanArray(cols) }
        dotsLeft = 0
        val gs = ArrayList<IntArray>()
        for (r in 0 until rows) for (c in 0 until cols) when (walls[r][c]) {
            '.' -> { dots[r][c] = true; dotsLeft++ }
            'o' -> { pellets[r][c] = true; dotsLeft++ }
            'P' -> { startC = c; startR = r }
            'G' -> gs.add(intArrayOf(c, r))
            '=' -> { exitC = c; exitR = r - 1 }
        }
        penC = gs.sortedBy { it[0] }[gs.size / 2][0]; penR = gs[0][1]
        buildPenDist()
        ghosts.clear()
        val n = ghostCount()
        for (i in 0 until n) {
            val s = gs[if (n == 2) (if (i == 0) 1 else 0) else i % gs.size]
            ghosts.add(Ghost(i, s[0], s[1]))
        }
        resetPositions()
        scatter = true; modeTimer = scatterTime()
    }

    private fun resetPositions() {
        player.c = startC; player.r = startR; player.dx = 0; player.dy = 0; player.t = 0f
        wantDx = 0; wantDy = 0; faceDx = -1; faceDy = 0
        for ((i, g) in ghosts.withIndex()) {
            g.c = g.spawnC; g.r = g.spawnR; g.dx = 0; g.dy = 0; g.t = 0f
            g.state = GhostState.HOUSE; g.releaseIn = 0.6f + i * 2.4f; g.reverseNext = false
        }
        scaredLeft = 0f; chain = 0
    }

    private fun buildPenDist() {
        penDist = Array(rows) { IntArray(cols) { Int.MAX_VALUE } }
        val q = ArrayDeque<IntArray>()
        penDist[penR][penC] = 0; q.add(intArrayOf(penC, penR))
        while (q.isNotEmpty()) {
            val (c, r) = q.poll().let { it[0] to it[1] }
            for (d in DIRS) {
                val nc = wrapC(c + d[0]); val nr = r + d[1]
                if (nr < 0 || nr >= rows || walls[nr][nc] == '#') continue
                if (penDist[nr][nc] == Int.MAX_VALUE) { penDist[nr][nc] = penDist[r][c] + 1; q.add(intArrayOf(nc, nr)) }
            }
        }
    }

    fun restart() {
        score = 0; lives = 3; levelClearedCount = 0
        loadLevel(1)
        phase = MazePhase.READY; phaseTimer = 2f
    }

    // ------------------------------------------------------------- helpers
    private fun wrapC(c: Int) = ((c % cols) + cols) % cols

    private fun passable(c: Int, r: Int, doorOk: Boolean): Boolean {
        if (r < 0 || r >= rows) return false
        val ch = walls[r][wrapC(c)]
        if (ch == '#') return false
        if (ch == '=') return doorOk
        return true
    }

    /** Called by the UI on swipe / button press. */
    fun setDirection(dx: Int, dy: Int) {
        if (phase == MazePhase.GAME_OVER) return
        wantDx = dx; wantDy = dy
        if (phase != MazePhase.PLAYING) return
        // Reversing in the middle of a corridor is allowed immediately.
        if ((player.dx != 0 || player.dy != 0) && dx == -player.dx && dy == -player.dy) {
            player.c = wrapC(player.c + player.dx); player.r += player.dy
            player.dx = dx; player.dy = dy
            player.t = 1f - player.t
            faceDx = dx; faceDy = dy
        } else if ((player.dx != 0 || player.dy != 0) && (dx != 0 || dy != 0) && dx != player.dx && dy != player.dy &&
            player.t <= 0.4f && passable(player.c + dx, player.r + dy, false)
        ) {
            // "Cornering": a turn pressed just after passing a junction still works (snaps back to the junction).
            player.dx = dx; player.dy = dy; player.t = 0f
            faceDx = dx; faceDy = dy
        }
    }

    // ------------------------------------------------------------- update
    fun update(dtIn: Float) {
        val dt = dtIn.coerceAtMost(0.05f)
        when (phase) {
            MazePhase.READY -> { phaseTimer -= dt; if (phaseTimer <= 0f) phase = MazePhase.PLAYING }
            MazePhase.DYING -> {
                phaseTimer -= dt
                if (phaseTimer <= 0f) {
                    lives--
                    if (lives <= 0) phase = MazePhase.GAME_OVER
                    else { resetPositions(); phase = MazePhase.READY; phaseTimer = 1.6f }
                }
            }
            MazePhase.LEVEL_DONE -> {
                phaseTimer -= dt
                if (phaseTimer <= 0f) { loadLevel(level + 1); phase = MazePhase.READY; phaseTimer = 2f }
            }
            MazePhase.GAME_OVER -> {}
            MazePhase.PLAYING -> play(dt)
        }
    }

    private fun play(dt: Float) {
        updateModes(dt)
        movePlayer(dt)
        for (g in ghosts) moveGhost(g, dt)
        collide()
        if (phase == MazePhase.PLAYING && dotsLeft == 0) {
            phase = MazePhase.LEVEL_DONE; phaseTimer = 2.2f; levelClearedCount++
            score += 500 + 100 * level
        }
    }

    private fun updateModes(dt: Float) {
        if (scaredLeft > 0f) {
            scaredLeft -= dt
            if (scaredLeft <= 0f) {
                scaredLeft = 0f
                for (g in ghosts) if (g.state == GhostState.SCARED) g.state = GhostState.NORMAL
            }
            return // chase / scatter clock pauses while ghosts are blue
        }
        modeTimer -= dt
        if (modeTimer <= 0f) {
            scatter = !scatter
            modeTimer = if (scatter) scatterTime() else chaseTime()
            for (g in ghosts) if (g.state == GhostState.NORMAL) g.reverseNext = true
        }
    }

    private fun movePlayer(dt: Float) {
        var step = playerSpeed() * dt
        var guard = 0
        while (step > 0f && guard++ < 8) {
            if (player.dx == 0 && player.dy == 0) {
                choosePlayerDir()
                if (player.dx == 0 && player.dy == 0) break
            }
            val remain = 1f - player.t
            if (step >= remain) {
                step -= remain
                player.c = wrapC(player.c + player.dx); player.r += player.dy; player.t = 0f
                arrivePlayer()
            } else { player.t += step; step = 0f }
        }
    }

    private fun choosePlayerDir() {
        if ((wantDx != 0 || wantDy != 0) && passable(player.c + wantDx, player.r + wantDy, false)) {
            player.dx = wantDx; player.dy = wantDy
        } else if (!(player.dx != 0 || player.dy != 0) || !passable(player.c + player.dx, player.r + player.dy, false)) {
            player.dx = 0; player.dy = 0
        }
        if (player.dx != 0 || player.dy != 0) { faceDx = player.dx; faceDy = player.dy }
    }

    private fun arrivePlayer() {
        val c = player.c; val r = player.r
        if (dots[r][c]) { dots[r][c] = false; dotsLeft--; score += 10 }
        if (pellets[r][c]) {
            pellets[r][c] = false; dotsLeft--; score += 50
            scaredLeft = scaredTime(); chain = 0
            for (g in ghosts) if (g.state == GhostState.NORMAL || g.state == GhostState.SCARED) {
                g.state = GhostState.SCARED; g.reverseNext = true
            }
        }
        choosePlayerDir()
    }

    private fun moveGhost(g: Ghost, dt: Float) {
        if (g.state == GhostState.HOUSE && g.releaseIn > 0f) { g.releaseIn -= dt; return }
        val sp = ghostSpeed() * when (g.state) {
            GhostState.SCARED -> 0.62f
            GhostState.EYES -> 2.1f
            else -> 1f
        }
        var step = sp * dt
        var guard = 0
        while (step > 0f && guard++ < 8) {
            if (g.dx == 0 && g.dy == 0) {
                chooseGhostDir(g)
                if (g.dx == 0 && g.dy == 0) break
            }
            val remain = 1f - g.t
            if (step >= remain) {
                step -= remain
                g.c = wrapC(g.c + g.dx); g.r += g.dy; g.t = 0f
                arriveGhost(g)
            } else { g.t += step; step = 0f }
        }
    }

    private fun arriveGhost(g: Ghost) {
        if (g.state == GhostState.HOUSE && g.c == exitC && g.r == exitR) g.state = if (scaredLeft > 0f) GhostState.SCARED else GhostState.NORMAL
        if (g.state == GhostState.EYES && g.c == penC && g.r == penR) { g.state = GhostState.HOUSE; g.releaseIn = 1.2f; g.dx = 0; g.dy = 0; return }
        chooseGhostDir(g)
    }

    private fun chooseGhostDir(g: Ghost) {
        val doorOk = g.state == GhostState.HOUSE || g.state == GhostState.EYES
        val moving = g.dx != 0 || g.dy != 0
        if (g.state == GhostState.EYES) {
            var best: IntArray? = null; var bd = Int.MAX_VALUE
            for (d in DIRS) {
                val nc = wrapC(g.c + d[0]); val nr = g.r + d[1]
                if (!passable(nc, nr, true)) continue
                val pd = penDist[nr][nc]
                if (pd < bd) { bd = pd; best = d }
            }
            g.dx = best?.get(0) ?: 0; g.dy = best?.get(1) ?: 0
            return
        }
        val cands = ArrayList<IntArray>(4)
        for (d in DIRS) {
            if (!passable(g.c + d[0], g.r + d[1], doorOk)) continue
            if (moving && d[0] == -g.dx && d[1] == -g.dy) continue
            cands.add(d)
        }
        if (g.reverseNext && moving && g.state != GhostState.HOUSE) {
            g.reverseNext = false
            if (passable(g.c - g.dx, g.r - g.dy, doorOk)) { g.dx = -g.dx; g.dy = -g.dy; return }
        }
        if (cands.isEmpty()) {
            // dead end: turn around
            if (moving && passable(g.c - g.dx, g.r - g.dy, doorOk)) { g.dx = -g.dx; g.dy = -g.dy } else { g.dx = 0; g.dy = 0 }
            return
        }
        val pick: IntArray = when {
            g.state == GhostState.SCARED -> cands[rnd.nextInt(cands.size)]
            g.state == GhostState.HOUSE -> nearest(cands, g, exitC, exitR)
            rnd.nextFloat() < wander() -> cands[rnd.nextInt(cands.size)]
            else -> { val t = target(g); nearest(cands, g, t[0], t[1]) }
        }
        g.dx = pick[0]; g.dy = pick[1]
    }

    private fun nearest(cands: List<IntArray>, g: Ghost, tc: Int, tr: Int): IntArray {
        var best = cands[0]; var bd = Int.MAX_VALUE
        for (d in cands) {   // DIRS order = up, left, down, right = classic tie-break
            val nc = g.c + d[0]; val nr = g.r + d[1]
            val dd = (nc - tc) * (nc - tc) + (nr - tr) * (nr - tr)
            if (dd < bd) { bd = dd; best = d }
        }
        return best
    }

    private fun target(g: Ghost): IntArray {
        val pc = player.c; val pr = player.r
        if (scatter) return when (g.kind) {
            0 -> intArrayOf(cols - 1, -2)
            1 -> intArrayOf(0, -2)
            2 -> intArrayOf(cols - 1, rows + 1)
            else -> intArrayOf(0, rows + 1)
        }
        return when (g.kind) {
            0 -> intArrayOf(pc, pr)
            1 -> intArrayOf(pc + 4 * faceDx, pr + 4 * faceDy)
            2 -> {
                val b = ghosts[0]
                val ac = pc + 2 * faceDx; val ar = pr + 2 * faceDy
                intArrayOf(ac + (ac - b.c), ar + (ar - b.r))
            }
            else -> {
                val d2 = (g.c - pc) * (g.c - pc) + (g.r - pr) * (g.r - pr)
                if (d2 > 64) intArrayOf(pc, pr) else intArrayOf(0, rows + 1)
            }
        }
    }

    private fun collide() {
        for (g in ghosts) {
            if (g.state != GhostState.NORMAL && g.state != GhostState.SCARED) continue
            val ddx = g.x - player.x; val ddy = g.y - player.y
            if (ddx * ddx + ddy * ddy < 0.42f) {
                if (g.state == GhostState.SCARED) {
                    score += 200 * (1 shl minOf(chain, 3)); chain++
                    g.state = GhostState.EYES
                } else {
                    phase = MazePhase.DYING; phaseTimer = 1.5f
                    return
                }
            }
        }
    }

    companion object {
        /** up, left, down, right */
        val DIRS = arrayOf(intArrayOf(0, -1), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(1, 0))
    }
}
