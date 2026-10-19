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

/** Decorative glossy icon for the File Converter tile: two pages with a swap arrow between them. */
class FileConverterIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private fun page(canvas: Canvas, l: Float, t: Float, pw: Float, ph: Float, top: String, bottom: String) {
        val c = pw * 0.30f; val r = l + pw; val b = t + ph; val rad = pw * 0.10f
        val path = Path().apply {
            moveTo(l + rad, t); lineTo(r - c, t); lineTo(r, t + c); lineTo(r, b - rad)
            quadTo(r, b, r - rad, b); lineTo(l + rad, b); quadTo(l, b, l, b - rad)
            lineTo(l, t + rad); quadTo(l, t, l + rad, t); close()
        }
        canvas.save(); canvas.translate(pw * 0.05f, pw * 0.07f)
        p.shader = null; p.color = Color.parseColor("#40000000"); canvas.drawPath(path, p)
        canvas.restore()
        p.shader = LinearGradient(l, t, l, b, Color.parseColor(top), Color.parseColor(bottom), Shader.TileMode.CLAMP)
        canvas.drawPath(path, p); p.shader = null
        p.color = Color.parseColor("#33000000")
        canvas.drawPath(Path().apply { moveTo(r - c, t); lineTo(r - c, t + c); lineTo(r, t + c); close() }, p)
        val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B3FFFFFF"); strokeWidth = pw * 0.07f; strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE
        }
        canvas.drawLine(l + pw * 0.2f, t + ph * 0.50f, r - pw * 0.2f, t + ph * 0.50f, lp)
        canvas.drawLine(l + pw * 0.2f, t + ph * 0.64f, r - pw * 0.35f, t + ph * 0.64f, lp)
        canvas.drawLine(l + pw * 0.2f, t + ph * 0.78f, r - pw * 0.2f, t + ph * 0.78f, lp)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        p.shader = RadialGradient(w / 2f, h / 2f, min(w, h) * 0.62f, Color.parseColor("#8800C2FF"), Color.parseColor("#0000C2FF"), Shader.TileMode.CLAMP)
        canvas.drawCircle(w / 2f, h / 2f, min(w, h) * 0.62f, p); p.shader = null

        val ph = h * 0.62f; val pw = ph * 0.78f
        page(canvas, w * 0.06f, h * 0.10f, pw, ph, "#6FA8FF", "#2F5BD8")
        page(canvas, w - pw - w * 0.08f, h * 0.28f, pw, ph, "#6FE39A", "#1E9C4E")

        // swap arrow badge
        val cx = w / 2f; val cy = h * 0.52f; val br = min(w, h) * 0.17f
        p.shader = LinearGradient(cx, cy - br, cx, cy + br, Color.parseColor("#FFB347"), Color.parseColor("#E5731A"), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, br, p); p.shader = null
        val ap = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; strokeWidth = br * 0.2f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; style = Paint.Style.STROKE
        }
        val a = br * 0.55f
        canvas.drawLine(cx - a, cy - br * 0.28f, cx + a, cy - br * 0.28f, ap)
        canvas.drawLine(cx + a, cy - br * 0.28f, cx + a * 0.5f, cy - br * 0.62f, ap)
        canvas.drawLine(cx + a, cy - br * 0.28f, cx + a * 0.5f, cy + br * 0.06f, ap)
        canvas.drawLine(cx + a, cy + br * 0.40f, cx - a, cy + br * 0.40f, ap)
        canvas.drawLine(cx - a, cy + br * 0.40f, cx - a * 0.5f, cy + br * 0.06f, ap)
        canvas.drawLine(cx - a, cy + br * 0.40f, cx - a * 0.5f, cy + br * 0.74f, ap)
    }
}
