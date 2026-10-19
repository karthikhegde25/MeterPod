package com.karthikhegde.meterpod.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A semicircular protractor rotated 90 degrees from the classic
 * orientation: the flat edge runs vertically along the LEFT side, and
 * the dome bulges out to the RIGHT. The pivot sits at the vertical
 * middle of the left edge. A single scale runs 0 at the bottom, 90
 * straight out to the right (the middle), and 180 at the top - so tick
 * labels and the needle all read correctly once the whole dial is
 * thought of as "rotated 90 degrees clockwise" from a normal protractor.
 *
 * Because the whole dial is rotated, the tick-number labels and the
 * on-screen Lock button are both rotated 90 degrees too, so they read
 * the same way the dial itself is oriented rather than sideways.
 *
 * The needle can be set two ways: dragging directly on the view, or via
 * setAngle() calls from outside (e.g. the +/- steppers on the real tool
 * screen). [isIconPreview] disables touch handling entirely so an instance
 * used purely as a decorative home-grid tile icon doesn't swallow the tap
 * before it reaches the tile's own click listener.
 */
class ProtractorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        private const val PADDING = 24f
    }

    private var angleDeg = 90f

    /** When true, this instance is purely decorative (a tile icon) and never handles touch. */
    var isIconPreview = false

    /** When true, touch-dragging the needle is ignored (the caller decides whether +/- still work). */
    var isLocked = false

    /** Fired only from an actual touch-drag, not from programmatic setAngle() calls. */
    var onAngleDragged: ((Float) -> Unit)? = null

    // Cached from the last onDraw so touch handling can convert a tap position
    // into an angle using the same pivot that was actually drawn.
    private var cachedPivotX = 0f
    private var cachedPivotY = 0f

    private val bodyFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val bodyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#CCEAF6FF")
    }

    private val majorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#F2FAFF")
    }

    private val minorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.parseColor("#B8CCE0")
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F2FAFF")
        setShadowLayer(3f, 1f, 2f, Color.parseColor("#99000000"))
        textAlign = Paint.Align.CENTER
    }

    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#E53935")
    }

    private val bevelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val pivotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#E53935")
    }

    fun setAngle(degrees: Float) {
        angleDeg = degrees.coerceIn(0f, 180f)
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isIconPreview) return false
        if (isLocked) return true

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val dx = event.x - cachedPivotX
                val dy = event.y - cachedPivotY
                val theta = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                val angle = (90f - theta).coerceIn(0f, 180f)

                angleDeg = angle
                invalidate()
                onAngleDragged?.invoke(angleDeg)
                return true
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Tile-icon mode: tiny padding and horizontally centred so the dial fills the icon.
        val pad = if (isIconPreview) 3f else PADDING
        val radius = min(w - pad * 2f, h / 2f - pad)
        val pivotX = if (isIconPreview) (w - radius) / 2f else PADDING
        val pivotY = h / 2f
        cachedPivotX = pivotX
        cachedPivotY = pivotY

        labelPaint.textSize = radius * 0.065f

        val rect = RectF(pivotX - radius, pivotY - radius, pivotX + radius, pivotY + radius)

        // Filled semicircle, flat edge along the left, dome bulging right.
        // 3D transparent plastic: soft drop shadow, translucent glass body, bevelled rim, glossy sheen.
        if (!isIconPreview) {
            bodyFillPaint.shader = null
            bodyFillPaint.color = Color.parseColor("#44000000")
            canvas.save(); canvas.translate(radius * 0.03f, radius * 0.04f)
            canvas.drawArc(rect, -90f, 180f, true, bodyFillPaint)
            canvas.restore()
        }
        bodyFillPaint.shader = RadialGradient(
            pivotX, pivotY, radius,
            intArrayOf(Color.parseColor("#26FFFFFF"), Color.parseColor("#3A9CCBFF"), Color.parseColor("#5CBFE0FF")),
            floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawArc(rect, -90f, 180f, true, bodyFillPaint)
        // bright upper-left sheen across the dome
        bodyFillPaint.shader = LinearGradient(pivotX, pivotY - radius, pivotX + radius * 0.6f, pivotY, Color.parseColor("#55FFFFFF"), Color.parseColor("#00FFFFFF"), Shader.TileMode.CLAMP)
        canvas.drawArc(rect, -90f, 90f, true, bodyFillPaint)
        bodyFillPaint.shader = null
        // outer rim (lit) + inner bevel line (shaded)
        bodyStrokePaint.shader = LinearGradient(pivotX, pivotY - radius, pivotX + radius, pivotY + radius, Color.parseColor("#FFFFFFFF"), Color.parseColor("#66A8C8E8"), Shader.TileMode.CLAMP)
        canvas.drawArc(rect, -90f, 180f, true, bodyStrokePaint)
        bodyStrokePaint.shader = null
        val inner = RectF(pivotX - radius * 0.965f, pivotY - radius * 0.965f, pivotX + radius * 0.965f, pivotY + radius * 0.965f)
        bevelPaint.color = Color.parseColor("#55000000")
        canvas.drawArc(inner, -90f, 180f, false, bevelPaint)

        // Ticks + labels: 0 at the bottom, 90 pointing right (the middle), 180 at the top.
        for (a in 0..180) {
            val thetaRad = Math.toRadians((90 - a).toDouble())
            val cosT = cos(thetaRad).toFloat()
            val sinT = sin(thetaRad).toFloat()

            val isMajor = a % 10 == 0
            val isMedium = a % 5 == 0
            val tickPaint = if (isMajor) majorTickPaint else minorTickPaint
            val innerR = when {
                isMajor -> radius * 0.86f
                isMedium -> radius * 0.90f
                else -> radius * 0.94f
            }

            val x1 = pivotX + innerR * cosT
            val y1 = pivotY + innerR * sinT
            val x2 = pivotX + radius * cosT
            val y2 = pivotY + radius * sinT
            canvas.drawLine(x1, y1, x2, y2, tickPaint)

            if (isMajor && !isIconPreview) {
                val labelR = radius * 0.74f
                val lx = pivotX + labelR * cosT
                val ly = pivotY + labelR * sinT - labelPaint.ascent() / 2f

                canvas.save()
                canvas.rotate(90f, lx, ly)
                if (a == 90) {
                    val savedSize = labelPaint.textSize
                    val savedBold = labelPaint.isFakeBoldText
                    labelPaint.textSize = savedSize * 1.35f
                    labelPaint.isFakeBoldText = true
                    canvas.drawText(a.toString(), lx, ly, labelPaint)
                    labelPaint.textSize = savedSize
                    labelPaint.isFakeBoldText = savedBold
                } else {
                    canvas.drawText(a.toString(), lx, ly, labelPaint)
                }
                canvas.restore()
            }
        }

        // Needle
        val needleThetaRad = Math.toRadians((90.0 - angleDeg))
        val nx = pivotX + (radius * 0.9f) * cos(needleThetaRad).toFloat()
        val ny = pivotY + (radius * 0.9f) * sin(needleThetaRad).toFloat()
        // needle: shadow, body, highlight - so it reads as a raised red pointer on the glass
        needlePaint.color = Color.parseColor("#55000000")
        canvas.drawLine(pivotX + 3f, pivotY + 4f, nx + 3f, ny + 4f, needlePaint)
        needlePaint.color = Color.parseColor("#E53935")
        canvas.drawLine(pivotX, pivotY, nx, ny, needlePaint)
        needlePaint.strokeWidth = 1.5f
        needlePaint.color = Color.parseColor("#99FFB4B0")
        canvas.drawLine(pivotX - 1f, pivotY - 1f, nx - 1f, ny - 1f, needlePaint)
        needlePaint.strokeWidth = 5f
        pivotPaint.shader = RadialGradient(pivotX - radius * 0.01f, pivotY - radius * 0.012f, radius * 0.04f, Color.parseColor("#FFB4B0"), Color.parseColor("#B71C1C"), Shader.TileMode.CLAMP)
        canvas.drawCircle(pivotX, pivotY, radius * 0.03f, pivotPaint)
        pivotPaint.shader = null
    }
}
