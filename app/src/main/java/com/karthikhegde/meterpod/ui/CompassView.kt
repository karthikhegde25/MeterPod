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
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A realistic, "3D" compass: a chrome bezel with a bevel, a recessed dial that rotates opposite to the device
 * heading (so N always points to north), a faceted red/silver needle with a drop shadow and brass pivot, a
 * fixed lubber-line pointer showing where the top of the phone faces, and a curved glass dome with highlights.
 * Everything is drawn with gradients (no bitmaps), so it stays sharp at any size.
 */
class CompassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var headingDeg = 0f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }

    fun setHeading(heading: Float) {
        headingDeg = heading
        invalidate()
    }

    private fun c(hex: String) = Color.parseColor(hex)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = w / 2f
        val cy = h / 2f
        val r = min(w, h) / 2f * 0.90f

        // ---- ground shadow under the case
        fill.shader = RadialGradient(cx, cy + r * 0.10f, r * 1.12f, intArrayOf(c("#99000000"), c("#55000000"), c("#00000000")), floatArrayOf(0.78f, 0.9f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy + r * 0.10f, r * 1.12f, fill)

        // ---- outer chrome bezel (brushed metal sweep)
        fill.shader = SweepGradient(
            cx, cy,
            intArrayOf(c("#F4F7FA"), c("#7D8793"), c("#E9EEF3"), c("#5E6772"), c("#F4F7FA"), c("#7D8793"), c("#E9EEF3"), c("#5E6772"), c("#F4F7FA")),
            null
        )
        canvas.drawCircle(cx, cy, r, fill)
        fill.shader = null
        stroke.shader = null
        stroke.strokeWidth = r * 0.012f; stroke.color = c("#33000000")
        canvas.drawCircle(cx, cy, r, stroke)

        // ---- bevel: lit from the top-left, shaded bottom-right (gives the raised/recessed ring)
        val bevelR = r * 0.93f
        stroke.strokeWidth = r * 0.05f
        stroke.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r, c("#8A96A3"), c("#F2F5F8"), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, bevelR, stroke)
        stroke.shader = null
        stroke.strokeWidth = r * 0.012f; stroke.color = c("#66000000")
        canvas.drawCircle(cx, cy, r * 0.90f, stroke)

        // ---- dial face (recessed)
        val dialR = r * 0.88f
        fill.shader = RadialGradient(cx - dialR * 0.2f, cy - dialR * 0.25f, dialR * 1.25f, intArrayOf(c("#24365A"), c("#101C34"), c("#070D1B")), floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, dialR, fill)
        fill.shader = null

        // ---- rotating compass card
        canvas.save()
        canvas.rotate(-headingDeg, cx, cy)
        drawCard(canvas, cx, cy, dialR)
        drawNeedle(canvas, cx, cy, dialR)
        canvas.restore()

        // ---- inner shadow on the dial edge (depth)
        stroke.strokeWidth = dialR * 0.07f
        stroke.shader = LinearGradient(cx, cy - dialR, cx, cy + dialR, c("#99000000"), c("#11000000"), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, dialR - stroke.strokeWidth / 2f, stroke)
        stroke.shader = null

        // ---- fixed lubber pointer on the bezel (where the top of the phone faces)
        val tipY = cy - r * 0.99f
        val ph = r * 0.13f; val pw = r * 0.055f
        val left = Path().apply { moveTo(cx, tipY + ph); lineTo(cx - pw, tipY); lineTo(cx, tipY + ph * 0.8f); close() }
        // Pointing inward: tip toward the dial
        val pL = Path().apply { moveTo(cx, tipY + ph); lineTo(cx - pw, tipY); lineTo(cx, tipY + ph * 0.25f); close() }
        val pR = Path().apply { moveTo(cx, tipY + ph); lineTo(cx + pw, tipY); lineTo(cx, tipY + ph * 0.25f); close() }
        fill.color = c("#FFC857"); canvas.drawPath(pL, fill)
        fill.color = c("#D9901A"); canvas.drawPath(pR, fill)
        left.reset()

        // ---- glass dome: reflection and rim darkening
        fill.shader = RadialGradient(cx - dialR * 0.35f, cy - dialR * 0.45f, dialR * 0.95f, intArrayOf(c("#55FFFFFF"), c("#14FFFFFF"), c("#00FFFFFF")), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, dialR, fill)
        // crescent highlight along the upper edge
        val rect = RectF(cx - dialR * 0.80f, cy - dialR * 0.88f, cx + dialR * 0.80f, cy + dialR * 0.10f)
        fill.shader = LinearGradient(0f, cy - dialR * 0.9f, 0f, cy - dialR * 0.1f, c("#66FFFFFF"), c("#00FFFFFF"), Shader.TileMode.CLAMP)
        val crescent = Path().apply {
            addOval(RectF(cx - dialR * 0.82f, cy - dialR * 0.92f, cx + dialR * 0.82f, cy + dialR * 0.30f), Path.Direction.CW)
            val cut = Path().apply { addOval(RectF(cx - dialR * 0.90f, cy - dialR * 0.80f, cx + dialR * 0.90f, cy + dialR * 0.55f), Path.Direction.CW) }
            op(cut, Path.Op.DIFFERENCE)
        }
        canvas.drawPath(crescent, fill)
        rect.setEmpty()
        fill.shader = null
        // small sharp glint
        fill.color = c("#66FFFFFF")
        canvas.drawCircle(cx - dialR * 0.45f, cy - dialR * 0.62f, dialR * 0.025f, fill)
    }

    private fun drawCard(canvas: Canvas, cx: Float, cy: Float, dialR: Float) {
        // fine ticks every 5 degrees
        for (deg in 0 until 360 step 5) {
            val major = deg % 90 == 0
            val mid = deg % 45 == 0
            val ten = deg % 15 == 0
            val len = when { major -> 0.13f; mid -> 0.10f; ten -> 0.07f; else -> 0.04f }
            val rad = Math.toRadians((deg - 90).toDouble())
            val r1 = dialR * (0.97f - len); val r2 = dialR * 0.97f
            stroke.shader = null
            stroke.strokeCap = Paint.Cap.ROUND
            stroke.strokeWidth = dialR * (if (major) 0.022f else if (ten) 0.012f else 0.007f)
            stroke.color = if (deg == 0) c("#FF4B4B") else if (ten) c("#D5DCE6") else c("#7D8AA3")
            canvas.drawLine(cx + r1 * cos(rad).toFloat(), cy + r1 * sin(rad).toFloat(), cx + r2 * cos(rad).toFloat(), cy + r2 * sin(rad).toFloat(), stroke)
        }
        stroke.strokeCap = Paint.Cap.BUTT

        // degree numbers every 30 (except where a letter sits)
        text.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        text.textSize = dialR * 0.075f
        text.color = c("#8FA0BE")
        for (deg in 30 until 360 step 30) {
            if (deg % 90 == 0) continue
            drawUpright(canvas, deg.toString(), cx, cy, dialR * 0.66f, deg, text)
        }

        // cardinal and intercardinal letters
        text.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        val labels = listOf(0 to "N", 90 to "E", 180 to "S", 270 to "W")
        for ((deg, s) in labels) {
            text.textSize = dialR * (if (deg == 0) 0.19f else 0.15f)
            text.color = if (deg == 0) c("#FF4B4B") else c("#F1F5FB")
            text.setShadowLayer(dialR * 0.01f, 0f, dialR * 0.012f, c("#99000000"))
            drawUpright(canvas, s, cx, cy, dialR * 0.74f, deg, text)
        }
        text.textSize = dialR * 0.085f
        text.color = c("#B6C2D9")
        for ((deg, s) in listOf(45 to "NE", 135 to "SE", 225 to "SW", 315 to "NW")) {
            drawUpright(canvas, s, cx, cy, dialR * 0.66f, deg, text)
        }
        text.clearShadowLayer()

        // inner decorative ring
        stroke.shader = null
        stroke.strokeWidth = dialR * 0.006f
        stroke.color = c("#33FFFFFF")
        canvas.drawCircle(cx, cy, dialR * 0.52f, stroke)
    }

    /** Draws [s] at radius [rad] on the card at bearing [deg], kept upright on screen despite the card's rotation. */
    private fun drawUpright(canvas: Canvas, s: String, cx: Float, cy: Float, rad: Float, deg: Int, p: Paint) {
        val a = Math.toRadians((deg - 90).toDouble())
        val x = cx + rad * cos(a).toFloat()
        val y = cy + rad * sin(a).toFloat()
        canvas.save()
        canvas.rotate(headingDeg, x, y)
        canvas.drawText(s, x, y - (p.ascent() + p.descent()) / 2f, p)
        canvas.restore()
    }

    private fun drawNeedle(canvas: Canvas, cx: Float, cy: Float, dialR: Float) {
        val len = dialR * 0.50f
        val half = dialR * 0.060f
        fun tri(tipY: Float, side: Float): Path = Path().apply {
            moveTo(cx, cy + tipY); lineTo(cx + side * half, cy); lineTo(cx, cy); close()
        }
        // drop shadow, offset down-right
        canvas.save()
        canvas.translate(dialR * 0.025f, dialR * 0.04f)
        fill.shader = null
        fill.color = c("#66000000")
        canvas.drawPath(tri(-len, -1f), fill); canvas.drawPath(tri(-len, 1f), fill)
        canvas.drawPath(tri(len, -1f), fill); canvas.drawPath(tri(len, 1f), fill)
        canvas.restore()

        // north half (red), faceted: lit left facet, shaded right facet
        fill.color = c("#FF6A5E"); canvas.drawPath(tri(-len, -1f), fill)
        fill.color = c("#A81414"); canvas.drawPath(tri(-len, 1f), fill)
        // south half (silver)
        fill.color = c("#F2F5F9"); canvas.drawPath(tri(len, -1f), fill)
        fill.color = c("#8794A6"); canvas.drawPath(tri(len, 1f), fill)
        // centre-line highlight
        stroke.shader = null
        stroke.strokeWidth = dialR * 0.006f
        stroke.color = c("#55FFFFFF")
        canvas.drawLine(cx, cy - len, cx, cy + len, stroke)

        // brass pivot cap with a specular spot
        val capR = dialR * 0.07f
        fill.shader = RadialGradient(cx - capR * 0.35f, cy - capR * 0.4f, capR * 1.3f, intArrayOf(c("#FFF3C4"), c("#D9A441"), c("#6B4A12")), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, capR, fill)
        fill.shader = null
        stroke.strokeWidth = dialR * 0.01f; stroke.color = c("#66000000")
        canvas.drawCircle(cx, cy, capR, stroke)
    }
}
