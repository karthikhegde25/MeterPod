package com.karthikhegde.meterpod.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Classic circular bubble level. The bubble offsets from center based on
 * the roll (X) and pitch (Y) angles, and turns green when both are within
 * a small tolerance of zero (i.e. the surface is level).
 */
class BubbleLevelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var rollDeg = 0f   // tilt left/right
    private var pitchDeg = 0f  // tilt front/back

    // How many degrees of tilt correspond to the bubble reaching the edge
    private val maxTiltForFullOffset = 20f

    // Below this many degrees (combined), we consider it "level"
    private val levelToleranceDeg = 0.7f

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.parseColor("#3A424B")
    }

    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#5A6470")
    }

    private val bubbleOffPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FF5252")
    }

    private val bubbleOnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#4CD964")
    }

    fun setTilt(roll: Float, pitch: Float) {
        rollDeg = roll
        pitchDeg = pitch
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h / 2f
        val outerRadius = min(w, h) / 2f * 0.92f
        val bubbleRadius = outerRadius * 0.16f
        val travel = outerRadius - bubbleRadius

        // Outer ring + a couple of concentric guide rings
        canvas.drawCircle(cx, cy, outerRadius, ringPaint)
        canvas.drawCircle(cx, cy, outerRadius * 0.66f, ringPaint)
        canvas.drawCircle(cx, cy, outerRadius * 0.33f, ringPaint)

        // Crosshair
        canvas.drawLine(cx - outerRadius, cy, cx + outerRadius, cy, crosshairPaint)
        canvas.drawLine(cx, cy - outerRadius, cx, cy + outerRadius, crosshairPaint)

        // Bubble position: clamp tilt to +/- maxTiltForFullOffset, map to +/- travel
        val normalizedX = (rollDeg / maxTiltForFullOffset).coerceIn(-1f, 1f)
        val normalizedY = (pitchDeg / maxTiltForFullOffset).coerceIn(-1f, 1f)

        val bubbleX = cx + normalizedX * travel
        // Tilting the top of the phone up (positive pitch) should move the
        // bubble toward the top of the screen, so subtract.
        val bubbleY = cy - normalizedY * travel

        val combinedTilt = kotlin.math.sqrt(rollDeg * rollDeg + pitchDeg * pitchDeg)
        val isLevel = combinedTilt <= levelToleranceDeg

        canvas.drawCircle(bubbleX, bubbleY, bubbleRadius, if (isLevel) bubbleOnPaint else bubbleOffPaint)
    }
}
