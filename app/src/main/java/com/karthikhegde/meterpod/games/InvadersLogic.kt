package com.karthikhegde.meterpod.games

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class Alien(val col: Int, val row: Int, var alive: Boolean = true)
class Shot(var x: Float, var y: Float, val vy: Float)
class Boom(val x: Float, val y: Float, var t: Float = 0f, val big: Boolean = false)

enum class InvState { READY, PLAYING, WAVE_CLEAR, GAME_OVER }

/**
 * Space Invaders rules in a fixed 360 x 560 world: a ship near the bottom, a formation of aliens that
 * sweeps sideways and steps down at each edge (faster as fewer remain), limited player shots, aliens that
 * shoot back, destructible shields, lives, waves, score and a short explosion animation.
 */
class InvadersGame(private val rnd: Random = Random.Default) {
    companion object {
        const val W = 360f
        const val H = 560f
        const val COLS = 8
        const val ROWS = 5
        const val ALIEN_W = 24f
        const val ALIEN_H = 16f
        const val CELL_X = 34f
        const val CELL_Y = 28f
        const val SHIP_W = 34f
        const val SHIP_H = 16f
        const val SHIP_Y = H - 46f
        const val MAX_PLAYER_SHOTS = 3
        const val SHIELD_COLS = 16
        const val SHIELD_ROWS = 9
        const val SHIELD_CELL = 3.5f
        const val SHIELD_Y = H - 128f
        const val INVASION_Y = SHIP_Y - 4f
    }

    var state = InvState.READY; private set
    var score = 0; private set
    var lives = 3; private set
    var wave = 1; private set

    var shipX = W / 2f
    var moveDir = 0          // -1 left, 0 stop, 1 right (held buttons)
    var firing = false       // held Shoot button
    var invulnerable = 0f; private set

    val aliens = ArrayList<Alien>()
    var formX = 0f; private set
    var formY = 0f; private set
    private var dir = 1
    var animFrame = 0; private set
    private var animT = 0f

    val playerShots = ArrayList<Shot>()
    val enemyShots = ArrayList<Shot>()
    val booms = ArrayList<Boom>()
    /** shields[s][row * SHIELD_COLS + col] = true while that chunk is intact */
    val shields = Array(4) { BooleanArray(SHIELD_COLS * SHIELD_ROWS) }
    private var fireCooldown = 0f
    private var enemyCooldown = 1f
    var time = 0f; private set

    init { newWave(keepScore = true) }

    fun shieldX(i: Int): Float {
        val total = 4 * SHIELD_COLS * SHIELD_CELL
        val gap = (W - total) / 5f
        return gap + i * (SHIELD_COLS * SHIELD_CELL + gap)
    }

    fun alienX(a: Alien) = formX + a.col * CELL_X
    fun alienY(a: Alien) = formY + a.row * CELL_Y
    fun points(a: Alien) = when (a.row) { 0 -> 30; 1, 2 -> 20; else -> 10 }
    val aliveCount: Int get() = aliens.count { it.alive }

    private fun newWave(keepScore: Boolean) {
        if (!keepScore) { score = 0; lives = 3; wave = 1 }
        aliens.clear()
        for (r in 0 until ROWS) for (c in 0 until COLS) aliens.add(Alien(c, r))
        formX = (W - ((COLS - 1) * CELL_X + ALIEN_W)) / 2f
        formY = 40f + min(5, wave - 1) * 8f
        dir = 1
        playerShots.clear(); enemyShots.clear()
        enemyCooldown = 1.2f
        for (s in shields) java.util.Arrays.fill(s, true)
        // chip the bottom corners of each shield so they read as arches
        for (s in shields) for (r in SHIELD_ROWS - 3 until SHIELD_ROWS) for (c in 4..SHIELD_COLS - 5) if (r >= SHIELD_ROWS - 2 || c in 6..SHIELD_COLS - 7) s[r * SHIELD_COLS + c] = false
    }

    fun start() { if (state == InvState.READY) state = InvState.PLAYING }

    fun restart() {
        score = 0; lives = 3; wave = 1
        booms.clear(); invulnerable = 0f; shipX = W / 2f
        newWave(keepScore = true)
        state = InvState.PLAYING
    }

    fun nextWave() {
        wave++
        booms.clear()
        newWave(keepScore = true)
        state = InvState.PLAYING
    }

    fun moveTo(x: Float) { shipX = x.coerceIn(SHIP_W / 2f + 4f, W - SHIP_W / 2f - 4f) }

    private fun fireShot() {
        if (playerShots.size >= MAX_PLAYER_SHOTS || fireCooldown > 0f) return
        playerShots.add(Shot(shipX, SHIP_Y - 8f, -480f))
        fireCooldown = 0.26f
    }

    fun shootOnce() { if (state == InvState.READY) start(); if (state == InvState.PLAYING) fireShot() }

