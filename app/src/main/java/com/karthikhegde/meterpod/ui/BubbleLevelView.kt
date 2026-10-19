package com.karthikhegde.meterpod.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * 3D circular bubble level: chrome bezel, recessed glass face with concentric guide rings, and a glossy
 * bubble sphere (with its own shadow) that turns green when the surface is level.
 */
class BubbleLevelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var rollDeg = 0f   // tilt left/right
    private var pitchDeg = 0f  // tilt front/back

    private val maxTiltForFullOffset = 20f
    private val levelToleranceDeg = 0.7f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setTilt(roll: Float, pitch: Float) {
        rollDeg = roll
        pitchDeg = pitch
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = w / 2f; val cy = h / 2f
        val outer = min(w, h) / 2f * 0.92f
        val bezel = outer * 0.10f
        val face = outer - bezel
        val bubbleRadius = face * 0.17f
        val travel = face - bubbleRadius

        Draw3D.dropShadow(canvas, cx + outer * 0.03f, cy + outer * 0.06f, outer * 1.05f, p)
        Draw3D.chromeRing(canvas, cx, cy, outer - bezel / 2f, bezel, p)
        Draw3D.dialFace(canvas, cx, cy, face, p)

        // guide rings (lit top-left edge, dark bottom-right) and crosshair
        p.style = Paint.Style.STROKE
        for ((k, f) in listOf(0.33f, 0.66f, 1f).withIndex()) {
            p.strokeWidth = face * 0.012f
            p.color = Draw3D.c("#5B6E93")
            canvas.drawCircle(cx, cy, face * f * (if (k == 2) 0.97f else 1f), p)
        }
        p.strokeWidth = face * 0.008f
        p.color = Draw3D.c("#4A5C80")
        canvas.drawLine(cx - face, cy, cx + face, cy, p)
        canvas.drawLine(cx, cy - face, cx, cy + face, p)
        p.style = Paint.Style.FILL

        val nx = (rollDeg / maxTiltForFullOffset).coerceIn(-1f, 1f)
        val ny = (pitchDeg / maxTiltForFullOffset).coerceIn(-1f, 1f)
        val bx = cx + nx * travel
        val by = cy - ny * travel
        val level = kotlin.math.sqrt(rollDeg * rollDeg + pitchDeg * pitchDeg) <= levelToleranceDeg

        // target ring glows when level
        if (level) {
            p.style = Paint.Style.STROKE; p.strokeWidth = face * 0.02f; p.color = Draw3D.c("#884CD964")
            canvas.drawCircle(cx, cy, bubbleRadius * 1.25f, p); p.style = Paint.Style.FILL
        }
        Draw3D.dropShadow(canvas, bx + bubbleRadius * 0.25f, by + bubbleRadius * 0.4f, bubbleRadius * 1.3f, p)
        if (level) Draw3D.sphere(canvas, bx, by, bubbleRadius, "#C8FFD5", "#3FD16A", "#0F6B2C", p)
        else Draw3D.sphere(canvas, bx, by, bubbleRadius, "#FFD0CC", "#FF5252", "#8E1B1B", p)

        Draw3D.glass(canvas, cx, cy, face, p)
    }
}
