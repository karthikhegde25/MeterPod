package com.karthikhegde.meterpod.games

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** One pair of pipes: the gap runs from [gapTop] to [gapTop] + the game's current gap height. */
class Pipe(var x: Float, val gapTop: Float, var scored: Boolean = false)

enum class FlappyState { READY, PLAYING, DEAD }

/**
 * Flappy Bird physics and rules in a fixed 360 x 640 world (the screen scales it).
 * The bird falls with gravity, flaps upward on tap, tilts with its vertical speed, pipes scroll in from the
 * right with a randomly placed gap, speed rises with the score, and background layers scroll at different
 * speeds for parallax.
 */
class FlappyGame(private val rnd: Random = Random.Default) {
    companion object {
        const val W = 360f
        const val H = 640f
        const val GROUND_H = 84f
        const val BIRD_X = 96f
        const val BIRD_R = 15f
        const val PIPE_W = 62f
        const val PIPE_SPACING = 210f
        const val GRAVITY = 1500f
        const val FLAP_V = -460f
        const val MAX_FALL = 760f
        const val GROUND_Y = H - GROUND_H
    }

    var state = FlappyState.READY; private set
    var birdY = H * 0.42f; private set
    var birdV = 0f; private set
    var score = 0; private set
    var time = 0f; private set          // seconds since start, drives wing flapping and idle bobbing
    val pipes = ArrayList<Pipe>()

    // parallax scroll offsets (world units): clouds slowest, hills medium, ground at pipe speed
    var cloudOff = 0f; private set
    var hillOff = 0f; private set
    var groundOff = 0f; private set

    val gap: Float get() = max(128f, 168f - score * 1.5f)
    val speed: Float get() = min(220f, 120f + score * 4f)

    /** Tilt in degrees: nose up while rising, diving as it falls. */
    val rotation: Float get() = when (state) {
        FlappyState.READY -> 0f
        else -> (birdV * 0.085f).coerceIn(-25f, 85f)
    }

    fun reset() {
        state = FlappyState.READY
        birdY = H * 0.42f; birdV = 0f; score = 0; time = 0f
        pipes.clear()
    }

    fun flap() {
        when (state) {
            FlappyState.READY -> { state = FlappyState.PLAYING; birdV = FLAP_V; spawnFirst() }
            FlappyState.PLAYING -> birdV = FLAP_V
            FlappyState.DEAD -> {}
        }
    }

    private fun spawnFirst() {
        pipes.clear()
        pipes.add(newPipe(W + 80f))
    }

    private fun newPipe(x: Float): Pipe {
        val g = gap
        val minTop = 70f
        val maxTop = GROUND_Y - 70f - g
        return Pipe(x, minTop + rnd.nextFloat() * (maxTop - minTop))
    }

    fun update(dtIn: Float) {
        val dt = min(dtIn, 0.034f)
        time += dt
        when (state) {
            FlappyState.READY -> {
                birdY = H * 0.42f + kotlin.math.sin(time * 4f) * 8f
                scroll(dt, 100f)
            }
            FlappyState.PLAYING -> {
                birdV = min(MAX_FALL, birdV + GRAVITY * dt)
                birdY += birdV * dt
                scroll(dt, speed)
                for (p in pipes) p.x -= speed * dt
                // award a point when the bird has passed a pipe pair
                for (p in pipes) if (!p.scored && p.x + PIPE_W < BIRD_X - BIRD_R) { p.scored = true; score++ }
                // remove pipes that left the screen, keep generating on the right
                pipes.removeAll { it.x + PIPE_W < -10f }
                val last = pipes.lastOrNull()
                if (last == null || last.x < W - PIPE_SPACING + 40f) pipes.add(newPipe((last?.x ?: W) + PIPE_SPACING))
                // top of the screen: stop at the edge instead of leaving it
                if (birdY - BIRD_R < 0f) { birdY = BIRD_R; if (birdV < 0f) birdV = 0f }
                if (collides()) state = FlappyState.DEAD
            }
            FlappyState.DEAD -> {
                // fall to the ground
                if (birdY + BIRD_R < GROUND_Y) { birdV = min(MAX_FALL, birdV + GRAVITY * dt); birdY = min(GROUND_Y - BIRD_R, birdY + birdV * dt) }
            }
        }
    }

    private fun scroll(dt: Float, s: Float) {
        cloudOff += s * 0.15f * dt
        hillOff += s * 0.4f * dt
        groundOff += s * dt
    }

    private fun collides(): Boolean {
        if (birdY + BIRD_R >= GROUND_Y) { birdY = GROUND_Y - BIRD_R; return true }
        val g = gap
        for (p in pipes) {
            if (circleRect(BIRD_X, birdY, BIRD_R * 0.92f, p.x, 0f, PIPE_W, p.gapTop)) return true
            if (circleRect(BIRD_X, birdY, BIRD_R * 0.92f, p.x, p.gapTop + g, PIPE_W, GROUND_Y - (p.gapTop + g))) return true
        }
        return false
    }

    private fun circleRect(cx: Float, cy: Float, r: Float, rx: Float, ry: Float, rw: Float, rh: Float): Boolean {
        val nx = cx.coerceIn(rx, rx + rw); val ny = cy.coerceIn(ry, ry + rh)
        val dx = cx - nx; val dy = cy - ny
        return dx * dx + dy * dy < r * r
    }
}
