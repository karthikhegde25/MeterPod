package com.karthikhegde.meterpod.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
        color = Color.parseColor("#2B2F33")
    }

    private val majorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#2B2F33")
    }

    private val minorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.parseColor("#6B7278")
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1B1E21")
        textAlign = Paint.Align.CENTER
    }

    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#E53935")
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
        canvas.drawArc(rect, -90f, 180f, true, bodyFillPaint)
        canvas.drawArc(rect, -90f, 180f, true, bodyStrokePaint)

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
        canvas.drawLine(pivotX, pivotY, nx, ny, needlePaint)
        canvas.drawCircle(pivotX, pivotY, radius * 0.03f, pivotPaint)
    }
}
