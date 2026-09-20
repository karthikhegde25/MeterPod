package com.karthikhegde.meterpod.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

/**
 * Shows a bitmap zoomed/panned freely (pinch to zoom, drag to pan), and
 * reports the exact source-image pixel under a tap via [onPixelSelected].
 * Bitmap filtering is disabled once zoomed in past 1:1 scale, so
 * individual pixels render as sharp squares instead of being smoothed
 * together — the point of a pixel-level magnifier is to actually see the
 * pixels, not a blurred-up approximation of them.
 */
class MagnifierView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onPixelSelected: ((x: Int, y: Int, color: Int) -> Unit)? = null

    private var bitmap: Bitmap? = null

    private var scale = 1f
    private var minScale = 1f
    private val maxScale = 64f
    private var translateX = 0f
    private var translateY = 0f

    private var selectedX = -1
    private var selectedY = -1

    private val matrix = Matrix()
    private val bitmapPaint = Paint().apply { isFilterBitmap = true }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.parseColor("#332B2F33")
    }

    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#FF5252")
    }

    private lateinit var scaleDetector: ScaleGestureDetector
    private lateinit var gestureDetector: GestureDetector

    init {
        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val newScale = (scale * detector.scaleFactor).coerceIn(minScale, maxScale)
                val focusX = detector.focusX
                val focusY = detector.focusY
                // Keep the point under the fingers fixed while scaling.
                translateX = focusX - (focusX - translateX) * (newScale / scale)
                translateY = focusY - (focusY - translateY) * (newScale / scale)
                scale = newScale
                clampTranslation()
                invalidate()
                return true
            }
        })

        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                selectPixelAt(e.x, e.y)
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                translateX -= dx
                translateY -= dy
                clampTranslation()
                invalidate()
                return true
            }
        })
    }

    fun setBitmap(bmp: Bitmap) {
        bitmap = bmp
        selectedX = -1
        selectedY = -1
        if (width > 0 && height > 0) {
            // Already laid out (e.g. loading a second image) - fit immediately.
            fitToView()
        }
        // Also handled in onSizeChanged as a backstop: the very first time this is
        // shown, the container was likely View.GONE until just now, so this view
        // may not have valid dimensions yet - onSizeChanged fires once real layout
        // happens and finishes the job then instead of relying on posting a runnable
        // and hoping layout has already completed by the time it runs.
        invalidate()
    }

    fun clear() {
        bitmap = null
        selectedX = -1
        selectedY = -1
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (bitmap != null && w > 0 && h > 0) {
            fitToView()
            invalidate()
        }
    }

    private fun fitToView() {
        val bmp = bitmap ?: return
        if (width <= 0 || height <= 0) return
        val scaleX = width.toFloat() / bmp.width
        val scaleY = height.toFloat() / bmp.height
        minScale = minOf(scaleX, scaleY)
        scale = minScale
        translateX = (width - bmp.width * scale) / 2f
        translateY = (height - bmp.height * scale) / 2f
    }

    private fun clampTranslation() {
        val bmp = bitmap ?: return
        val scaledWidth = bmp.width * scale
        val scaledHeight = bmp.height * scale

        translateX = if (scaledWidth <= width) {
            (width - scaledWidth) / 2f
        } else {
            translateX.coerceIn(width - scaledWidth, 0f)
        }
        translateY = if (scaledHeight <= height) {
            (height - scaledHeight) / 2f
        } else {
            translateY.coerceIn(height - scaledHeight, 0f)
        }
    }

    private fun selectPixelAt(viewX: Float, viewY: Float) {
        val bmp = bitmap ?: return
        val bitmapX = ((viewX - translateX) / scale).toInt()
        val bitmapY = ((viewY - translateY) / scale).toInt()
        if (bitmapX in 0 until bmp.width && bitmapY in 0 until bmp.height) {
            selectedX = bitmapX
            selectedY = bitmapY
            val color = bmp.getPixel(bitmapX, bitmapY)
            invalidate()
            onPixelSelected?.invoke(bitmapX, bitmapY, color)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress) {
            gestureDetector.onTouchEvent(event)
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap ?: return

        bitmapPaint.isFilterBitmap = scale <= 4f

        matrix.reset()
        matrix.postScale(scale, scale)
        matrix.postTranslate(translateX, translateY)
        canvas.drawBitmap(bmp, matrix, bitmapPaint)

        // Pixel grid once zoomed in enough for individual pixels to be usefully visible.
        if (scale >= 12f) {
            val startX = maxOf(0, ((0 - translateX) / scale).toInt())
            val endX = minOf(bmp.width, ((width - translateX) / scale).toInt() + 1)
            val startY = maxOf(0, ((0 - translateY) / scale).toInt())
            val endY = minOf(bmp.height, ((height - translateY) / scale).toInt() + 1)

            for (x in startX..endX) {
                val screenX = translateX + x * scale
                canvas.drawLine(screenX, 0f, screenX, height.toFloat(), gridPaint)
            }
            for (y in startY..endY) {
                val screenY = translateY + y * scale
                canvas.drawLine(0f, screenY, width.toFloat(), screenY, gridPaint)
            }
        }

        if (selectedX >= 0 && selectedY >= 0) {
            val rect = RectF(
                translateX + selectedX * scale,
                translateY + selectedY * scale,
                translateX + (selectedX + 1) * scale,
                translateY + (selectedY + 1) * scale
            )
            canvas.drawRect(rect, crosshairPaint)
        }
    }
}
