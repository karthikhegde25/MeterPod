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
 * Draws a compass rose that rotates opposite to the device heading, so the
 * "N" label always points toward true/magnetic north in the real world,
 * while a fixed triangular pointer at the top of the view always represents
 * the direction the top of the phone is currently facing.
 */
class CompassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var headingDeg = 0f

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.parseColor("#3A424B")
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#5A6470")
    }

    private val northTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.parseColor("#FF5252")
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E4E8EC")
        textAlign = Paint.Align.CENTER
    }

    private val northLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5252")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val interCardinalLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9AA5B1")
        textAlign = Paint.Align.CENTER
    }

    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#3A7BD5")
    }

    fun setHeading(heading: Float) {
        headingDeg = heading
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h / 2f
        val radius = min(w, h) / 2f * 0.82f

        labelPaint.textSize = radius * 0.14f
        northLabelPaint.textSize = radius * 0.16f
        interCardinalLabelPaint.textSize = radius * 0.10f

        canvas.save()
        canvas.rotate(-headingDeg, cx, cy)

        canvas.drawCircle(cx, cy, radius, ringPaint)

        // Cardinal points (N/E/S/W) every 90 degrees, intercardinal points
        // (NE/SE/SW/NW) every 90 degrees offset by 45, and plain tick marks
        // filling in the remaining 15-degree steps.
        val cardinalLabels = mapOf(0 to "N", 90 to "E", 180 to "S", 270 to "W")
        val interCardinalLabels = mapOf(45 to "NE", 135 to "SE", 225 to "SW", 315 to "NW")

        for (deg in 0 until 360 step 15) {
            val isCardinal = cardinalLabels.containsKey(deg)
            val isInterCardinal = interCardinalLabels.containsKey(deg)
            val rad = Math.toRadians((deg - 90).toDouble())
            val outerR = radius
            val innerR = when {
                isCardinal -> radius * 0.84f
                isInterCardinal -> radius * 0.88f
                else -> radius * 0.92f
            }
            val x1 = cx + innerR * cos(rad).toFloat()
            val y1 = cy + innerR * sin(rad).toFloat()
            val x2 = cx + outerR * cos(rad).toFloat()
            val y2 = cy + outerR * sin(rad).toFloat()
            canvas.drawLine(x1, y1, x2, y2, if (deg == 0) northTickPaint else tickPaint)

            val label = cardinalLabels[deg] ?: interCardinalLabels[deg]
            if (label != null) {
                val paint = when {
                    deg == 0 -> northLabelPaint
                    isCardinal -> labelPaint
                    else -> interCardinalLabelPaint
                }
                val labelR = if (isCardinal) radius * 0.68f else radius * 0.74f
                val lx = cx + labelR * cos(rad).toFloat()
                val ly = cy + labelR * sin(rad).toFloat() - paint.ascent() / 2f
                canvas.save()
                // Keep the letters upright even though the whole dial is rotated.
                canvas.rotate(headingDeg, lx, ly)
                canvas.drawText(label, lx, ly, paint)
                canvas.restore()
            }
        }

        canvas.restore()

        // Fixed pointer (not rotated) showing the direction the phone's top edge faces.
        val pointerHeight = radius * 0.22f
        val pointerHalfWidth = radius * 0.08f
        val tipY = cy - radius - 4f
        val path = Path().apply {
            moveTo(cx, tipY)
            lineTo(cx - pointerHalfWidth, tipY + pointerHeight)
            lineTo(cx + pointerHalfWidth, tipY + pointerHeight)
            close()
        }
        canvas.drawPath(path, pointerPaint)
    }
}
