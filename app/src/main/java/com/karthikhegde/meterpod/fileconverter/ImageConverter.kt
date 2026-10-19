package com.karthikhegde.meterpod.fileconverter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import android.net.Uri
import com.karthikhegde.meterpod.filecreator.AndroidBuilders
import java.io.ByteArrayOutputStream

enum class ImgFormat(val label: String, val ext: String, val mime: String) {
    PNG("PNG", "png", "image/png"),
    JPEG("JPG / JPEG", "jpg", "image/jpeg"),
    WEBP("WebP", "webp", "image/webp"),
    BMP("BMP", "bmp", "image/bmp"),
    GIF("GIF", "gif", "image/gif"),
    PDF("PDF (one page)", "pdf", "application/pdf")
}

/** Image to image conversion using only Android's own decoders/encoders (plus the hand-written BMP/GIF writers). */
object ImageConverter {

    /** Decodes [uri], down-sampling so neither side exceeds [maxSide], and applies the EXIF rotation. */
    fun decode(ctx: Context, uri: Uri, maxSide: Int = 4096): Bitmap {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "That file is not an image this device can read" }
        var sample = 1
        while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IllegalArgumentException("That file is not an image this device can read")

        val orientation = try {
            cr.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        } catch (_: Exception) { null } ?: ExifInterface.ORIENTATION_NORMAL
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
        }
        if (!m.isIdentity) {
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (rotated !== bmp) { bmp.recycle(); bmp = rotated }
        }
        return bmp
    }

    /** Scales so the longest side is at most [maxSide] (0 = keep size). Never enlarges. */
    fun resize(src: Bitmap, maxSide: Int): Bitmap {
        if (maxSide <= 0) return src
        val longest = maxOf(src.width, src.height)
        if (longest <= maxSide) return src
        val f = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(src, maxOf(1, (src.width * f).toInt()), maxOf(1, (src.height * f).toInt()), true)
    }

    private fun flattenOnWhite(src: Bitmap): Bitmap {
        if (!src.hasAlpha()) return src
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)
        c.drawBitmap(src, 0f, 0f, null)
        return out
    }

    fun encode(src: Bitmap, format: ImgFormat, quality: Int): ByteArray = when (format) {
        ImgFormat.PNG -> AndroidBuilders.png(src)
        ImgFormat.WEBP -> if (android.os.Build.VERSION.SDK_INT >= 30 && quality >= 100) AndroidBuilders.webp(src) else {
            val bos = ByteArrayOutputStream()
            @Suppress("DEPRECATION")
            val fmt = if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            check(src.compress(fmt, quality, bos)) { "This device could not encode WebP" }
            bos.toByteArray()
        }
        ImgFormat.JPEG -> {
            val flat = flattenOnWhite(src)
            val bos = ByteArrayOutputStream()
            check(flat.compress(Bitmap.CompressFormat.JPEG, quality, bos)) { "This device could not encode JPEG" }
            if (flat !== src) flat.recycle()
            bos.toByteArray()
        }
        ImgFormat.BMP -> { val f = flattenOnWhite(src); val b = AndroidBuilders.bmp(f); if (f !== src) f.recycle(); b }
        ImgFormat.GIF -> { val f = flattenOnWhite(src); val b = AndroidBuilders.gif(f); if (f !== src) f.recycle(); b }
        ImgFormat.PDF -> {
            val scale = minOf(595f / src.width, 842f / src.height)
            val pw = maxOf(1, (src.width * scale).toInt()); val ph = maxOf(1, (src.height * scale).toInt())
            val doc = PdfDocument()
            try {
                val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, 1).create())
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawBitmap(src, null, Rect(0, 0, pw, ph), Paint(Paint.FILTER_BITMAP_FLAG))
                doc.finishPage(page)
                val bos = ByteArrayOutputStream()
                doc.writeTo(bos)
                bos.toByteArray()
            } finally {
                doc.close()
            }
        }
    }
}
