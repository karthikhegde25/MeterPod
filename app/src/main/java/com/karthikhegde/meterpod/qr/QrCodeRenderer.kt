package com.karthikhegde.meterpod.qr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

/**
 * Renders a QR code to a [Bitmap] with custom module shapes and colors.
 *
 * IMPORTANT: this uses ZXing's low-level [Encoder.encode] API, NOT the
 * high-level [com.google.zxing.MultiFormatWriter]/[com.google.zxing.qrcode.QRCodeWriter].
 * The high-level writer pre-rasterizes the code into a pixel grid at the
 * requested output size (e.g. 1024x1024 individual 1px modules), which is
 * useless for drawing custom per-module shapes (rounded/circle dots) -
 * attempting to do so means building a Path out of hundreds of thousands
 * of tiny sub-pixel rectangles, which is both wrong-looking and extremely
 * slow. The low-level [Encoder.encode] call instead returns the QR code's
 * true native module grid (e.g. 21x21, 25x25, 29x29...) via
 * `qrCode.matrix: ByteMatrix`, which is what should actually be iterated
 * over to draw one shape per module.
 */
enum class QrDotShape { SQUARE, ROUNDED, CIRCLE }

/** Shape used for the finder "eye" (the solid ball) and for the finder frame (the ring around it); both are chosen independently of [QrDotShape]. */
enum class QrEyeShape { SQUARE, ROUNDED, CIRCLE }

enum class QrErrorCorrection(val label: String, val zxing: ErrorCorrectionLevel) {
    LOW("Low (7%)", ErrorCorrectionLevel.L),
    MEDIUM("Medium (15%)", ErrorCorrectionLevel.M),
    QUARTILE("Quartile (25%)", ErrorCorrectionLevel.Q),
    HIGH("High (30%)", ErrorCorrectionLevel.H)
}

object QrCodeRenderer {

    private const val DEFAULT_QUIET_ZONE_MODULES = 4

    fun render(
        content: String,
        sizePx: Int,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE,
        dotShape: QrDotShape = QrDotShape.SQUARE,
        eyeShape: QrEyeShape = QrEyeShape.SQUARE,
        frameShape: QrEyeShape = QrEyeShape.SQUARE,
        /** 0..1: how round the corners of square/rounded pixels are (1 = fully round). */
        pixelRounding: Float = 0f,
        errorCorrection: QrErrorCorrection = QrErrorCorrection.MEDIUM,
        logo: Bitmap? = null,
        logoSizeFraction: Float = 0.2f
    ): Bitmap {
        require(content.isNotEmpty()) { "Content must not be empty" }

        val hints = hashMapOf<EncodeHintType, Any>(EncodeHintType.CHARACTER_SET to "UTF-8")
        val ecLevel = if (logo != null) ErrorCorrectionLevel.H else errorCorrection.zxing
        val qrCode = Encoder.encode(content, ecLevel, hints)
        val byteMatrix = qrCode.matrix ?: error("QR encoding produced no matrix")

        val nativeSize = byteMatrix.width // true module count, e.g. 21/25/29...
        val quietZoneModules = DEFAULT_QUIET_ZONE_MODULES
        val moduleCount = nativeSize + quietZoneModules * 2
        val moduleSizePx = sizePx.toFloat() / moduleCount
        val offsetPx = quietZoneModules * moduleSizePx

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(backgroundColor)

        val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = foregroundColor
            style = Paint.Style.FILL
        }

        val squarePath = Path()
        val shapedPath = Path()
        val cornerRadius = moduleSizePx * 0.5f * pixelRounding.coerceIn(0f, 1f)

        for (my in 0 until nativeSize) {
            for (mx in 0 until nativeSize) {
                if (byteMatrix.get(mx, my).toInt() != 1) continue

                val left = offsetPx + mx * moduleSizePx
                val top = offsetPx + my * moduleSizePx
                val right = left + moduleSizePx
                val bottom = top + moduleSizePx
                val rect = RectF(left, top, right, bottom)

                if (isInFinderPattern(mx, my, nativeSize)) {
                    // Eyes are drawn separately below as a ring + pupil, styled
                    // by eyeShape independently of the data dots - skip them here.
                    continue
                } else {
                    when (dotShape) {
                        QrDotShape.SQUARE, QrDotShape.ROUNDED ->
                            if (cornerRadius < 0.5f) squarePath.addRect(rect, Path.Direction.CW)
                            else shapedPath.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
                        QrDotShape.CIRCLE -> {
                            val cx = (left + right) / 2f
                            val cy = (top + bottom) / 2f
                            val r = (moduleSizePx / 2f * 0.92f).coerceAtLeast(1f)
                            shapedPath.addCircle(cx, cy, r, Path.Direction.CW)
                        }
                    }
                }
            }
        }

        canvas.drawPath(squarePath, fgPaint)
        canvas.drawPath(shapedPath, fgPaint)

        drawEyes(canvas, fgPaint, eyeShape, frameShape, nativeSize, moduleSizePx, offsetPx)

        if (logo != null) {
            drawLogo(canvas, logo, sizePx, logoSizeFraction, backgroundColor)
        }

