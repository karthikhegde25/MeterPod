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

/** Decorative glossy icon for the Games tile: a game controller with a d-pad and four buttons. */
class GamesIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val s = min(w, h); val cx = w / 2f; val cy = h / 2f

        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cy, s * 0.62f, Color.parseColor("#88B07CFF"), Color.parseColor("#00B07CFF"), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, s * 0.62f, p); p.shader = null

        val body = RectF(cx - s * 0.42f, cy - s * 0.22f, cx + s * 0.42f, cy + s * 0.2f)
        p.shader = LinearGradient(0f, body.top, 0f, body.bottom, Color.parseColor("#6D7BFF"), Color.parseColor("#2A2F9E"), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(body, s * 0.2f, s * 0.2f, p)
        // grips
        canvas.drawCircle(cx - s * 0.30f, cy + s * 0.18f, s * 0.14f, p)
        canvas.drawCircle(cx + s * 0.30f, cy + s * 0.18f, s * 0.14f, p)
        p.shader = null

        // gloss
        p.color = Color.parseColor("#44FFFFFF")
        canvas.drawRoundRect(RectF(body.left + s * 0.06f, body.top + s * 0.03f, body.right - s * 0.06f, body.top + s * 0.12f), s * 0.05f, s * 0.05f, p)

        // d-pad
        p.color = Color.parseColor("#F5F7FA")
        val dx = cx - s * 0.23f; val dy = cy - s * 0.01f; val a = s * 0.07f; val l = s * 0.035f
        canvas.drawRoundRect(RectF(dx - a, dy - l, dx + a, dy + l), l, l, p)
        canvas.drawRoundRect(RectF(dx - l, dy - a, dx + l, dy + a), l, l, p)

        // buttons
        val bx = cx + s * 0.23f; val by = cy - s * 0.01f; val d = s * 0.075f; val r = s * 0.04f
        p.color = Color.parseColor("#FF5C5C"); canvas.drawCircle(bx + d, by, r, p)
        p.color = Color.parseColor("#4CD964"); canvas.drawCircle(bx, by + d, r, p)
        p.color = Color.parseColor("#FFC940"); canvas.drawCircle(bx - d, by, r, p)
        p.color = Color.parseColor("#4C8DFF"); canvas.drawCircle(bx, by - d, r, p)
    }
}
