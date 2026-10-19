package com.karthikhegde.meterpod.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/** Decorative glossy icon for the Accelerometer tile: a phone with X / Y / Z motion arrows. */
class AccelerometerIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val rect = RectF()

    private fun arrow(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, s: Float) {
        line.color = color; line.strokeWidth = s * 0.05f
        c.drawLine(x1, y1, x2, y2, line)
        val dx = x2 - x1; val dy = y2 - y1
        val len = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(0.001f)
        val ux = dx / len; val uy = dy / len
        val hl = s * 0.10f
        val p = Path()
        p.moveTo(x2, y2)
        p.lineTo(x2 - ux * hl - uy * hl * 0.55f, y2 - uy * hl + ux * hl * 0.55f)
        p.lineTo(x2 - ux * hl + uy * hl * 0.55f, y2 - uy * hl - ux * hl * 0.55f)
        p.close()
        fill.color = color; fill.shader = null
        c.drawPath(p, fill)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val s = min(w, h); val cx = w / 2f; val cy = h / 2f

        fill.shader = RadialGradient(cx, cy, s * 0.5f, Color.parseColor("#8866E0A0"), Color.parseColor("#0066E0A0"), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, s * 0.5f, fill); fill.shader = null

        // phone body
        rect.set(cx - s * 0.17f, cy - s * 0.30f, cx + s * 0.17f, cy + s * 0.30f)
        fill.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, Color.parseColor("#7C8CA6"), Color.parseColor("#2B3547"), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, s * 0.07f, s * 0.07f, fill)
        rect.set(rect.left + s * 0.025f, rect.top + s * 0.045f, rect.right - s * 0.025f, rect.bottom - s * 0.045f)
        fill.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, Color.parseColor("#3E86F5"), Color.parseColor("#17306E"), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, s * 0.04f, s * 0.04f, fill); fill.shader = null

        // axis arrows: X red (right), Y green (up), Z blue (diagonal)
        arrow(canvas, cx + s * 0.20f, cy, cx + s * 0.46f, cy, Color.parseColor("#FF5252"), s)
        arrow(canvas, cx, cy - s * 0.31f, cx, cy - s * 0.47f, Color.parseColor("#4CD964"), s)
        arrow(canvas, cx - s * 0.20f, cy + s * 0.22f, cx - s * 0.40f, cy + s * 0.42f, Color.parseColor("#FFC940"), s)
    }
}
