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

/** Decorative glossy icon for the Magnetic Field tile: a horseshoe magnet with field lines. */
class MagneticFieldIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val s = min(w, h); val cx = w / 2f; val cy = h / 2f

        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cy, s * 0.62f, Color.parseColor("#8800C2FF"), Color.parseColor("#0000C2FF"), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, s * 0.62f, p); p.shader = null

        // field lines (behind the magnet)
        val fl = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = Color.parseColor("#88B8D8FF"); strokeWidth = s * 0.03f; strokeCap = Paint.Cap.ROUND
        }
        for (k in 1..2) {
            val r = s * (0.30f + 0.11f * k)
            canvas.drawArc(RectF(cx - r, cy + s * 0.10f - r, cx + r, cy + s * 0.10f + r), 200f, 140f, false, fl)
        }

        // horseshoe: thick arc (top) plus two legs
        val thick = s * 0.17f
        val r = s * 0.25f
        val arcBox = RectF(cx - r, cy - s * 0.16f - r, cx + r, cy - s * 0.16f + r)
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = thick; strokeCap = Paint.Cap.BUTT
            shader = LinearGradient(0f, cy - s * 0.4f, 0f, cy + s * 0.3f, Color.parseColor("#FF6B6B"), Color.parseColor("#B71C1C"), Shader.TileMode.CLAMP)
        }
        canvas.drawArc(arcBox, 180f, 180f, false, body)
        val legTop = cy - s * 0.16f
        val legBottom = cy + s * 0.22f
        val leftX = cx - r; val rightX = cx + r
        val tipH = s * 0.10f
        body.style = Paint.Style.FILL
        canvas.drawRect(leftX - thick / 2f, legTop, leftX + thick / 2f, legBottom - tipH, body)
        canvas.drawRect(rightX - thick / 2f, legTop, rightX + thick / 2f, legBottom - tipH, body)
        // silver tips
        p.shader = LinearGradient(0f, legBottom - tipH, 0f, legBottom, Color.parseColor("#FFFFFF"), Color.parseColor("#9AA5B1"), Shader.TileMode.CLAMP)
        canvas.drawRect(leftX - thick / 2f, legBottom - tipH, leftX + thick / 2f, legBottom, p)
        canvas.drawRect(rightX - thick / 2f, legBottom - tipH, rightX + thick / 2f, legBottom, p)
        p.shader = null
        // gloss on the arc
        val gloss = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = Color.parseColor("#55FFFFFF"); strokeWidth = thick * 0.18f; strokeCap = Paint.Cap.ROUND
        }
        canvas.drawArc(RectF(arcBox.left + thick * 0.2f, arcBox.top + thick * 0.2f, arcBox.right - thick * 0.2f, arcBox.bottom - thick * 0.2f), 200f, 70f, false, gloss)
    }
}
