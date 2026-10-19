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

/** Decorative glossy document icon with a green "+" badge for the File Creator tile. */
class FileCreatorIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

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
        p.shader = RadialGradient(w / 2f, h / 2f, min(w, h) * 0.62f, Color.parseColor("#8800C2FF"), Color.parseColor("#0000C2FF"), Shader.TileMode.CLAMP)
        canvas.drawCircle(w / 2f, h / 2f, min(w, h) * 0.62f, p); p.shader = null

        canvas.save(); canvas.translate(pw * 0.04f, pw * 0.07f)
        p.color = Color.parseColor("#40000000"); canvas.drawPath(page, p)
        canvas.restore()

        p.shader = LinearGradient(l, t, l, b, Color.parseColor("#FFFFFF"), Color.parseColor("#C9D3E0"), Shader.TileMode.CLAMP)
        canvas.drawPath(page, p); p.shader = null

        val fold = Path().apply { moveTo(r - c, t); lineTo(r - c, t + c); lineTo(r, t + c); close() }
        p.color = Color.parseColor("#9FB0C6"); canvas.drawPath(fold, p)

        val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7A8CA3"); strokeWidth = pw * 0.05f; strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE
        }
        val x1 = l + pw * 0.16f; val x2 = r - pw * 0.16f
        canvas.drawLine(x1, t + ph * 0.42f, x2, t + ph * 0.42f, lp)
        canvas.drawLine(x1, t + ph * 0.54f, x2 - pw * 0.15f, t + ph * 0.54f, lp)
        canvas.drawLine(x1, t + ph * 0.66f, x2 - pw * 0.30f, t + ph * 0.66f, lp)

        // "+" badge
        val br = pw * 0.30f
        val bx = r - br * 0.45f; val by = b - br * 0.40f
        p.shader = LinearGradient(bx, by - br, bx, by + br, Color.parseColor("#5CE08A"), Color.parseColor("#1E9C4E"), Shader.TileMode.CLAMP)
        canvas.drawCircle(bx, by, br, p); p.shader = null
        val pl = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; strokeWidth = br * 0.30f; strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE
        }
        canvas.drawLine(bx - br * 0.5f, by, bx + br * 0.5f, by, pl)
        canvas.drawLine(bx, by - br * 0.5f, bx, by + br * 0.5f, pl)
    }
}
