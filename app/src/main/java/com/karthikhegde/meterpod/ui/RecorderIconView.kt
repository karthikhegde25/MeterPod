package com.karthikhegde.meterpod.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Decorative glossy microphone for the Recorder tile. Everything is drawn symmetrically about the
 * centre, inside the view's bounds, with no animation, so the tile looks identical every time.
 */
class RecorderIconView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val rect = RectF()

    init { setLayerType(LAYER_TYPE_HARDWARE, null) }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val s = min(w, h); val cx = w / 2f; val cy = h / 2f

        // soft red glow kept inside the view
        fill.shader = RadialGradient(cx, cy, s * 0.5f, Color.parseColor("#88FF5252"), Color.parseColor("#00FF5252"), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, s * 0.5f, fill); fill.shader = null

        // symmetric sound arcs
        line.color = Color.parseColor("#9CC2FF"); line.strokeWidth = s * 0.045f
        rect.set(cx - s * 0.36f, cy - s * 0.30f, cx + s * 0.36f, cy + s * 0.18f)
        canvas.drawArc(rect, 160f, 40f, false, line)
        canvas.drawArc(rect, 340f, 40f, false, line)

        // capsule
        val mw = s * 0.14f
        val top = cy - s * 0.34f; val bottom = cy + s * 0.06f
        rect.set(cx - mw, top, cx + mw, bottom)
        fill.shader = LinearGradient(cx - mw, 0f, cx + mw, 0f, Color.parseColor("#FAFBFD"), Color.parseColor("#8793A3"), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, mw, mw, fill); fill.shader = null
        fill.color = Color.parseColor("#33000000")
        for (k in 0..3) {
            val y = top + s * 0.10f + k * s * 0.055f
            canvas.drawRect(cx - mw * 0.55f, y, cx + mw * 0.55f, y + s * 0.014f, fill)
        }

        // holder, stem, base
        line.color = Color.parseColor("#DDE3EC"); line.strokeWidth = s * 0.04f
        rect.set(cx - mw * 1.55f, cy - s * 0.20f, cx + mw * 1.55f, cy + s * 0.20f)
        canvas.drawArc(rect, 0f, 180f, false, line)
        canvas.drawLine(cx, cy + s * 0.20f, cx, cy + s * 0.31f, line)
        canvas.drawLine(cx - s * 0.10f, cy + s * 0.31f, cx + s * 0.10f, cy + s * 0.31f, line)
    }
}
