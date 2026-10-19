package com.karthikhegde.meterpod.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/**
 * Horizontal intensity bar showing how far the current magnetic field
 * reading has deviated from the calibrated baseline. Green near baseline,
 * shifting through yellow to red as the deviation grows.
 */
class MetalDetectorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** 0f = at baseline, 1f = at or beyond the "strong signal" threshold. */
    private var intensity = 0f

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1B2127")
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#3A424B")
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#E4E8EC")
    }

    fun setIntensity(value: Float) {
        intensity = value.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val corner = h / 2f
        val track = RectF(0f, 0f, w, h)
        canvas.drawRoundRect(track, corner, corner, trackPaint)

        if (fillPaint.shader == null) {
            fillPaint.shader = LinearGradient(
                0f, 0f, w, 0f,
                intArrayOf(
                    Color.parseColor("#4CD964"),
                    Color.parseColor("#FFC940"),
                    Color.parseColor("#FF5252")
                ),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
        }

        val fillWidth = w * intensity
        if (fillWidth > 0f) {
            val fillRect = RectF(0f, 0f, fillWidth, h)
            canvas.drawRoundRect(fillRect, corner, corner, fillPaint)
        }

        canvas.drawRoundRect(track, corner, corner, borderPaint)

        // Marker at the "strong signal" threshold, near the right edge.
        val markerX = w * 0.85f
        canvas.drawLine(markerX, 0f, markerX, h, markerPaint)
    }
}
