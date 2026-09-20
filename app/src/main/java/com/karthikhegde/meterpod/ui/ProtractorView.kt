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
 * A classic semicircular protractor, oriented with its flat base running
 * DOWN THE LEFT edge of the view and the dome bulging out to the RIGHT -
 * pivot at the vertical middle of the left edge. 180 degrees points
 * straight up, 90 degrees points straight right (this is also where the
 * live angle readout sits, at the vertical middle of the dial), 0 degrees
 * points straight down. A single scale only - no inner/second ring.
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
                // theta: 0 = east (right), +90 = south (down), -90 = north (up) -
                // the mirror image of the drawing convention below (a = 90 - theta).
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

        val padding = PADDING
        // Flat edge runs vertically down the left side, so the horizontal
        // reach available for the dome is (w - padding), while the vertical
        // reach is split evenly above and below the pivot: (h / 2 - padding).
        val radius = min(w - padding * 2f, h / 2f - padding)
        val pivotX = padding
        val pivotY = h / 2f
        cachedPivotX = pivotX
        cachedPivotY = pivotY

        labelPaint.textSize = radius * 0.065f

        val rect = RectF(pivotX - radius, pivotY - radius, pivotX + radius, pivotY + radius)

        // Filled semicircle: flat edge on the left, dome bulging right.
        // -90 (north/top) sweeping 180 clockwise through 0 (east/right) to 90 (south/bottom).
        canvas.drawArc(rect, -90f, 180f, true, bodyFillPaint)
        canvas.drawArc(rect, -90f, 180f, true, bodyStrokePaint)

        // Ticks + labels: 180 at the top, 90 pointing right (dead center), 0 at the bottom.
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
                // The whole dial was rotated 90 (counter-clockwise) from the
                // classic flat-top layout to this flat-left one, so the
                // printed numbers rotate the same 90 along with it, exactly
                // as if a physical protractor had been turned on its side.
                canvas.save()
                canvas.rotate(90f, lx, ly)
                if (a == 90) {
                    val savedSize = labelPaint.textSize
                    val savedFakeBold = labelPaint.isFakeBoldText
                    labelPaint.textSize = savedSize * 1.35f
                    labelPaint.isFakeBoldText = true
                    canvas.drawText(a.toString(), lx, ly, labelPaint)
                    labelPaint.textSize = savedSize
                    labelPaint.isFakeBoldText = savedFakeBold
                } else {
                    canvas.drawText(a.toString(), lx, ly, labelPaint)
                }
                canvas.restore()
            }
        }

        // Needle: theta = 90 - angleDeg, same convention as the ticks above.
        val needleThetaRad = Math.toRadians((90.0 - angleDeg))
        val nx = pivotX + (radius * 0.9f) * cos(needleThetaRad).toFloat()
        val ny = pivotY + (radius * 0.9f) * sin(needleThetaRad).toFloat()
        canvas.drawLine(pivotX, pivotY, nx, ny, needlePaint)
        canvas.drawCircle(pivotX, pivotY, radius * 0.03f, pivotPaint)
    }
}