    fun update(dtIn: Float) {
        val dt = min(dtIn, 0.034f)
        time += dt
        booms.forEach { it.t += dt }
        booms.removeAll { it.t > 0.4f }
        if (state != InvState.PLAYING) return

        invulnerable = max(0f, invulnerable - dt)
        fireCooldown = max(0f, fireCooldown - dt)
        moveTo(shipX + moveDir * 230f * dt)
        if (firing) fireShot()

        // ---- formation: sideways sweep, reverse and step down at the edges, speeds up as aliens die
        val total = ROWS * COLS
        val alive = aliveCount
        val speed = (20f + wave * 5f) * (1f + 2.0f * (1f - alive / total.toFloat()))
        formX += dir * speed * dt
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = 0f
        for (a in aliens) if (a.alive) { minX = min(minX, alienX(a)); maxX = max(maxX, alienX(a) + ALIEN_W); maxY = max(maxY, alienY(a) + ALIEN_H) }
        if (alive > 0) {
            if (dir > 0 && maxX >= W - 8f) { dir = -1; formY += 12f; formX -= (maxX - (W - 8f)) }
            else if (dir < 0 && minX <= 8f) { dir = 1; formY += 12f; formX += (8f - minX) }
        }
        animT += dt * (1f + (1f - alive / total.toFloat()) * 2f)
        if (animT > 0.5f) { animT = 0f; animFrame = 1 - animFrame }

        // ---- player shots
        val it = playerShots.iterator()
        while (it.hasNext()) {
            val s = it.next()
            s.y += s.vy * dt
            if (s.y < -10f) { it.remove(); continue }
            if (hitShield(s.x, s.y)) { it.remove(); continue }
            var hit = false
            for (a in aliens) {
                if (!a.alive) continue
                val ax = alienX(a); val ay = alienY(a)
                if (s.x >= ax - 2f && s.x <= ax + ALIEN_W + 2f && s.y >= ay && s.y <= ay + ALIEN_H) {
                    a.alive = false; score += points(a) * 1
                    booms.add(Boom(ax + ALIEN_W / 2f, ay + ALIEN_H / 2f))
                    hit = true; break
                }
            }
            if (hit) it.remove()
        }

        // ---- enemy fire
        enemyCooldown -= dt
        if (enemyCooldown <= 0f && enemyShots.size < 3 + min(3, wave / 2) && alive > 0) {
            val cols = aliens.filter { it.alive }.groupBy { it.col }
            val col = cols.keys.random(rnd)
            val shooter = cols[col]!!.maxByOrNull { it.row }!!
            enemyShots.add(Shot(alienX(shooter) + ALIEN_W / 2f, alienY(shooter) + ALIEN_H, 170f + wave * 12f))
            enemyCooldown = max(0.45f, (1.5f - wave * 0.1f)) * (0.5f + rnd.nextFloat())
        }
        val ei = enemyShots.iterator()
        while (ei.hasNext()) {
            val s = ei.next()
            s.y += s.vy * dt
            if (s.y > H) { ei.remove(); continue }
            if (hitShield(s.x, s.y)) { ei.remove(); continue }
            if (invulnerable <= 0f && s.y >= SHIP_Y - 6f && s.y <= SHIP_Y + SHIP_H && kotlin.math.abs(s.x - shipX) <= SHIP_W / 2f) {
                ei.remove()
                lives--
                booms.add(Boom(shipX, SHIP_Y + 4f, big = true))
                invulnerable = 1.6f
                enemyShots.clear()
                if (lives <= 0) { state = InvState.GAME_OVER; return }
                break
            }
        }

        // aliens chew through shields they touch
        for (a in aliens) if (a.alive) eraseShieldsOver(alienX(a), alienY(a), ALIEN_W, ALIEN_H)

        // ---- end conditions
        if (aliveCount == 0) { state = InvState.WAVE_CLEAR; return }
        var lowest = 0f
        for (a in aliens) if (a.alive) lowest = max(lowest, alienY(a) + ALIEN_H)
        if (lowest >= INVASION_Y) { state = InvState.GAME_OVER; booms.add(Boom(shipX, SHIP_Y + 4f, big = true)) }
    }

    /** Removes the shield chunk under (x, y) and its neighbours; returns true if the shot hit something. */
    private fun hitShield(x: Float, y: Float): Boolean {
        for (i in 0 until 4) {
            val sx = shieldX(i)
            if (x < sx || x >= sx + SHIELD_COLS * SHIELD_CELL || y < SHIELD_Y || y >= SHIELD_Y + SHIELD_ROWS * SHIELD_CELL) continue
            val c = ((x - sx) / SHIELD_CELL).toInt(); val r = ((y - SHIELD_Y) / SHIELD_CELL).toInt()
            val s = shields[i]
            if (!s[r * SHIELD_COLS + c]) continue
            for (dr in -1..1) for (dc in -1..1) {
                val rr = r + dr; val cc = c + dc
                if (rr in 0 until SHIELD_ROWS && cc in 0 until SHIELD_COLS && (dr == 0 || dc == 0 || rnd.nextBoolean())) s[rr * SHIELD_COLS + cc] = false
            }
            return true
        }
        return false
    }

    private fun eraseShieldsOver(x: Float, y: Float, w: Float, h: Float) {
        for (i in 0 until 4) {
            val sx = shieldX(i)
            if (x + w < sx || x > sx + SHIELD_COLS * SHIELD_CELL || y + h < SHIELD_Y || y > SHIELD_Y + SHIELD_ROWS * SHIELD_CELL) continue
            for (r in 0 until SHIELD_ROWS) for (c in 0 until SHIELD_COLS) {
                val cx = sx + c * SHIELD_CELL; val cy = SHIELD_Y + r * SHIELD_CELL
                if (cx + SHIELD_CELL > x && cx < x + w && cy + SHIELD_CELL > y && cy < y + h) shields[i][r * SHIELD_COLS + c] = false
            }
        }
    }
}