        return bitmap
    }

    /** True for modules inside any of the three 7x7 finder-pattern corner squares. */
    private fun isInFinderPattern(mx: Int, my: Int, nativeSize: Int): Boolean {
        val inTopLeft = mx < 7 && my < 7
        val inTopRight = mx >= nativeSize - 7 && my < 7
        val inBottomLeft = mx < 7 && my >= nativeSize - 7
        return inTopLeft || inTopRight || inBottomLeft
    }

    /**
     * Draws the three finder-pattern "eyes" as a 7-module outer ring plus a
     * 3-module solid pupil (the standard QR eye structure: 7x7 border, 1
     * module thick, around a 5x5 gap, with a 3x3 solid center), each shaped
     * per [eyeShape] rather than as a grid of tiny per-module squares - a
     * ring/pupil pair is how real "styled QR" apps shape eyes, and looks
     * like one coherent eye instead of a cluster of small shapes.
     */
    private fun drawEyes(
        canvas: Canvas,
        paint: Paint,
        eyeShape: QrEyeShape,
        frameShape: QrEyeShape,
        nativeSize: Int,
        moduleSizePx: Float,
        offsetPx: Float
    ) {
        val eyeTopLeftModules = listOf(
            0 to 0,
            (nativeSize - 7) to 0,
            0 to (nativeSize - 7)
        )

        val path = Path()
        for ((mx, my) in eyeTopLeftModules) {
            val left = offsetPx + mx * moduleSizePx
            val top = offsetPx + my * moduleSizePx
            addEyeRing(path, frameShape, left, top, moduleSizePx)
            addEyePupil(path, eyeShape, left, top, moduleSizePx)
        }
        canvas.drawPath(path, paint)
    }

    /** Adds the outer 7-module ring (7x7 shape with a concentric 5x5 hole) for one eye. */
    private fun addEyeRing(path: Path, eyeShape: QrEyeShape, left: Float, top: Float, moduleSizePx: Float) {
        val outerSize = moduleSizePx * 7f
        val innerSize = moduleSizePx * 5f
        val innerInset = moduleSizePx * 1f
        val outerRect = RectF(left, top, left + outerSize, top + outerSize)
        val innerRect = RectF(left + innerInset, top + innerInset, left + innerInset + innerSize, top + innerInset + innerSize)

        when (eyeShape) {
            QrEyeShape.SQUARE -> {
                path.addRect(outerRect, Path.Direction.CW)
                path.addRect(innerRect, Path.Direction.CCW)
            }
            QrEyeShape.ROUNDED -> {
                val outerRadius = outerSize * 0.22f
                val innerRadius = innerSize * 0.22f
                path.addRoundRect(outerRect, outerRadius, outerRadius, Path.Direction.CW)
                path.addRoundRect(innerRect, innerRadius, innerRadius, Path.Direction.CCW)
            }
            QrEyeShape.CIRCLE -> {
                path.addOval(outerRect, Path.Direction.CW)
                path.addOval(innerRect, Path.Direction.CCW)
            }
        }
    }

    /** Adds the solid 3-module pupil centered inside one eye. */
    private fun addEyePupil(path: Path, eyeShape: QrEyeShape, left: Float, top: Float, moduleSizePx: Float) {
        val pupilSize = moduleSizePx * 3f
        val pupilInset = moduleSizePx * 2f
        val pupilRect = RectF(left + pupilInset, top + pupilInset, left + pupilInset + pupilSize, top + pupilInset + pupilSize)

        when (eyeShape) {
            QrEyeShape.SQUARE -> path.addRect(pupilRect, Path.Direction.CW)
            QrEyeShape.ROUNDED -> {
                val radius = pupilSize * 0.28f
                path.addRoundRect(pupilRect, radius, radius, Path.Direction.CW)
            }
            QrEyeShape.CIRCLE -> path.addOval(pupilRect, Path.Direction.CW)
        }
    }

    private fun drawLogo(
        canvas: Canvas,
        logo: Bitmap,
        sizePx: Int,
        logoSizeFraction: Float,
        backgroundColor: Int
    ) {
        val logoSize = (sizePx * logoSizeFraction).toInt().coerceAtLeast(1)
        val left = (sizePx - logoSize) / 2f
        val top = (sizePx - logoSize) / 2f
        val padding = logoSize * 0.08f

        // Rounded-corner backing plate behind the logo so it's legible
        // against whatever module pattern sits underneath it.
        val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = backgroundColor
            style = Paint.Style.FILL
        }
        val plateRect = RectF(left - padding, top - padding, left + logoSize + padding, top + logoSize + padding)
        canvas.drawRoundRect(plateRect, logoSize * 0.18f, logoSize * 0.18f, platePaint)

        val scaledLogo = Bitmap.createScaledBitmap(logo, logoSize, logoSize, true)
        val clipPath = Path().apply {
            addRoundRect(
                RectF(left, top, left + logoSize, top + logoSize),
                logoSize * 0.18f, logoSize * 0.18f, Path.Direction.CW
            )
        }
        canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawBitmap(scaledLogo, left, top, null)
        canvas.restore()
    }
}
