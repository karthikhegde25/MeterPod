package com.karthikhegde.meterpod.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * A physically-scaled vertical ruler anchored to the right edge of the
 * screen, plus a draggable red measurement line with a center readout and
 * a lockable cursor.
 *
 * Scale comes from the device's reported display density
 * (`resources.displayMetrics.ydpi`), which Android reports relative to the
 * CURRENT screen orientation on essentially all modern devices. In practice
 * this is accurate to within a couple percent on most phones, but there's
 * no hardware calibration step available to a normal app, so treat it the
 * way any screen-ruler app's readings should be treated: a close estimate,
 * not a certified measurement.
 */
class RulerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** When true, draws a simplified decorative ruler (for the tool-grid tile icon) instead of a physically-scaled one. */
    var isIconPreview = false

    private var linePositionPx = 0f
    private var isLocked = false
    private var downWasOnLock = false

    var onValueChangedListener: ((Double) -> Unit)? = null

    private val rulerBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#F5F1E6")
    }

    private val rulerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#2B2F33")
    }

    private val majorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#1B1E21")
    }

    private val minorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.parseColor("#5A5A5A")
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1B1E21")
        textAlign = Paint.Align.LEFT
    }

    private val lineStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#E53935")
    }

    private val valueTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val valueBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#CC1B2127")
    }

    private val lockBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val lockShacklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    private var rulerWidthPx = 130f
    private val lockIconRadius = 34f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        linePositionPx = h / 2f
        rulerWidthPx = if (isIconPreview) w * 0.35f else 130f
        labelPaint.textSize = if (isIconPreview) 0f else 26f
        valueTextPaint.textSize = 52f
    }

    private fun pixelsPerCm(): Float {
        if (isIconPreview) {
            // Decorative scale sized to the view itself - a physically accurate
            // scale would be meaningless (and visually noisy) at icon size.
            return (height / 8f).coerceAtLeast(1f)
        }
        val metrics = resources.displayMetrics
        val dpi = if (metrics.ydpi > 0f) metrics.ydpi else metrics.densityDpi.toFloat()
        return dpi / 2.54f
    }

    private fun currentValueCm(): Double {
        return (linePositionPx / pixelsPerCm()).toDouble()
    }

    private fun lockIconCenter(): Pair<Float, Float> {
        // The lock icon rides along the red line rather than sitting at a fixed
        // spot, so it's always right where the measurement currently is.
        return (width / 2f) to linePositionPx
    }

    /** Nudges the line by a small amount (used by the fragment's +/- buttons); ignored while locked. */
    fun nudgeCm(deltaCm: Float) {
        if (isLocked) return
        val deltaPx = deltaCm * pixelsPerCm()
        linePositionPx = (linePositionPx + deltaPx).coerceIn(0f, height.toFloat())
        onValueChangedListener?.invoke(currentValueCm())
        invalidate()
    }

    private fun isPointOnLockIcon(x: Float, y: Float): Boolean {
        val (cx, cy) = lockIconCenter()
        val dx = x - cx
        val dy = y - cy
        val r = lockIconRadius * 1.4f
        return (dx * dx + dy * dy) <= r * r
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isIconPreview) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isPointOnLockIcon(event.x, event.y)) {
                    downWasOnLock = true
                    isLocked = !isLocked
                    invalidate()
                    return true
                }
                downWasOnLock = false
                if (!isLocked) updateLinePosition(event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!downWasOnLock && !isLocked) updateLinePosition(event.y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                downWasOnLock = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateLinePosition(y: Float) {
        linePositionPx = y.coerceIn(0f, height.toFloat())
        onValueChangedListener?.invoke(currentValueCm())
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val pxPerCm = pixelsPerCm()
        // On the real screen, the ruler is flush against the right edge. For the
        // small tile icon preview, center it instead - flush-right looks off-balance
        // at icon size where there's no "rest of the screen" context to justify it.
        val rulerRight = if (isIconPreview) (w + rulerWidthPx) / 2f else w
        val rulerLeft = rulerRight - rulerWidthPx
        val rulerRect = RectF(rulerLeft, 0f, rulerRight, h)

        canvas.drawRect(rulerRect, rulerBodyPaint)
        canvas.drawRect(rulerRect, rulerBorderPaint)

        // Ticks every mm, medium every 5mm, major (labeled) every cm.
        var mm = 0
        var yPx = 0f
        while (yPx <= h) {
            val isCm = mm % 10 == 0
            val isHalfCm = mm % 5 == 0
            val tickPaint = if (isCm) majorTickPaint else minorTickPaint
            val tickLength = when {
                isCm -> rulerWidthPx * 0.55f
                isHalfCm -> rulerWidthPx * 0.4f
                else -> rulerWidthPx * 0.25f
            }
            canvas.drawLine(rulerRight - tickLength, yPx, rulerRight, yPx, tickPaint)

            if (!isIconPreview && isCm && mm > 0) {
                canvas.drawText((mm / 10).toString(), rulerLeft + 6f, yPx + 9f, labelPaint)
            }

            mm += 1
            yPx = (mm / 10f) * pxPerCm
        }

        if (isIconPreview) return

        // Full-width red measurement line.
        canvas.drawLine(0f, linePositionPx, w, linePositionPx, lineStrokePaint)

        // Value readout, positioned on the left side (away from the ruler strip
        // on the right and the lock icon in the center).
        val valueText = String.format("%.3f cm", currentValueCm())
        val textWidth = valueTextPaint.measureText(valueText)
        val boxPadding = 20f
        val boxCenterY = h / 2f
        val minAnchorX = textWidth / 2f + boxPadding + 12f
        val anchorX = ((w - rulerWidthPx) * 0.32f).coerceAtLeast(minAnchorX)
        val boxRect = RectF(
            anchorX - textWidth / 2f - boxPadding,
            boxCenterY - 40f,
            anchorX + textWidth / 2f + boxPadding,
            boxCenterY + 40f
        )
        canvas.drawRoundRect(boxRect, 16f, 16f, valueBackgroundPaint)
        canvas.drawText(valueText, anchorX, boxCenterY + 16f, valueTextPaint)

        drawLockIcon(canvas)
    }

    private fun drawLockIcon(canvas: Canvas) {
        val (cx, cy) = lockIconCenter()
        val color = if (isLocked) Color.parseColor("#E53935") else Color.parseColor("#3A424B")
        lockBodyPaint.color = color
        lockShacklePaint.color = color

        val bodyRect = RectF(
            cx - lockIconRadius * 0.7f, cy - lockIconRadius * 0.1f,
            cx + lockIconRadius * 0.7f, cy + lockIconRadius * 0.9f
        )
        canvas.drawRoundRect(bodyRect, 8f, 8f, lockBodyPaint)

        val shackleRadius = lockIconRadius * 0.5f
        val shacklePath = Path()
        if (isLocked) {
            // Closed shackle, sitting directly over the body.
            shacklePath.addArc(
                RectF(cx - shackleRadius, cy - lockIconRadius * 1.0f, cx + shackleRadius, cy),
                180f, 180f
            )
        } else {
            // Open shackle, swung off to the side.
            shacklePath.addArc(
                RectF(cx - shackleRadius * 0.2f, cy - lockIconRadius * 1.0f, cx + shackleRadius * 1.8f, cy),
                180f, 180f
            )
        }
        canvas.drawPath(shacklePath, lockShacklePaint)
    }
}
