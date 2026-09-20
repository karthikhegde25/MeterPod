package com.karthikhegde.meterpod.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws a quarter-circle protractor with a needle showing the current
 * acute angle (0..90 degrees) between the phone's plane and the ground.
 */
class AngleDialView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var angleDegrees = 0f

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.parseColor("#3A424B")
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 14f
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#3A7BD5")
    }

    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#FF5252")
    }

    private val pivotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FF5252")
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#5A6470")
    }

    fun setAngle(angle: Float) {
        angleDegrees = angle.coerceIn(0f, 90f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w * 0.12f
        val cy = h * 0.88f
        val radius = min(w, h) * 0.75f

        // Ground line (horizontal reference)
        canvas.drawLine(cx, cy, cx + radius * 1.05f, cy, basePaint)
        // Vertical reference (90 degrees)
        canvas.drawLine(cx, cy, cx, cy - radius * 1.05f, basePaint)

        // Background quarter arc
        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(rect, 270f, 90f, false, tickPaint)

        // Progress arc from 0 to current angle (angle measured up from horizontal)
        canvas.drawArc(rect, 360f, -angleDegrees, false, arcPaint)

        // Tick marks every 15 degrees
        for (deg in 0..90 step 15) {
            val rad = Math.toRadians((-deg).toDouble())
            val x1 = cx + (radius * 0.92f) * cos(rad).toFloat()
            val y1 = cy + (radius * 0.92f) * sin(rad).toFloat()
            val x2 = cx + radius * cos(rad).toFloat()
            val y2 = cy + radius * sin(rad).toFloat()
            canvas.drawLine(x1, y1, x2, y2, tickPaint)
        }

        // Needle pointing at the current angle
        val rad = Math.toRadians((-angleDegrees).toDouble())
        val nx = cx + radius * cos(rad).toFloat()
        val ny = cy + radius * sin(rad).toFloat()
        canvas.drawLine(cx, cy, nx, ny, needlePaint)
        canvas.drawCircle(cx, cy, 10f, pivotPaint)
    }
}
