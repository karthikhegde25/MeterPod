package com.karthikhegde.meterpod.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/** Decorative glossy document icon (folded corner, text lines, magnifier badge) for the File Viewer tile. */
class FileIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private fun line(color: String, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = Color.parseColor(color); strokeWidth = width; strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val ph = min(h, w * 1.25f) * 0.90f
        val pw = ph * 0.78f
        val l = (w - pw) / 2f - pw * 0.04f
        val t = (h - ph) / 2f - ph * 0.02f
        val r = l + pw; val b = t + ph
        val c = pw * 0.30f
        val rad = pw * 0.08f

        val page = Path().apply {
            moveTo(l + rad, t); lineTo(r - c, t); lineTo(r, t + c); lineTo(r, b - rad)
            quadTo(r, b, r - rad, b); lineTo(l + rad, b); quadTo(l, b, l, b - rad)
            lineTo(l, t + rad); quadTo(l, t, l + rad, t); close()
        }
        // halo
        p.shader = RadialGradient(w / 2f, h / 2f, min(w, h) * 0.62f, Color.parseColor("#8800C2FF"), Color.parseColor("#0000C2FF"), Shader.TileMode.CLAMP)
        canvas.drawCircle(w / 2f, h / 2f, min(w, h) * 0.62f, p); p.shader = null
        // shadow
        canvas.save(); canvas.translate(pw * 0.04f, pw * 0.07f)
        p.shader = null; p.color = Color.parseColor("#40000000"); canvas.drawPath(page, p)
        canvas.restore()

        // paper
        p.shader = LinearGradient(l, t, r, b, Color.parseColor("#FFFFFF"), Color.parseColor("#E3EEFF"), Shader.TileMode.CLAMP)
        canvas.drawPath(page, p)
        // blue header band clipped to the page
        canvas.save(); canvas.clipPath(page)
        p.shader = LinearGradient(0f, t, 0f, t + ph * 0.22f, Color.parseColor("#7FE0FF"), Color.parseColor("#0A7BFF"), Shader.TileMode.CLAMP)
        canvas.drawRect(l, t, r, t + ph * 0.22f, p)
        canvas.restore()
        p.shader = null
        canvas.drawPath(page, line("#33465F", pw * 0.035f))

        // folded corner
        val corner = Path().apply { moveTo(r - c, t); lineTo(r - c, t + c); lineTo(r, t + c); close() }
        p.shader = LinearGradient(r - c, t, r, t + c, Color.parseColor("#FFFFFF"), Color.parseColor("#9DB4D6"), Shader.TileMode.CLAMP)
        canvas.drawPath(corner, p); p.shader = null
        canvas.drawPath(corner, line("#33465F", pw * 0.03f))

        // text lines
        val x1 = l + pw * 0.16f; val x2 = r - pw * 0.16f
        var y = t + ph * 0.40f
        val gap = ph * 0.13f
        val lp = line("#1E88FF", pw * 0.07f)
        canvas.drawLine(x1, y, x2, y, lp); y += gap
        canvas.drawLine(x1, y, x2 - pw * 0.10f, y, lp); y += gap
        canvas.drawLine(x1, y, x1 + pw * 0.40f, y, lp)

        // magnifier badge, bottom-right
        val bx = r - pw * 0.02f; val by = b - ph * 0.02f; val br = pw * 0.26f
        canvas.save(); canvas.translate(pw * 0.02f, pw * 0.04f)
        p.color = Color.parseColor("#40000000"); canvas.drawCircle(bx - br * 0.2f, by - br * 0.2f, br, p)
        canvas.restore()
        p.shader = RadialGradient(bx - br * 0.4f, by - br * 0.4f, br * 1.5f, Color.parseColor("#FFF9C4"), Color.parseColor("#FFAB00"), Shader.TileMode.CLAMP)
        canvas.drawCircle(bx - br * 0.2f, by - br * 0.2f, br, p)
        p.shader = RadialGradient(bx - br * 0.4f, by - br * 0.45f, br, Color.parseColor("#FFFFFF"), Color.parseColor("#8CC0FF"), Shader.TileMode.CLAMP)
        canvas.drawCircle(bx - br * 0.2f, by - br * 0.2f, br * 0.68f, p)
        p.shader = null
        canvas.drawArc(
            bx - br * 0.2f - br * 0.45f, by - br * 0.2f - br * 0.45f, bx - br * 0.2f + br * 0.45f, by - br * 0.2f + br * 0.45f,
            200f, 70f, false, line("#B3FFFFFF", pw * 0.03f)
        )
    }
}
