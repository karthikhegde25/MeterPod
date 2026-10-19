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

/*
 * Decorative, non-interactive home-screen tile icons for Compass, Digital Level and
 * Pedometer. They are separate from the real tool views (CompassView, BubbleLevelView,
 * StepHistoryChartView) so the tools themselves look exactly as before.
 */

internal fun fillPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
internal fun strokePaint(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE; this.color = color; strokeWidth = width; strokeCap = Paint.Cap.ROUND
}

/** Soft white sheen over the upper part of a round glass face. */
internal fun drawGlassSheen(canvas: Canvas, cx: Float, cy: Float, r: Float, paint: Paint) {
    val save = canvas.save()
    val clip = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
    canvas.clipPath(clip)
    paint.shader = LinearGradient(cx, cy - r, cx, cy + r * 0.1f, Color.parseColor("#66FFFFFF"), Color.parseColor("#00FFFFFF"), Shader.TileMode.CLAMP)
    canvas.drawOval(RectF(cx - r * 0.95f, cy - r * 1.05f, cx + r * 0.95f, cy + r * 0.12f), paint)
    paint.shader = null
    canvas.restoreToCount(save)
}

/** Metal bezel (bright top-left to dark bottom-right) with a recessed inner lip, plus drop shadow. */
internal fun drawBezel(canvas: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
    p.shader = null
    p.color = Color.parseColor("#59000000")
    canvas.drawCircle(cx + r * 0.03f, cy + r * 0.08f, r, p)

    p.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r,
        intArrayOf(Color.parseColor("#FBFDFF"), Color.parseColor("#9AA8BB"), Color.parseColor("#3A4452")),
        floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    canvas.drawCircle(cx, cy, r, p)

    p.shader = LinearGradient(cx + r, cy + r, cx - r, cy - r,
        intArrayOf(Color.parseColor("#D8E0EB"), Color.parseColor("#6C7889"), Color.parseColor("#262D37")),
        floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    canvas.drawCircle(cx, cy, r * 0.90f, p)
    p.shader = null
}

class CompassIconView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val p = fillPaint()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = w / 2f; val cy = h / 2f
        val r = min(w, h) / 2f * 0.95f

        drawBezel(canvas, cx, cy, r, p)

        // Dark dial face
        val fr = r * 0.82f
        p.shader = RadialGradient(cx - fr * 0.25f, cy - fr * 0.3f, fr * 1.3f,
            Color.parseColor("#3C5478"), Color.parseColor("#0B121C"), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, fr, p)
        p.shader = null

        // Tick marks every 30 degrees; north tick red, cardinals longer
        for (i in 0 until 12) {
            val deg = i * 30
            val rad = Math.toRadians((deg - 90).toDouble())
            val cardinal = deg % 90 == 0
            val inner = fr * (if (cardinal) 0.74f else 0.82f)
            val tick = strokePaint(if (deg == 0) Color.parseColor("#FF5252") else Color.parseColor("#C9D6E8"), fr * (if (cardinal) 0.07f else 0.045f))
            canvas.drawLine(cx + inner * cos(rad).toFloat(), cy + inner * sin(rad).toFloat(),
                cx + fr * 0.94f * cos(rad).toFloat(), cy + fr * 0.94f * sin(rad).toFloat(), tick)
        }

        // Needle: glossy red north half, silver south half, rotated like a real reading
        canvas.save()
        canvas.rotate(35f, cx, cy)
        val len = fr * 0.66f
        val half = fr * 0.17f
        val north = Path().apply { moveTo(cx, cy - len); lineTo(cx - half, cy); lineTo(cx + half, cy); close() }
        p.shader = LinearGradient(cx - half, cy, cx + half, cy - len, Color.parseColor("#C62828"), Color.parseColor("#FF8A80"), Shader.TileMode.CLAMP)
        canvas.drawPath(north, p)
        val south = Path().apply { moveTo(cx, cy + len); lineTo(cx - half, cy); lineTo(cx + half, cy); close() }
        p.shader = LinearGradient(cx - half, cy, cx + half, cy + len, Color.parseColor("#F3F6FA"), Color.parseColor("#7F8B9C"), Shader.TileMode.CLAMP)
        canvas.drawPath(south, p)
        p.shader = null
        // centre highlight line on the north half for a faceted look
        canvas.drawLine(cx, cy - len * 0.9f, cx, cy, strokePaint(Color.parseColor("#66FFFFFF"), fr * 0.03f))
        canvas.restore()

        // Brass centre cap
        p.shader = RadialGradient(cx - fr * 0.05f, cy - fr * 0.06f, fr * 0.16f, Color.parseColor("#FFF3B0"), Color.parseColor("#B8860B"), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, fr * 0.12f, p)
        p.shader = null

        drawGlassSheen(canvas, cx, cy, fr, p)
    }
}

