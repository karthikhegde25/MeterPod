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
 * Horizontal level bar (matches the Metal Detector tab's visual style):
 * fills left-to-right based on the current normalized level, green-to-red
 * gradient, with a thin vertical peak-hold marker that stays at the
 * highest level seen since the last reset.
 */
class SoundLevelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var level = 0f
    private var peakLevel = 0f

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

    private val peakMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#E4E8EC")
    }

    fun setLevel(normalizedLevel: Float, normalizedPeak: Float) {
        level = normalizedLevel.coerceIn(0f, 1f)
        peakLevel = normalizedPeak.coerceIn(0f, 1f)
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
                floatArrayOf(0f, 0.65f, 1f),
                Shader.TileMode.CLAMP
            )
        }

        val fillWidth = w * level
        if (fillWidth > 0f) {
            val fillRect = RectF(0f, 0f, fillWidth, h)
            canvas.drawRoundRect(fillRect, corner, corner, fillPaint)
        }

        canvas.drawRoundRect(track, corner, corner, borderPaint)

        val peakX = w * peakLevel
        canvas.drawLine(peakX, 0f, peakX, h, peakMarkerPaint)
    }
}
