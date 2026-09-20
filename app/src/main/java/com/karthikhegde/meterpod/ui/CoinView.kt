package com.karthikhegde.meterpod.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.karthikhegde.meterpod.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A coin rendered from two real photos (res/drawable-nodpi/coin_head.jpg,
 * coin_tail.jpg) rather than drawn shapes - each face is painted with a
 * BitmapShader clipped to a circle, so the square photo's white corners
 * are simply outside the circle and never drawn.
 *
 * [toss] runs a squash-and-flip animation: the coin's horizontal scale
 * oscillates 1 -> 0 -> 1 repeatedly (simulating it spinning edge-on and
 * back, since a flat Canvas view can't do a true 3D flip), the face shown
 * alternates every half-cycle (swapping which photo's shader is bound
 * right as the coin is edge-on and invisible, so the swap itself is never
 * seen), and a sine-shaped vertical bounce gives it a tossed-in-the-air
 * feel. The final face is forced to the given result when the animation
 * ends.
 */
class CoinView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onFlipComplete: ((Boolean) -> Unit)? = null

    private var showingHead = true
    private var isFlipping = false
    private var squashFactor = 1f
    private var bounceOffsetPx = 0f

    private val headBitmap: Bitmap by lazy { BitmapFactory.decodeResource(resources, R.drawable.coin_head) }
    private val tailBitmap: Bitmap by lazy { BitmapFactory.decodeResource(resources, R.drawable.coin_tail) }
    private val headShader: BitmapShader by lazy { BitmapShader(headBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    private val tailShader: BitmapShader by lazy { BitmapShader(tailBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }

    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#B8860B")
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#40000000")
    }
    private val shaderMatrix = Matrix()

    /** Starts the toss animation; [result] (true = heads) is the face it lands on. */
    fun toss(result: Boolean) {
        if (isFlipping) return
        isFlipping = true

        val halfFlipCount = 7
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 1400
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.addUpdateListener { anim ->
            val progress = anim.animatedValue as Float
            val cyclePosition = (progress * halfFlipCount) % 1f
            squashFactor = cos(cyclePosition * Math.PI).toFloat()

            val cycleIndex = (progress * halfFlipCount).toInt()
            showingHead = cycleIndex % 2 == 0

            bounceOffsetPx = -sin(progress * Math.PI).toFloat() * (height * 0.16f)
            invalidate()
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                isFlipping = false
                showingHead = result
                squashFactor = 1f
                bounceOffsetPx = 0f
                invalidate()
                onFlipComplete?.invoke(result)
            }
        })
        animator.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val baseCy = h / 2f
        val radius = min(w, h) / 2f * 0.72f

        // Shadow on the "ground", fixed position, fades out as the coin rises.
        val liftFraction = (-bounceOffsetPx / (h * 0.16f)).coerceIn(0f, 1f)
        shadowPaint.alpha = ((1f - liftFraction) * 90).toInt()
        canvas.drawOval(
            cx - radius * 0.8f, baseCy + radius * 0.95f,
            cx + radius * 0.8f, baseCy + radius * 1.15f,
            shadowPaint
        )

        val cy = baseCy + bounceOffsetPx
        val shader = if (showingHead) headShader else tailShader
        val bitmapForScale = if (showingHead) headBitmap else tailBitmap

        facePaint.shader = updatedShaderMatrix(shader, bitmapForScale, cx, cy, radius)

        canvas.save()
        canvas.scale(abs(squashFactor).coerceAtLeast(0.05f), 1f, cx, cy)

        canvas.drawCircle(cx, cy, radius, facePaint)
        canvas.drawCircle(cx, cy, radius, rimPaint)

        canvas.restore()
    }

    /** Maps the (square) coin photo onto the circle's bounding box, cropping the corners away via the circular clip in onDraw. */
    private fun updatedShaderMatrix(shader: BitmapShader, bitmap: Bitmap, cx: Float, cy: Float, radius: Float): BitmapShader {
        val scale = (radius * 2f) / min(bitmap.width, bitmap.height).toFloat()
        shaderMatrix.reset()
        shaderMatrix.setScale(scale, scale)
        shaderMatrix.postTranslate(cx - radius, cy - radius)
        shader.setLocalMatrix(shaderMatrix)
        return shader
    }
}
