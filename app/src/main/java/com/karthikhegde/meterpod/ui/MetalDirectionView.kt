package com.karthikhegde.meterpod.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A simple ringed arrow that rotates to point toward the horizontal
 * direction (relative to how the phone is currently held) that the local
 * magnetic field anomaly is strongest in. Dims out when there's no
 * meaningful signal to point at.
 */
class MetalDirectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var bearingDeg = 0f
    private var active = false

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#3A424B")
    }

    private val arrowActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FF5252")
    }

    private val arrowInactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#3A424B")
    }

    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#9AA5B1")
    }

    /** bearingDeg: 0 = toward the top of the phone, 90 = toward the right edge, etc. */
    fun setBearing(bearingDegrees: Float, isActive: Boolean) {
        bearingDeg = bearingDegrees
        active = isActive
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h / 2f
        val radius = min(w, h) / 2f * 0.85f

        canvas.drawCircle(cx, cy, radius, ringPaint)

        canvas.save()
        canvas.rotate(bearingDeg, cx, cy)

        val arrowLength = radius * 0.8f
        val arrowHalfWidth = radius * 0.16f
        val tipY = cy - arrowLength
        val path = Path().apply {
            moveTo(cx, tipY)
            lineTo(cx - arrowHalfWidth, cy - arrowLength * 0.35f)
            lineTo(cx - arrowHalfWidth * 0.4f, cy - arrowLength * 0.35f)
            lineTo(cx - arrowHalfWidth * 0.4f, cy + radius * 0.5f)
            lineTo(cx + arrowHalfWidth * 0.4f, cy + radius * 0.5f)
            lineTo(cx + arrowHalfWidth * 0.4f, cy - arrowLength * 0.35f)
            lineTo(cx + arrowHalfWidth, cy - arrowLength * 0.35f)
            close()
        }
        canvas.drawPath(path, if (active) arrowActivePaint else arrowInactivePaint)
        canvas.restore()

        canvas.drawCircle(cx, cy, radius * 0.06f, centerDotPaint)
    }
}
