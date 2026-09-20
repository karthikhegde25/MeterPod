package com.karthikhegde.meterpod.qr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

enum class QrDotShape { SQUARE, ROUNDED, CIRCLE }

enum class QrErrorCorrection(val zxing: ErrorCorrectionLevel, val label: String) {
    L(ErrorCorrectionLevel.L, "Low (~7%)"),
    M(ErrorCorrectionLevel.M, "Medium (~15%)"),
    Q(ErrorCorrectionLevel.Q, "Quartile (~25%)"),
    H(ErrorCorrectionLevel.H, "High (~30%)")
}

/**
 * Renders a QR code to a [Bitmap]. The encoding (text -> module grid,
 * version selection, error-correction bytes) is done by ZXing
 * (`com.google.zxing:core`).
 *
 * IMPORTANT: this uses ZXing's low-level `Encoder.encode()`, which returns
 * the true, unscaled module grid (e.g. 25x25 - one cell per actual QR
 * module). An earlier version of this file used the high-level
 * `MultiFormatWriter.encode(..., sizePx, sizePx, ...)` instead, which
 * hands back an already-rasterized `sizePx x sizePx` bitmap grid - i.e.
 * ~900x900 = 810,000 individual 1px cells, not ~25x25 modules. Treating
 * each of those 810,000 pixels as its own "module" happened to look right
 * for SQUARE (filling a 1px rect over an already-correct pixel is a
 * no-op), but for ROUNDED/CIRCLE it meant building a Path out of up to
 * ~400,000 tiny sub-pixel circles/rounded-rects - a Path that size takes
 * far too long to construct and fill, which is why those two modes never
 * produced anything. Working from the real module grid keeps the shape
 * count in the hundreds, not hundreds of thousands.
 */
object QrCodeRenderer {

    fun render(
        content: String,
        sizePx: Int,
        darkColor: Int,
        lightColor: Int,
        errorCorrection: QrErrorCorrection,
        dotShape: QrDotShape,
        quietZoneModules: Int = 4,
        logo: Bitmap? = null,
        logoSizeFraction: Float = 0.20f,
        logoPaddingFraction: Float = 0.14f,
        logoCornerRadiusFraction: Float = 0.22f
    ): Bitmap {
        require(content.isNotEmpty()) { "Content must not be empty" }

        val hints = hashMapOf<EncodeHintType, Any>(
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val ecLevel = if (logo != null) ErrorCorrectionLevel.H else errorCorrection.zxing
        val qrCode = Encoder.encode(content, ecLevel, hints)
        val byteMatrix = qrCode.matrix ?: error("QR encoding produced no matrix")
        val nativeSize = byteMatrix.width // native module count, e.g. 21, 25, 29... - always square

        val moduleCount = nativeSize + quietZoneModules * 2

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(lightColor)

        val moduleW = sizePx.toFloat() / moduleCount
        val moduleH = sizePx.toFloat() / moduleCount
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = darkColor
            style = Paint.Style.FILL
        }

        // The three big corner "finder" squares (each a 7x7 block) are what a
        // scanner locks onto first; if those get rounded/circled like the
        // data dots they melt into an unreadable blob at anything but huge
        // sizes. Every real QR styler keeps them as solid squares regardless
        // of the chosen dot shape, so we detect and reserve those three
        // 7x7 regions (in native module coordinates - no quiet-zone offset
        // to worry about here) and always draw them with SQUARE.
        val finderSize = 7
        fun isInFinderPattern(mx: Int, my: Int): Boolean {
            val inTopLeft = mx < finderSize && my < finderSize
            val inTopRight = mx >= nativeSize - finderSize && my < finderSize
            val inBottomLeft = mx < finderSize && my >= nativeSize - finderSize
            return inTopLeft || inTopRight || inBottomLeft
        }

        val squarePath = Path()
        val shapedPath = Path()

        for (my in 0 until nativeSize) {
            for (mx in 0 until nativeSize) {
                if (byteMatrix.get(mx, my).toInt() != 1) continue

                val x = mx + quietZoneModules
                val y = my + quietZoneModules
                val left = x * moduleW
                val top = y * moduleH
                val right = left + moduleW
                val bottom = top + moduleH

                if (isInFinderPattern(mx, my)) {
                    squarePath.addRect(left, top, right, bottom, Path.Direction.CW)
                    continue
                }

                when (dotShape) {
                    QrDotShape.SQUARE -> squarePath.addRect(left, top, right, bottom, Path.Direction.CW)
                    QrDotShape.ROUNDED -> {
                        val r = (moduleW.coerceAtMost(moduleH) * 0.35f).coerceAtLeast(1f)
                        shapedPath.addRoundRect(RectF(left, top, right, bottom), r, r, Path.Direction.CW)
                    }
                    QrDotShape.CIRCLE -> {
                        val cx = left + moduleW / 2f
                        val cy = top + moduleH / 2f
                        val radius = (minOf(moduleW, moduleH) / 2f * 0.9f).coerceAtLeast(1f)
                        shapedPath.addCircle(cx, cy, radius, Path.Direction.CW)
                    }
                }
            }
        }

        if (!squarePath.isEmpty) canvas.drawPath(squarePath, paint)
        if (!shapedPath.isEmpty) canvas.drawPath(shapedPath, paint)

        if (logo != null) {
            drawLogo(canvas, logo, sizePx, lightColor, logoSizeFraction, logoPaddingFraction, logoCornerRadiusFraction)
        }

        return bitmap
    }

    private fun drawLogo(
        canvas: Canvas,
        logo: Bitmap,
        sizePx: Int,
        lightColor: Int,
        logoSizeFraction: Float,
        logoPaddingFraction: Float,
        logoCornerRadiusFraction: Float
    ) {
        val logoSize = (sizePx * logoSizeFraction).toInt().coerceAtLeast(1)
        val pad = logoSize * logoPaddingFraction
        val left = (sizePx - logoSize) / 2f
        val top = (sizePx - logoSize) / 2f
        val cornerRadius = logoSize * logoCornerRadiusFraction

        val bgRect = RectF(left - pad, top - pad, left + logoSize + pad, top + logoSize + pad)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lightColor }
        canvas.drawRoundRect(bgRect, cornerRadius + pad, cornerRadius + pad, bgPaint)

        val scaledLogo = Bitmap.createScaledBitmap(logo, logoSize, logoSize, true)
        val roundedLogo = Bitmap.createBitmap(logoSize, logoSize, Bitmap.Config.ARGB_8888)
        val logoCanvas = Canvas(roundedLogo)
        val clipPath = Path().apply {
            addRoundRect(RectF(0f, 0f, logoSize.toFloat(), logoSize.toFloat()), cornerRadius, cornerRadius, Path.Direction.CW)
        }
        logoCanvas.clipPath(clipPath)
        logoCanvas.drawBitmap(scaledLogo, 0f, 0f, null)

        canvas.drawBitmap(roundedLogo, left, top, null)
    }
}
