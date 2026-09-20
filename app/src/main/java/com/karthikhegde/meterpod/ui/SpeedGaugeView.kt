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
 * A half-circle speedometer: 0 at the left, max speed at the right, needle
 * sweeping up and over through the top as speed increases. Uses Android's
 * Canvas.drawArc angle convention directly (0deg = 3 o'clock, clockwise
 * positive), so the needle math and the background arc share the same
 * angle system without any sign-flipping.
 */
class SpeedGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var maxSpeedKmh = 180f
        set(value) {
            field = value
            invalidate()
        }

    private var speedKmh = 0f

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 14f
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#232A32")
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 14f
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#3A7BD5")
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#5A6470")
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9AA5B1")
        textAlign = Paint.Align.CENTER
    }

    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#FF5252")
    }

    private val pivotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FF5252")
    }

    fun setSpeedKmh(speed: Float) {
        speedKmh = speed.coerceIn(0f, maxSpeedKmh)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h * 0.92f
        val radius = min(w / 2f, h) * 0.82f

        labelPaint.textSize = radius * 0.12f

        val arcRect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        // Background track (full range) then a progress arc up to the current speed.
        canvas.drawArc(arcRect, 180f, 180f, false, trackPaint)
        val progressSweep = 180f * (speedKmh / maxSpeedKmh).coerceIn(0f, 1f)
        canvas.drawArc(arcRect, 180f, progressSweep, false, progressPaint)

        // Ticks + labels every 20 km/h (assuming a 180 km/h range; scales with maxSpeedKmh).
        val step = (maxSpeedKmh / 9f).let { raw -> (raw / 10f).let { if (it < 1f) 10f else Math.round(it) * 10f } }
        var mark = 0f
        while (mark <= maxSpeedKmh + 0.01f) {
            val t = mark / maxSpeedKmh
            val angleDeg = 180f + t * 180f
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val outerR = radius + 4f
            val innerR = radius - 16f
            val x1 = cx + innerR * cos(angleRad).toFloat()
            val y1 = cy + innerR * sin(angleRad).toFloat()
            val x2 = cx + outerR * cos(angleRad).toFloat()
            val y2 = cy + outerR * sin(angleRad).toFloat()
            canvas.drawLine(x1, y1, x2, y2, tickPaint)

            val labelR = radius - 34f
            val lx = cx + labelR * cos(angleRad).toFloat()
            val ly = cy + labelR * sin(angleRad).toFloat() - labelPaint.ascent() / 2f
            canvas.drawText(mark.toInt().toString(), lx, ly, labelPaint)

            mark += step
        }

        // Needle
        val t = (speedKmh / maxSpeedKmh).coerceIn(0f, 1f)
        val needleAngleDeg = 180f + t * 180f
        val needleAngleRad = Math.toRadians(needleAngleDeg.toDouble())
        val needleLength = radius - 20f
        val nx = cx + needleLength * cos(needleAngleRad).toFloat()
        val ny = cy + needleLength * sin(needleAngleRad).toFloat()
        canvas.drawLine(cx, cy, nx, ny, needlePaint)
        canvas.drawCircle(cx, cy, 12f, pivotPaint)
    }
}
