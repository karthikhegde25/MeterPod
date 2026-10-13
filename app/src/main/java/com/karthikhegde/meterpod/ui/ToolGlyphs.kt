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
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Which home-screen tile glyph a [ToolGlyphView] draws. */
enum class ToolGlyph {
    ANGLE, METAL, SPEED, SOUND, PROTRACTOR, RULER, ODOMETER, LIGHT,
    MAGNIFIER, CONVERTER, CALCULATOR, WORDS, SCREEN, SENSOR, NETWORK, QR
}

/**
 * Decorative, non-interactive glossy tile icons (gradients, highlights, soft shadows)
 * for the home screen. Separate from each tool's real view so the tools look unchanged.
 */
class ToolGlyphView(context: Context, private val glyph: ToolGlyph) : View(context) {

    private val p = fillPaint()

    private fun col(s: String) = Color.parseColor(s)
    private fun lg(x0: Float, y0: Float, x1: Float, y1: Float, a: String, b: String) =
        LinearGradient(x0, y0, x1, y1, col(a), col(b), Shader.TileMode.CLAMP)
    private fun rg(x: Float, y: Float, r: Float, a: String, b: String) =
        RadialGradient(x, y, r, col(a), col(b), Shader.TileMode.CLAMP)
    private fun rg3(x: Float, y: Float, r: Float, a: String, b: String, c: String) =
        RadialGradient(x, y, r, intArrayOf(col(a), col(b), col(c)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)

    /** Fills [path] with a vertical gradient, soft shadow and a white sheen on its top half. */
    private fun glossyPath(c: Canvas, path: Path, top: String, bottom: String, s: Float) {
        val b = RectF(); path.computeBounds(b, true)
        c.save(); c.translate(s * 0.015f, s * 0.03f)
        p.shader = null; p.color = col("#59000000"); c.drawPath(path, p)
        c.restore()
        p.shader = lg(b.left, b.top, b.left, b.bottom, top, bottom)
        c.drawPath(path, p)
        c.save(); c.clipPath(path)
        p.shader = LinearGradient(0f, b.top, 0f, b.centerY(), col("#80FFFFFF"), col("#00FFFFFF"), Shader.TileMode.CLAMP)
        c.drawRect(b.left, b.top, b.right, b.centerY(), p)
        c.restore()
        p.shader = null
    }

    private fun glossyRound(c: Canvas, r: RectF, rad: Float, top: String, bottom: String, s: Float) {
        glossyPath(c, Path().apply { addRoundRect(r, rad, rad, Path.Direction.CW) }, top, bottom, s)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val s = min(w, h); val cx = w / 2f; val cy = h / 2f
        when (glyph) {
            ToolGlyph.ANGLE -> angle(canvas, cx, cy, s)
            ToolGlyph.METAL -> metal(canvas, cx, cy, s)
            ToolGlyph.SPEED -> speed(canvas, cx, cy, s)
            ToolGlyph.SOUND -> sound(canvas, cx, cy, s)
            ToolGlyph.PROTRACTOR -> protractor(canvas, cx, cy, s)
            ToolGlyph.RULER -> ruler(canvas, cx, cy, s)
            ToolGlyph.ODOMETER -> odometer(canvas, cx, cy, s)
            ToolGlyph.LIGHT -> light(canvas, cx, cy, s)
            ToolGlyph.MAGNIFIER -> magnifier(canvas, cx, cy, s)
            ToolGlyph.CONVERTER -> converter(canvas, cx, cy, s)
            ToolGlyph.CALCULATOR -> calculator(canvas, cx, cy, s)
            ToolGlyph.WORDS -> words(canvas, cx, cy, s)
            ToolGlyph.SCREEN -> screen(canvas, cx, cy, s)
            ToolGlyph.SENSOR -> sensor(canvas, cx, cy, s)
            ToolGlyph.NETWORK -> network(canvas, cx, cy, s)
            ToolGlyph.QR -> qr(canvas, cx, cy, s)
        }
    }

    private fun darkFace(c: Canvas, cx: Float, cy: Float, s: Float): Float {
        val r = s / 2f * 0.95f
        drawBezel(c, cx, cy, r, p)
        val fr = r * 0.82f
        p.shader = rg(cx - fr * 0.25f, cy - fr * 0.3f, fr * 1.3f, "#3C5478", "#0B121C")
        c.drawCircle(cx, cy, fr, p); p.shader = null
        return fr
    }

    private fun cap(c: Canvas, cx: Float, cy: Float, fr: Float) {
        p.shader = rg(cx - fr * 0.05f, cy - fr * 0.06f, fr * 0.16f, "#FFF3B0", "#B8860B")
        c.drawCircle(cx, cy, fr * 0.12f, p); p.shader = null
    }

    private fun angle(c: Canvas, cx: Float, cy: Float, s: Float) {
        val fr = darkFace(c, cx, cy, s)
        val rect = RectF(cx - fr * 0.8f, cy - fr * 0.8f, cx + fr * 0.8f, cy + fr * 0.8f)
        p.shader = lg(cx, cy, cx + fr * 0.8f, cy, "#2F6FE0", "#8FD0FF")
        c.drawArc(rect, -42f, 42f, true, p); p.shader = null
        for (i in 0 until 12) {
            val a = Math.toRadians((i * 30).toDouble())
            c.drawLine(cx + fr * 0.88f * cos(a).toFloat(), cy + fr * 0.88f * sin(a).toFloat(),
                cx + fr * 0.97f * cos(a).toFloat(), cy + fr * 0.97f * sin(a).toFloat(), strokePaint(col("#C9D6E8"), fr * 0.045f))
        }
        c.drawLine(cx, cy, cx + fr * 0.85f, cy, strokePaint(col("#FFFFFF"), fr * 0.05f))
        val a = Math.toRadians(-42.0)
        c.drawLine(cx, cy, cx + fr * 0.85f * cos(a).toFloat(), cy + fr * 0.85f * sin(a).toFloat(), strokePaint(col("#FF6E6E"), fr * 0.06f))
        cap(c, cx, cy, fr)
        drawGlassSheen(c, cx, cy, fr, p)
    }

    private fun metal(c: Canvas, cx: Float, cy: Float, s: Float) {
        val t = s * 0.15f; val rc = s * 0.20f; val ay = cy - s * 0.08f; val legLen = s * 0.30f; val tip = s * 0.09f
        fun magnet(dx: Float, dy: Float, shadow: Boolean) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = t; strokeCap = Paint.Cap.BUTT }
            if (shadow) paint.color = col("#59000000")
            else paint.shader = lg(0f, ay - rc, 0f, ay + legLen, "#FF8A80", "#C62828")
            c.save(); c.translate(dx, dy)
            c.drawArc(RectF(cx - rc, ay - rc, cx + rc, ay + rc), 180f, 180f, false, paint)
            c.drawLine(cx - rc, ay, cx - rc, ay + legLen - tip, paint)
            c.drawLine(cx + rc, ay, cx + rc, ay + legLen - tip, paint)
            c.restore()
        }
        magnet(s * 0.015f, s * 0.03f, true)
        magnet(0f, 0f, false)
        // silver tips
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = t; strokeCap = Paint.Cap.BUTT
            shader = lg(cx - rc - t / 2, 0f, cx - rc + t / 2, 0f, "#FFFFFF", "#8C98A6") }
        c.drawLine(cx - rc, ay + legLen - tip, cx - rc, ay + legLen, tp)
        tp.shader = lg(cx + rc - t / 2, 0f, cx + rc + t / 2, 0f, "#FFFFFF", "#8C98A6")
        c.drawLine(cx + rc, ay + legLen - tip, cx + rc, ay + legLen, tp)
        // highlight along the arch
        c.drawArc(RectF(cx - rc - t * 0.25f, ay - rc - t * 0.25f, cx + rc + t * 0.25f, ay + rc + t * 0.25f), 200f, 100f, false,
            strokePaint(col("#80FFFFFF"), t * 0.18f))
        // iron filings pulled toward the tips
        p.color = col("#C9D2DE")
        val fy = ay + legLen + s * 0.07f
        listOf(-0.30f to 0f, -0.22f to 0.04f, -0.14f to -0.01f, 0.14f to 0.02f, 0.22f to -0.01f, 0.30f to 0.04f).forEach { (dx, dy) ->
            c.drawCircle(cx + s * dx, fy + s * dy, s * 0.018f, p)
        }
        // detection sparkle
        p.color = col("#FFD54F")
        val sp = Path().apply { val x = cx + s * 0.34f; val y = cy - s * 0.30f; val k = s * 0.07f
            moveTo(x, y - k); lineTo(x + k * 0.3f, y - k * 0.3f); lineTo(x + k, y); lineTo(x + k * 0.3f, y + k * 0.3f)
            lineTo(x, y + k); lineTo(x - k * 0.3f, y + k * 0.3f); lineTo(x - k, y); lineTo(x - k * 0.3f, y - k * 0.3f); close() }
        c.drawPath(sp, p)
    }

    private fun speed(c: Canvas, cx: Float, cy: Float, s: Float) {
        val fr = darkFace(c, cx, cy, s)
        val rr = fr * 0.68f; val rect = RectF(cx - rr, cy - rr, cx + rr, cy + rr)
        val sw = fr * 0.17f
        c.drawArc(rect, 135f, 270f, false, strokePaint(col("#26323F"), sw).apply { strokeCap = Paint.Cap.BUTT })
        c.drawArc(rect, 135f, 90f, false, strokePaint(col("#4CD964"), sw).apply { strokeCap = Paint.Cap.BUTT })
        c.drawArc(rect, 225f, 60f, false, strokePaint(col("#FFC940"), sw).apply { strokeCap = Paint.Cap.BUTT })
        c.drawArc(rect, 285f, 39f, false, strokePaint(col("#FF5252"), sw).apply { strokeCap = Paint.Cap.BUTT })
        c.save(); c.rotate(324f, cx, cy)
        val np = Path().apply { moveTo(cx + fr * 0.70f, cy); lineTo(cx, cy - fr * 0.07f); lineTo(cx, cy + fr * 0.07f); close() }
        p.shader = lg(cx, cy, cx + fr * 0.7f, cy, "#FFFFFF", "#FF5252"); c.drawPath(np, p); p.shader = null
        c.restore()
        cap(c, cx, cy, fr)
        drawGlassSheen(c, cx, cy, fr, p)
    }

    private fun sound(c: Canvas, cx: Float, cy: Float, s: Float) {
        val hs = floatArrayOf(0.30f, 0.52f, 0.78f, 1.0f, 0.70f, 0.48f, 0.28f)
        val bw = s * 0.09f; val gap = s * 0.052f
        val total = hs.size * bw + (hs.size - 1) * gap
        val colsTop = listOf("#7DF5FF", "#6FD6FF", "#7FB2FF", "#B28CFF", "#E48CFF", "#FF8CC8", "#FF9AA2")
        val colsBot = listOf("#14A8C8", "#1E88E5", "#3F51D8", "#7B2FE0", "#B72FD0", "#D81B8C", "#E53950")
        hs.forEachIndexed { i, f ->
            val x = cx - total / 2f + i * (bw + gap)
            val bh = s * 0.80f * f
            glossyRound(c, RectF(x, cy - bh / 2f, x + bw, cy + bh / 2f), bw / 2f, colsTop[i], colsBot[i], s)
        }
    }

    private fun protractor(c: Canvas, cx: Float, cy0: Float, s: Float) {
        val cy = cy0 + s * 0.24f; val r = s * 0.47f
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)
        c.save(); c.translate(s * 0.015f, s * 0.03f)
        p.color = col("#59000000"); c.drawArc(rect, 180f, 180f, true, p); c.restore()
        p.shader = lg(0f, cy - r, 0f, cy, "#F3FAFF", "#8CC0FF"); c.drawArc(rect, 180f, 180f, true, p); p.shader = null
        c.drawArc(rect, 180f, 180f, true, strokePaint(col("#FFFFFF"), s * 0.02f))
        // inner cut-out
        val ir = r * 0.42f
        p.shader = lg(0f, cy - ir, 0f, cy, "#CFE6FF", "#6FA8F0"); c.drawArc(RectF(cx - ir, cy - ir, cx + ir, cy + ir), 180f, 180f, true, p); p.shader = null
        for (a in 0..180 step 10) {
            val rad = Math.toRadians((180 + a).toDouble())
            val inner = r * (if (a % 30 == 0) 0.78f else 0.86f)
            c.drawLine(cx + inner * cos(rad).toFloat(), cy + inner * sin(rad).toFloat(),
                cx + r * 0.96f * cos(rad).toFloat(), cy + r * 0.96f * sin(rad).toFloat(), strokePaint(col("#2B4A7A"), s * 0.018f))
        }
        c.drawLine(cx - r, cy, cx + r, cy, strokePaint(col("#2B4A7A"), s * 0.022f))
        val a = Math.toRadians(-120.0)
        c.drawLine(cx, cy, cx + r * 0.88f * cos(a).toFloat(), cy + r * 0.88f * sin(a).toFloat(), strokePaint(col("#E53935"), s * 0.035f))
        p.shader = rg(cx, cy, s * 0.05f, "#FF8A80", "#C62828"); c.drawCircle(cx, cy, s * 0.04f, p); p.shader = null
        c.save(); c.clipPath(Path().apply { addArc(rect, 180f, 180f); close() })
        p.shader = LinearGradient(0f, cy - r, 0f, cy - r * 0.35f, col("#80FFFFFF"), col("#00FFFFFF"), Shader.TileMode.CLAMP)
        c.drawOval(RectF(cx - r * 0.9f, cy - r * 1.02f, cx + r * 0.9f, cy - r * 0.3f), p); p.shader = null
        c.restore()
    }

    private fun ruler(c: Canvas, cx: Float, cy: Float, s: Float) {
        c.save(); c.rotate(-35f, cx, cy)
        val l = cx - s * 0.47f; val r = cx + s * 0.47f; val t = cy - s * 0.16f; val b = cy + s * 0.16f
        glossyRound(c, RectF(l, t, r, b), s * 0.04f, "#FFE98A", "#FFA800", s)
        c.drawRoundRect(RectF(l, t, r, b), s * 0.04f, s * 0.04f, strokePaint(col("#B77900"), s * 0.015f))
        val n = 15; val step = (r - l - s * 0.1f) / (n - 1)
        for (i in 0 until n) {
            val x = l + s * 0.05f + i * step
            val len = if (i % 5 == 0) s * 0.13f else s * 0.07f
            c.drawLine(x, t, x, t + len, strokePaint(col("#6B4A00"), s * 0.014f).apply { strokeCap = Paint.Cap.BUTT })
        }
        c.restore()
    }

    private fun odometer(c: Canvas, cx: Float, cy: Float, s: Float) {
        // map pin over a glossy base ring
        val ey = cy + s * 0.34f
        p.color = col("#59000000"); c.drawOval(RectF(cx - s * 0.24f, ey - s * 0.05f, cx + s * 0.24f, ey + s * 0.07f), p)
        p.shader = lg(0f, ey - s * 0.05f, 0f, ey + s * 0.05f, "#7DF5FF", "#1E88E5")
        c.drawOval(RectF(cx - s * 0.21f, ey - s * 0.05f, cx + s * 0.21f, ey + s * 0.045f), p); p.shader = null
        val pcy = cy - s * 0.10f; val pr = s * 0.24f
        val pin = Path().apply {
            addCircle(cx, pcy, pr, Path.Direction.CW)
            moveTo(cx - pr * 0.93f, pcy + pr * 0.35f); lineTo(cx, ey - s * 0.02f); lineTo(cx + pr * 0.93f, pcy + pr * 0.35f); close()
        }
        glossyPath(c, pin, "#FF8A80", "#C62828", s)
        p.shader = rg(cx - pr * 0.1f, pcy - pr * 0.15f, pr * 0.5f, "#FFFFFF", "#E3E8EF")
        c.drawCircle(cx, pcy, pr * 0.42f, p); p.shader = null
    }

    private fun light(c: Canvas, cx: Float, cy: Float, s: Float) {
        p.shader = rg(cx, cy, s * 0.5f, "#66FFC940", "#00FFC940"); c.drawCircle(cx, cy, s * 0.5f, p); p.shader = null
        for (i in 0 until 12) {
            val a = Math.toRadians((i * 30).toDouble())
            val r0 = s * 0.31f; val r1 = s * (if (i % 2 == 0) 0.47f else 0.41f)
            c.drawLine(cx + r0 * cos(a).toFloat(), cy + r0 * sin(a).toFloat(), cx + r1 * cos(a).toFloat(), cy + r1 * sin(a).toFloat(),
                strokePaint(col("#FFB300"), s * 0.06f))
        }
        p.shader = rg3(cx - s * 0.07f, cy - s * 0.09f, s * 0.30f, "#FFFBD0", "#FFD233", "#FF8F00")
        c.drawCircle(cx, cy, s * 0.23f, p); p.shader = null
        p.shader = lg(0f, cy - s * 0.22f, 0f, cy, "#99FFFFFF", "#00FFFFFF")
        c.drawOval(RectF(cx - s * 0.16f, cy - s * 0.21f, cx + s * 0.10f, cy - s * 0.04f), p); p.shader = null
    }

    private fun magnifier(c: Canvas, cx: Float, cy: Float, s: Float) {
        val lx = cx - s * 0.09f; val ly = cy - s * 0.09f; val lr = s * 0.28f
        c.save(); c.rotate(45f, lx, ly)
        val hr = RectF(lx + lr * 0.92f, ly - s * 0.055f, lx + lr + s * 0.34f, ly + s * 0.055f)
        c.save(); c.translate(s * 0.015f, s * 0.03f); p.color = col("#59000000"); c.drawRoundRect(hr, s * 0.05f, s * 0.05f, p); c.restore()
        p.shader = lg(0f, hr.top, 0f, hr.bottom, "#A57B52", "#3E2A18"); c.drawRoundRect(hr, s * 0.05f, s * 0.05f, p); p.shader = null
        c.restore()
        // lens interior hints at the colour picker
        p.shader = SweepGradient(lx, ly, intArrayOf(col("#B3FF5252"), col("#B3FFEB3B"), col("#B34CD964"), col("#B300BCD4"), col("#B33A7BFF"), col("#B3E040FB"), col("#B3FF5252")), null)
        c.drawCircle(lx, ly, lr, p); p.shader = null
        p.shader = rg(lx, ly, lr, "#00FFFFFF", "#66FFFFFF"); c.drawCircle(lx, ly, lr, p); p.shader = null
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = s * 0.07f
            shader = LinearGradient(lx - lr, ly - lr, lx + lr, ly + lr, intArrayOf(col("#FFFFFF"), col("#9AA8BB"), col("#3A4452")), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP) }
        c.drawCircle(lx, ly, lr, ring)
        c.drawArc(RectF(lx - lr * 0.7f, ly - lr * 0.7f, lx + lr * 0.7f, ly + lr * 0.7f), 200f, 70f, false, strokePaint(col("#B3FFFFFF"), s * 0.03f))
    }

    private fun arrow(l: Float, r: Float, y: Float, s: Float, right: Boolean): Path {
        val t = s * 0.12f; val hh = s * 0.17f; val head = s * 0.24f
        return Path().apply {
            if (right) {
                moveTo(l, y - t / 2); lineTo(r - head, y - t / 2); lineTo(r - head, y - hh); lineTo(r, y)
                lineTo(r - head, y + hh); lineTo(r - head, y + t / 2); lineTo(l, y + t / 2)
            } else {
                moveTo(r, y - t / 2); lineTo(l + head, y - t / 2); lineTo(l + head, y - hh); lineTo(l, y)
                lineTo(l + head, y + hh); lineTo(l + head, y + t / 2); lineTo(r, y + t / 2)
            }
            close()
        }
    }

    private fun converter(c: Canvas, cx: Float, cy: Float, s: Float) {
        glossyPath(c, arrow(cx - s * 0.42f, cx + s * 0.42f, cy - s * 0.18f, s, true), "#9CC6FF", "#2F6FE0", s)
        glossyPath(c, arrow(cx - s * 0.42f, cx + s * 0.42f, cy + s * 0.20f, s, false), "#FFC46B", "#F57C00", s)
    }

    private fun calculator(c: Canvas, cx: Float, cy: Float, s: Float) {
        val body = RectF(cx - s * 0.30f, cy - s * 0.43f, cx + s * 0.30f, cy + s * 0.43f)
        glossyRound(c, body, s * 0.08f, "#6A7584", "#222A33", s)
        c.drawRoundRect(body, s * 0.08f, s * 0.08f, strokePaint(col("#99FFFFFF"), s * 0.012f))
        val scr = RectF(cx - s * 0.24f, cy - s * 0.37f, cx + s * 0.24f, cy - s * 0.17f)
        p.shader = lg(0f, scr.top, 0f, scr.bottom, "#D5FBE5", "#7BD9A5"); c.drawRoundRect(scr, s * 0.03f, s * 0.03f, p); p.shader = null
        c.drawLine(cx + s * 0.20f, cy - s * 0.27f, cx + s * 0.06f, cy - s * 0.27f, strokePaint(col("#1B4D36"), s * 0.035f))
        val cw = s * 0.10f; val g = s * 0.027f; val ch = s * 0.105f
        val x0 = cx - (4 * cw + 3 * g) / 2f; val y0 = cy - s * 0.10f
        for (row in 0 until 3) for (cl in 0 until 4) {
            val x = x0 + cl * (cw + g); val y = y0 + row * (ch + g)
            val orange = cl == 3
            glossyRound(c, RectF(x, y, x + cw, y + ch), s * 0.025f, if (orange) "#FFC46B" else "#FFFFFF", if (orange) "#F57C00" else "#AEB9C8", s * 0.4f)
        }
    }

    private fun words(c: Canvas, cx: Float, cy: Float, s: Float) {
        glossyRound(c, RectF(cx - s * 0.38f, cy - s * 0.36f, cx + s * 0.38f, cy + s * 0.36f), s * 0.08f, "#FFFFFF", "#CBDAF0", s)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER; textSize = s * 0.40f; isFakeBoldText = true
            shader = LinearGradient(0f, cy - s * 0.3f, 0f, cy + s * 0.04f, col("#7FB2FF"), col("#1F5FD0"), Shader.TileMode.CLAMP) }
        c.drawText("Aa", cx, cy + s * 0.04f, tp)
        val lp = strokePaint(col("#8FA6C8"), s * 0.05f)
        c.drawLine(cx - s * 0.26f, cy + s * 0.17f, cx + s * 0.26f, cy + s * 0.17f, lp)
        c.drawLine(cx - s * 0.26f, cy + s * 0.27f, cx + s * 0.10f, cy + s * 0.27f, lp)
    }

    private fun screen(c: Canvas, cx: Float, cy: Float, s: Float) {
        val body = RectF(cx - s * 0.23f, cy - s * 0.42f, cx + s * 0.23f, cy + s * 0.42f)
        glossyRound(c, body, s * 0.08f, "#5B6676", "#12171E", s)
        val scr = RectF(body.left + s * 0.03f, body.top + s * 0.07f, body.right - s * 0.03f, body.bottom - s * 0.07f)
        p.shader = LinearGradient(scr.left, scr.top, scr.right, scr.bottom, intArrayOf(col("#5AC8FF"), col("#4C6FFF"), col("#9B4DFF")), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(scr, s * 0.04f, s * 0.04f, p); p.shader = null
        c.save(); c.clipPath(Path().apply { addRoundRect(scr, s * 0.04f, s * 0.04f, Path.Direction.CW) })
        val gl = Path().apply { moveTo(scr.left, scr.top); lineTo(scr.right, scr.top); lineTo(scr.left, scr.top + (scr.bottom - scr.top) * 0.55f); close() }
        p.color = col("#40FFFFFF"); c.drawPath(gl, p)
        c.restore()
        p.color = col("#0A0E12"); c.drawCircle(cx, body.top + s * 0.035f, s * 0.014f, p)
        // size markers on the screen corners
        val mp = strokePaint(col("#E6FFFFFF"), s * 0.018f)
        val m = s * 0.07f; val i = s * 0.035f
        c.drawLine(scr.left + i, scr.top + i + m, scr.left + i, scr.top + i, mp); c.drawLine(scr.left + i, scr.top + i, scr.left + i + m, scr.top + i, mp)
        c.drawLine(scr.right - i, scr.bottom - i - m, scr.right - i, scr.bottom - i, mp); c.drawLine(scr.right - i, scr.bottom - i, scr.right - i - m, scr.bottom - i, mp)
    }

    private fun sensor(c: Canvas, cx: Float, cy: Float, s: Float) {
        val r = s / 2f * 0.95f
        drawBezel(c, cx, cy, r, p)
        val fr = r * 0.82f
        p.shader = rg(cx, cy, fr, "#0F4A36", "#03130E"); c.drawCircle(cx, cy, fr, p); p.shader = null
        c.save(); c.rotate(-40f, cx, cy)
        p.shader = SweepGradient(cx, cy, intArrayOf(col("#002EE59D"), col("#002EE59D"), col("#CC5CFFBD")), floatArrayOf(0f, 0.72f, 1f))
        c.drawCircle(cx, cy, fr, p); p.shader = null
        c.restore()
        val ring = strokePaint(col("#802EE59D"), fr * 0.03f)
        c.drawCircle(cx, cy, fr * 0.33f, ring); c.drawCircle(cx, cy, fr * 0.66f, ring)
        c.drawLine(cx - fr, cy, cx + fr, cy, ring); c.drawLine(cx, cy - fr, cx, cy + fr, ring)
        listOf(0.45f to -0.5f, -0.35f to 0.42f).forEach { (dx, dy) ->
            p.shader = rg(cx + fr * dx, cy + fr * dy, fr * 0.14f, "#FFFFFFFF", "#002EE59D")
            c.drawCircle(cx + fr * dx, cy + fr * dy, fr * 0.14f, p); p.shader = null
        }
        drawGlassSheen(c, cx, cy, fr, p)
    }

    private fun network(c: Canvas, cx: Float, cy: Float, s: Float) {
        val oy = cy + s * 0.30f
        fun arcs(dx: Float, dy: Float, shadow: Boolean) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = s * 0.09f; strokeCap = Paint.Cap.ROUND
                if (shadow) color = col("#59000000") else shader = lg(0f, oy - s * 0.52f, 0f, oy, "#8BE9FF", "#2F8CFF") }
            for (rr in floatArrayOf(0.20f, 0.36f, 0.52f)) {
                c.drawArc(RectF(cx + dx - s * rr, oy + dy - s * rr, cx + dx + s * rr, oy + dy + s * rr), 225f, 90f, false, paint)
            }
        }
        arcs(s * 0.015f, s * 0.03f, true); arcs(0f, 0f, false)
        c.save(); c.translate(s * 0.012f, s * 0.025f); p.color = col("#59000000"); c.drawCircle(cx, oy, s * 0.07f, p); c.restore()
        p.shader = rg(cx - s * 0.02f, oy - s * 0.025f, s * 0.09f, "#E9FBFF", "#2F8CFF"); c.drawCircle(cx, oy, s * 0.07f, p); p.shader = null
    }

    private fun qr(c: Canvas, cx: Float, cy: Float, s: Float) {
        val half = s * 0.42f
        val card = RectF(cx - half, cy - half, cx + half, cy + half)
        glossyRound(c, card, s * 0.09f, "#FFFFFF", "#CBD8EC", s)
        val m = s * 0.075f
        val cell = (2 * half - 2 * m) / 7f
        val gx = card.left + m; val gy = card.top + m
        val mod = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL
            shader = LinearGradient(gx, gy, gx + 7 * cell, gy + 7 * cell, col("#2B4FA8"), col("#081433"), Shader.TileMode.CLAMP) }
        fun cellRect(col: Int, row: Int, span: Int = 1, inset: Float = 0.06f) =
            RectF(gx + col * cell + cell * inset, gy + row * cell + cell * inset, gx + (col + span) * cell - cell * inset, gy + (row + span) * cell - cell * inset)
        for ((ec, er) in listOf(0 to 0, 4 to 0, 0 to 4)) {
            c.drawRoundRect(cellRect(ec, er, 3), cell * 0.35f, cell * 0.35f, mod)
            val inner = cellRect(ec + 1, er + 1, 1, 0.12f)
            p.color = col("#E8EFFA"); c.drawRoundRect(inner, cell * 0.2f, cell * 0.2f, p)
        }
        val data = arrayOf("...1...", "...0...", "...1...", "1011001", "...1011", "...0101", "...1110")
        for (row in 0 until 7) for (cl in 0 until 7) {
            if (data[row][cl] == '1') c.drawRoundRect(cellRect(cl, row), cell * 0.25f, cell * 0.25f, mod)
        }
    }
}
