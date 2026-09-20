package com.karthikhegde.meterpod.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * Draws the most recent audio buffer as a simple oscilloscope-style line
 * graph: x = sample index, y = sample amplitude (-1..1), centered
 * vertically. Purely visual — no scale/units, just a live sense of the
 * waveform's shape and loudness.
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var samples: FloatArray = FloatArray(0)

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1B2127")
    }

    private val centerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#3A424B")
    }

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#3A7BD5")
        strokeJoin = Paint.Join.ROUND
    }

    private val path = Path()

    fun setSamples(newSamples: FloatArray) {
        samples = newSamples
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        canvas.drawRect(0f, 0f, w, h, backgroundPaint)

        val midY = h / 2f
        canvas.drawLine(0f, midY, w, midY, centerLinePaint)

        if (samples.isEmpty()) return

        path.reset()
        val step = w / (samples.size - 1).coerceAtLeast(1)
        for (i in samples.indices) {
            val x = i * step
            // Amplify slightly so quieter sounds are still visible, then clamp.
            val amplified = (samples[i] * 3.5f).coerceIn(-1f, 1f)
            val y = midY - amplified * (midY * 0.9f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, wavePaint)
    }
}
