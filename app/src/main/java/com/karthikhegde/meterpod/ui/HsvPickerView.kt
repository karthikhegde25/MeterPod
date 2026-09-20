package com.karthikhegde.meterpod.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * A classic HSV color picker: a saturation/value gradient square on the
 * left (horizontal = saturation, vertical = value, tinted by the current
 * hue) plus a full-spectrum hue strip on the right. Dragging in either
 * region updates the selected color and reports it via [onColorChanged].
 */
class HsvPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onColorChanged: ((Int) -> Unit)? = null

    /** When true, this instance is purely decorative (a tile icon) and never handles touch. */
    var isIconPreview = false

    private var hue = 0f        // 0..360
    private var saturation = 1f // 0..1
    private var value = 1f      // 0..1

    private val hueStripWidthFraction = 0.16f
    private val stripGap = 16f

    private val svBasePaint = Paint()
    private val svShadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val huePaint = Paint()
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
    }
    private val markerShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#55000000")
    }
    private val hueMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
    }

    fun setColor(argb: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(argb, hsv)
        hue = hsv[0]; saturation = hsv[1]; value = hsv[2]
        invalidate()
    }

    fun currentColor(): Int = Color.HSVToColor(floatArrayOf(hue, saturation, value))

    private fun svRect(): RectF {
        val w = width.toFloat()
        val h = height.toFloat()
        val svWidth = w * (1f - hueStripWidthFraction) - stripGap
        return RectF(0f, 0f, svWidth, h)
    }

    private fun hueRect(): RectF {
        val w = width.toFloat()
        val h = height.toFloat()
        val svWidth = w * (1f - hueStripWidthFraction) - stripGap
        return RectF(svWidth + stripGap, 0f, w, h)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isIconPreview) return false
        val sv = svRect()
        val hueBar = hueRect()

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (event.x <= sv.right) {
                    saturation = ((event.x - sv.left) / sv.width()).coerceIn(0f, 1f)
                    value = (1f - (event.y - sv.top) / sv.height()).coerceIn(0f, 1f)
                    invalidate()
                    onColorChanged?.invoke(currentColor())
                    return true
                } else if (event.x >= hueBar.left) {
                    hue = ((event.y - hueBar.top) / hueBar.height() * 360f).coerceIn(0f, 360f)
                    invalidate()
                    onColorChanged?.invoke(currentColor())
                    return true
                }
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val sv = svRect()
        val hueBar = hueRect()

        // Saturation/value square: white -> hue color gradient (saturation),
        // with a transparent -> black gradient layered on top (value).
        val hueColor = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        svBasePaint.shader = LinearGradient(
            sv.left, 0f, sv.right, 0f,
            Color.WHITE, hueColor, Shader.TileMode.CLAMP
        )
        canvas.drawRect(sv, svBasePaint)

        svShadePaint.shader = LinearGradient(
            0f, sv.top, 0f, sv.bottom,
            Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP
        )
        canvas.drawRect(sv, svShadePaint)

        // Hue strip: full spectrum top-to-bottom.
        val hueColors = IntArray(13) { i -> Color.HSVToColor(floatArrayOf(i * 30f, 1f, 1f)) }
        huePaint.shader = LinearGradient(
            0f, hueBar.top, 0f, hueBar.bottom,
            hueColors, null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(hueBar, huePaint)

        // SV marker
        val markerX = sv.left + saturation * sv.width()
        val markerY = sv.top + (1f - value) * sv.height()
        canvas.drawCircle(markerX, markerY, 14f, markerShadowPaint)
        canvas.drawCircle(markerX, markerY, 14f, markerPaint)

        // Hue marker (a horizontal notch on the strip)
        val hueMarkerY = hueBar.top + (hue / 360f) * hueBar.height()
        canvas.drawLine(hueBar.left - 4f, hueMarkerY, hueBar.right + 4f, hueMarkerY, hueMarkerPaint)
    }
}
