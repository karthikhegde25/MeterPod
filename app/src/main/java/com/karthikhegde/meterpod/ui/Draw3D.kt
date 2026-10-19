package com.karthikhegde.meterpod.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient

/** Shared gradient helpers that give the dial views a lit, three-dimensional look. */
internal object Draw3D {
    fun c(hex: String) = Color.parseColor(hex)

    /** Polished chrome ring: bright/dark sweep with a thin dark edge. */
    fun chromeRing(canvas: Canvas, cx: Float, cy: Float, r: Float, thickness: Float, p: Paint) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = thickness
        p.shader = SweepGradient(cx, cy,
            intArrayOf(c("#F6F9FC"), c("#7C8793"), c("#E8EDF3"), c("#58626E"), c("#F6F9FC"), c("#7C8793"), c("#E8EDF3"), c("#58626E"), c("#F6F9FC")), null)
        canvas.drawCircle(cx, cy, r, p)
        p.shader = null
        p.strokeWidth = thickness * 0.12f
        p.color = c("#55000000")
        canvas.drawCircle(cx, cy, r + thickness / 2f, p)
        canvas.drawCircle(cx, cy, r - thickness / 2f, p)
        p.style = Paint.Style.FILL
    }

    /** Dark recessed dial face. */
    fun dialFace(canvas: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx - r * 0.25f, cy - r * 0.3f, r * 1.3f,
            intArrayOf(c("#26385C"), c("#101C34"), c("#070D1B")), floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, p)
        p.shader = null
    }

    /** Glass reflection over a circle. */
    fun glass(canvas: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx - r * 0.35f, cy - r * 0.45f, r * 0.95f,
            intArrayOf(c("#4DFFFFFF"), c("#12FFFFFF"), c("#00FFFFFF")), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, p)
        p.shader = null
    }

    /** A lit sphere (bubble, pivot cap): highlight spot, body colour, dark rim. */
    fun sphere(canvas: Canvas, cx: Float, cy: Float, r: Float, light: String, base: String, dark: String, p: Paint) {
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx - r * 0.35f, cy - r * 0.4f, r * 1.4f,
            intArrayOf(c(light), c(base), c(dark)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, p)
        p.shader = null
        p.color = c("#99FFFFFF")
        canvas.drawCircle(cx - r * 0.38f, cy - r * 0.42f, r * 0.17f, p)
    }

    /** Soft ellipse shadow under an object. */
    fun dropShadow(canvas: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cy, r, intArrayOf(c("#88000000"), c("#00000000")), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, p)
        p.shader = null
    }

    /** Needle with shadow, shaded two-sided body and a highlight line. */
    fun needle(canvas: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, width: Float, p: Paint) {
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = width; p.color = c("#66000000")
        canvas.drawLine(x0 + width * 0.35f, y0 + width * 0.5f, x1 + width * 0.35f, y1 + width * 0.5f, p)
        p.strokeWidth = width; p.color = c("#C62828")
        canvas.drawLine(x0, y0, x1, y1, p)
        p.strokeWidth = width * 0.55f; p.color = c("#FF6A5E")
        canvas.drawLine(x0 - width * 0.1f, y0 - width * 0.1f, x1 - width * 0.1f, y1 - width * 0.1f, p)
        p.strokeWidth = width * 0.15f; p.color = c("#AAFFFFFF")
        canvas.drawLine(x0 - width * 0.18f, y0 - width * 0.18f, x1 - width * 0.18f, y1 - width * 0.18f, p)
        p.style = Paint.Style.FILL
    }

    /** Draws text with an extruded look (dark copies behind, bright face on top). */
    fun depthText(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint, depth: Float) {
        val face = paint.color
        paint.color = c("#CC000000")
        for (i in 3 downTo 1) canvas.drawText(text, x + depth * i / 3f, y + depth * i / 3f * 1.2f, paint)
        paint.color = face
        canvas.drawText(text, x, y, paint)
    }
}