class LevelIconView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val p = fillPaint()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = w / 2f; val cy = h / 2f
        val r = min(w, h) / 2f * 0.95f

        drawBezel(canvas, cx, cy, r, p)

        // Glowing green liquid face
        val fr = r * 0.82f
        p.shader = RadialGradient(cx, cy, fr, intArrayOf(Color.parseColor("#4FE3A0"), Color.parseColor("#1C8F68"), Color.parseColor("#0B3D2F")),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, fr, p)
        p.shader = null

        val guide = Color.parseColor("#80FFFFFF")
        canvas.drawCircle(cx, cy, fr * 0.62f, strokePaint(guide, fr * 0.035f))
        canvas.drawCircle(cx, cy, fr * 0.30f, strokePaint(guide, fr * 0.035f))
        canvas.drawLine(cx - fr * 0.92f, cy, cx + fr * 0.92f, cy, strokePaint(Color.parseColor("#59FFFFFF"), fr * 0.03f))
        canvas.drawLine(cx, cy - fr * 0.92f, cx, cy + fr * 0.92f, strokePaint(Color.parseColor("#59FFFFFF"), fr * 0.03f))

        // Glossy bubble, slightly off-centre
        val bx = cx + fr * 0.16f; val by = cy - fr * 0.14f; val br = fr * 0.27f
        p.color = Color.parseColor("#55000000")
        canvas.drawCircle(bx + br * 0.12f, by + br * 0.18f, br, p)
        p.shader = RadialGradient(bx - br * 0.35f, by - br * 0.4f, br * 1.5f,
            intArrayOf(Color.WHITE, Color.parseColor("#C8FFE0"), Color.parseColor("#34C77B"), Color.parseColor("#12703F")),
            floatArrayOf(0f, 0.25f, 0.65f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(bx, by, br, p)
        p.shader = null

        drawGlassSheen(canvas, cx, cy, fr, p)
    }
}

class PedometerIconView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val p = fillPaint()
    private val heights = floatArrayOf(0.42f, 0.70f, 0.52f, 0.82f, 0.64f)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val left = w * 0.06f; val right = w * 0.94f
        val top = h * 0.10f; val bottom = h * 0.88f
        val slot = (right - left) / heights.size
        val barW = slot * 0.66f

        // soft glow behind the bars
        p.shader = RadialGradient(w / 2f, h * 0.55f, w * 0.75f, Color.parseColor("#9900B0FF"), Color.parseColor("#0000B0FF"), Shader.TileMode.CLAMP)
        canvas.drawCircle(w / 2f, h * 0.55f, w * 0.75f, p)
        p.shader = null

        heights.forEachIndexed { i, frac ->
            val x = left + slot * i + slot / 2f
            val bh = (bottom - top) * frac
            val rect = RectF(x - barW / 2f, bottom - bh, x + barW / 2f, bottom)
            val today = i == heights.lastIndex
            val c1 = if (today) "#FFF59D" else "#C2F3FF"
            val c2 = if (today) "#FF8F00" else "#009BFF"

            // bar shadow
            p.color = Color.parseColor("#33000000")
            canvas.drawRoundRect(RectF(rect.left + barW * 0.06f, rect.top + barW * 0.10f, rect.right + barW * 0.06f, rect.bottom + barW * 0.10f), barW * 0.3f, barW * 0.3f, p)
            // body
            p.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, Color.parseColor(c1), Color.parseColor(c2), Shader.TileMode.CLAMP)
            canvas.drawRoundRect(rect, barW * 0.3f, barW * 0.3f, p)
            // left-side gloss strip
            p.shader = LinearGradient(rect.left, 0f, rect.right, 0f, Color.parseColor("#CCFFFFFF"), Color.parseColor("#00FFFFFF"), Shader.TileMode.CLAMP)
            canvas.drawRoundRect(RectF(rect.left + barW * 0.1f, rect.top + barW * 0.12f, rect.left + barW * 0.55f, rect.bottom - barW * 0.12f), barW * 0.2f, barW * 0.2f, p)
            p.shader = null
        }

        // baseline
        canvas.drawLine(left, bottom + h * 0.04f, right, bottom + h * 0.04f, strokePaint(Color.parseColor("#BFE3FF"), h * 0.03f))
    }
}
