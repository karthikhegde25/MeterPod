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
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Decorative glossy stopwatch / clock icon for the Time tile. */
class TimeIconView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val s = min(w, h); val cx = w / 2f; val cy = h * 0.54f; val r = s * 0.38f

        p.style = Paint.Style.FILL
        p.shader = RadialGradient(w / 2f, h / 2f, s * 0.62f, Color.parseColor("#8800C2FF"), Color.parseColor("#0000C2FF"), Shader.TileMode.CLAMP)
        canvas.drawCircle(w / 2f, h / 2f, s * 0.62f, p)
        // crown button
        p.shader = LinearGradient(0f, cy - r - s * 0.12f, 0f, cy - r, Color.parseColor("#D8DEE8"), Color.parseColor("#7C8898"), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(cx - s * 0.07f, cy - r - s * 0.11f, cx + s * 0.07f, cy - r + s * 0.02f), s * 0.03f, s * 0.03f, p)
        // bezel
        p.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r, Color.parseColor("#F2F5FA"), Color.parseColor("#5C6878"), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, p)
        // face
        p.shader = RadialGradient(cx - r * 0.2f, cy - r * 0.3f, r * 1.2f, Color.parseColor("#3C5478"), Color.parseColor("#0B121C"), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r * 0.84f, p); p.shader = null
        // ticks
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.parseColor("#C9D6E8"); strokeWidth = s * 0.018f; strokeCap = Paint.Cap.ROUND }
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0 - 90.0)
            val r1 = r * (if (i % 3 == 0) 0.62f else 0.70f); val r2 = r * 0.77f
            canvas.drawLine(cx + (cos(a) * r1).toFloat(), cy + (sin(a) * r1).toFloat(), cx + (cos(a) * r2).toFloat(), cy + (sin(a) * r2).toFloat(), tp)
        }
        // hands
        val hp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE; strokeWidth = s * 0.035f; strokeCap = Paint.Cap.ROUND }
        canvas.drawLine(cx, cy, cx + r * 0.30f, cy - r * 0.18f, hp)
        canvas.drawLine(cx, cy, cx - r * 0.05f, cy - r * 0.55f, hp)
        hp.color = Color.parseColor("#FF5252"); hp.strokeWidth = s * 0.02f
        canvas.drawLine(cx, cy, cx - r * 0.32f, cy + r * 0.38f, hp)
        p.color = Color.parseColor("#FF5252"); canvas.drawCircle(cx, cy, s * 0.03f, p)
        // gloss
        p.shader = LinearGradient(0f, cy - r, 0f, cy, Color.parseColor("#55FFFFFF"), Color.parseColor("#00FFFFFF"), Shader.TileMode.CLAMP)
        canvas.drawOval(RectF(cx - r * 0.7f, cy - r * 0.82f, cx + r * 0.7f, cy - r * 0.05f), p); p.shader = null
    }
}
