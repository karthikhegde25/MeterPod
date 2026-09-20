package com.karthikhegde.meterpod.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/** Simple bar chart for daily step history; the last entry (today) is highlighted. */
class StepHistoryChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    data class DayEntry(val label: String, val steps: Int, val isToday: Boolean)

    private var entries: List<DayEntry> = emptyList()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#3A424B")
    }

    private val todayBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#3A7BD5")
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9AA5B1")
        textAlign = Paint.Align.CENTER
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E4E8EC")
        textAlign = Paint.Align.CENTER
    }

    fun setEntries(newEntries: List<DayEntry>) {
        entries = newEntries
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f || entries.isEmpty()) return

        labelPaint.textSize = h * 0.06f
        valuePaint.textSize = h * 0.055f

        val maxSteps = max(entries.maxOf { it.steps }, 1)
        val chartBottom = h - labelPaint.textSize * 1.8f
        val chartTop = valuePaint.textSize * 1.4f
        val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)

        val slotWidth = w / entries.size
        val barWidth = slotWidth * 0.5f

        entries.forEachIndexed { index, entry ->
            val centerX = slotWidth * index + slotWidth / 2f
            val barHeight = chartHeight * (entry.steps.toFloat() / maxSteps)
            val barTop = chartBottom - barHeight
            val rect = RectF(centerX - barWidth / 2f, barTop, centerX + barWidth / 2f, chartBottom)
            canvas.drawRoundRect(rect, 6f, 6f, if (entry.isToday) todayBarPaint else barPaint)

            if (entry.steps > 0) {
                canvas.drawText(entry.steps.toString(), centerX, barTop - 8f, valuePaint)
            }
            canvas.drawText(entry.label, centerX, h - 6f, labelPaint)
        }
    }
}
