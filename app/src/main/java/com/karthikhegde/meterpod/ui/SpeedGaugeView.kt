package com.karthikhegde.meterpod.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A 3D half-circle speedometer: chrome bezel, recessed dark face, a glowing green-to-red progress band,
 * extruded tick numbers and a shaded needle on a brass hub. 0 at the left, max speed at the right.
 */
class SpeedGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var maxSpeedKmh = 180f
        set(value) {
            field = value
            invalidate()
        }

    private var speedKmh = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }

    fun setSpeedKmh(speed: Float) {
        speedKmh = speed.coerceIn(0f, maxSpeedKmh)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = w / 2f
        val cy = h * 0.92f
        val radius = min(w / 2f, h) * 0.82f
        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val half = Path().apply { moveTo(cx - radius, cy); arcTo(rect, 180f, 180f); lineTo(cx - radius, cy); close() }

        // drop shadow, bezel, face
        canvas.save(); canvas.translate(radius * 0.03f, radius * 0.05f)
        p.style = Paint.Style.FILL; p.shader = null; p.color = Draw3D.c("#55000000")
        canvas.drawPath(half, p)
        canvas.restore()
        val bezel = radius * 0.07f
        canvas.save(); canvas.clipRect(0f, 0f, w, cy + radius * 0.02f)
        Draw3D.chromeRing(canvas, cx, cy, radius + bezel / 2f, bezel, p)
        canvas.restore()
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx - radius * 0.2f, cy - radius * 0.6f, radius * 1.6f,
            intArrayOf(Draw3D.c("#2A3F66"), Draw3D.c("#101C34"), Draw3D.c("#070D1B")), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(half, p)
        p.shader = null

        // track + glowing progress band (green -> yellow -> red)
        val bandR = radius * 0.80f
        val band = RectF(cx - bandR, cy - bandR, cx + bandR, cy + bandR)
        val bw = radius * 0.11f
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = bw
        p.color = Draw3D.c("#1A2640")
        canvas.drawArc(band, 180f, 180f, false, p)
        val sweep = 180f * (speedKmh / maxSpeedKmh).coerceIn(0f, 1f)
        if (sweep > 0.5f) {
            val sg = SweepGradient(cx, cy, intArrayOf(Draw3D.c("#2DD36F"), Draw3D.c("#FFD23F"), Draw3D.c("#FF4B4B"), Draw3D.c("#2DD36F")), floatArrayOf(0.5f, 0.68f, 0.98f, 1f))
            p.shader = sg
            canvas.drawArc(band, 180f, sweep, false, p)
            p.shader = null
            p.strokeWidth = bw * 0.28f; p.color = Draw3D.c("#66FFFFFF")
            val hl = RectF(band); hl.inset(bw * 0.18f, bw * 0.18f)
            canvas.drawArc(hl, 180f, sweep, false, p)
        }
        p.strokeCap = Paint.Cap.BUTT

        // ticks + extruded numbers
        label.textSize = radius * 0.105f
        label.color = Draw3D.c("#F2F7FF")
        val step = (maxSpeedKmh / 9f).let { raw -> (raw / 10f).let { if (it < 1f) 10f else Math.round(it) * 10f } }
        var mark = 0f
        while (mark <= maxSpeedKmh + 0.01f) {
            val ang = Math.toRadians((180f + mark / maxSpeedKmh * 180f).toDouble())
            p.strokeWidth = radius * 0.014f; p.color = Draw3D.c("#E8EEF7")
            canvas.drawLine(cx + radius * 0.955f * cos(ang).toFloat(), cy + radius * 0.955f * sin(ang).toFloat(), cx + radius * 0.995f * cos(ang).toFloat(), cy + radius * 0.995f * sin(ang).toFloat(), p)
            val lr = radius * 0.60f
            Draw3D.depthText(canvas, mark.toInt().toString(), cx + lr * cos(ang).toFloat(), cy + lr * sin(ang).toFloat() - (label.ascent() + label.descent()) / 2f, label, radius * 0.012f)
            mark += step
        }

        // needle + hub
        val t = (speedKmh / maxSpeedKmh).coerceIn(0f, 1f)
        val ang = Math.toRadians((180f + t * 180f).toDouble())
        val len = radius * 0.86f
        Draw3D.needle(canvas, cx, cy, cx + len * cos(ang).toFloat(), cy + len * sin(ang).toFloat(), radius * 0.04f, p)
        Draw3D.dropShadow(canvas, cx + radius * 0.01f, cy + radius * 0.025f, radius * 0.09f, p)
        Draw3D.sphere(canvas, cx, cy, radius * 0.06f, "#FFF3C4", "#D9A441", "#6B4A12", p)

        // glass sheen on the upper half
        canvas.save(); canvas.clipPath(half)
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx - radius * 0.35f, cy - radius * 0.8f, radius, intArrayOf(Draw3D.c("#40FFFFFF"), Draw3D.c("#00FFFFFF")), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx - radius * 0.35f, cy - radius * 0.8f, radius, p)
        p.shader = null
        canvas.restore()
    }
}
