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
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A 3D glass quarter-dial that shows the acute angle (0..90 degrees) between the phone's plane and the ground:
 * chrome rim, recessed dark face, glowing progress band, extruded degree numbers and a shaded needle.
 */
class AngleDialView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var angleDegrees = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }

    fun setAngle(angle: Float) {
        angleDegrees = angle.coerceIn(0f, 90f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = w * 0.12f
        val cy = h * 0.88f
        val radius = min(w, h) * 0.75f
        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val sector = Path().apply { moveTo(cx, cy); arcTo(rect, 270f, 90f); close() }

        // ground shadow of the dial
        canvas.save(); canvas.translate(radius * 0.03f, radius * 0.045f)
        p.style = Paint.Style.FILL; p.shader = null; p.color = Color.parseColor("#55000000")
        canvas.drawPath(sector, p)
        canvas.restore()

        // recessed face
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx + radius * 0.3f, cy - radius * 0.3f, radius * 1.3f,
            intArrayOf(Draw3D.c("#2B4068"), Draw3D.c("#101C34"), Draw3D.c("#070D1B")), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(sector, p)
        p.shader = null

        // chrome rim along the arc and the two straight edges
        p.style = Paint.Style.STROKE
        p.strokeWidth = radius * 0.035f
        p.shader = LinearGradient(cx, cy - radius, cx + radius, cy, Draw3D.c("#F6F9FC"), Draw3D.c("#6B7683"), Shader.TileMode.CLAMP)
        canvas.drawPath(sector, p)
        p.shader = null

        // glowing progress band (lit from above): dark under-band, blue band, highlight
        val bandR = radius * 0.80f
        val band = RectF(cx - bandR, cy - bandR, cx + bandR, cy + bandR)
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = radius * 0.10f; p.color = Draw3D.c("#1A2640")
        canvas.drawArc(band, 270f, 90f, false, p)
        if (angleDegrees > 0.2f) {
            p.strokeWidth = radius * 0.10f; p.color = Draw3D.c("#2F6BF0")
            canvas.drawArc(band, 360f, -angleDegrees, false, p)
            p.strokeWidth = radius * 0.05f; p.color = Draw3D.c("#8FC0FF")
            canvas.drawArc(RectF(cx - bandR - radius * 0.015f, cy - bandR - radius * 0.015f, cx + bandR - radius * 0.015f, cy + bandR - radius * 0.015f), 360f, -angleDegrees, false, p)
        }
        p.strokeCap = Paint.Cap.BUTT

        // ticks and extruded numbers
        text.textSize = radius * 0.085f
        for (deg in 0..90 step 5) {
            val rad = Math.toRadians((-deg).toDouble())
            val major = deg % 15 == 0
            val r1 = radius * (if (major) 0.90f else 0.94f)
            p.strokeWidth = radius * (if (major) 0.012f else 0.006f)
            p.color = if (major) Draw3D.c("#E8EEF7") else Draw3D.c("#7B88A0")
            canvas.drawLine(cx + r1 * cos(rad).toFloat(), cy + r1 * sin(rad).toFloat(), cx + radius * 0.985f * cos(rad).toFloat(), cy + radius * 0.985f * sin(rad).toFloat(), p)
        }
        text.color = Draw3D.c("#F2F7FF")
        for (deg in 0..90 step 15) {
            val rad = Math.toRadians((-deg).toDouble())
            val lr = radius * 0.64f
            Draw3D.depthText(canvas, deg.toString(), cx + lr * cos(rad).toFloat(), cy + lr * sin(rad).toFloat() - (text.ascent() + text.descent()) / 2f, text, radius * 0.012f)
        }

        // needle + pivot sphere
        val rad = Math.toRadians((-angleDegrees).toDouble())
        val len = radius * 0.95f
        Draw3D.needle(canvas, cx, cy, cx + len * cos(rad).toFloat(), cy + len * sin(rad).toFloat(), radius * 0.035f, p)
        Draw3D.dropShadow(canvas, cx + radius * 0.01f, cy + radius * 0.02f, radius * 0.07f, p)
        Draw3D.sphere(canvas, cx, cy, radius * 0.045f, "#FFF3C4", "#D9A441", "#6B4A12", p)

        // glass sheen over the whole sector
        canvas.save(); canvas.clipPath(sector)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(cx, cy - radius, cx + radius * 0.6f, cy - radius * 0.2f, Draw3D.c("#40FFFFFF"), Draw3D.c("#00FFFFFF"), Shader.TileMode.CLAMP)
        canvas.drawRect(cx, cy - radius, cx + radius, cy - radius * 0.35f, p)
        p.shader = null
        canvas.restore()
    }
}
