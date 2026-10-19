package com.karthikhegde.meterpod.filecreator

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

/** Builders that rely on built-in Android APIs (Bitmap, PdfDocument, SQLite, Base64). */
object AndroidBuilders {

    fun renderBitmap(width: Int, height: Int, background: Int, text: String, textColor: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(background)
        if (text.isNotBlank()) {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textSize = (minOf(width, height) / 8f).coerceAtLeast(8f)
                typeface = Typeface.DEFAULT_BOLD
            }
            val inner = (width * 0.9f).toInt().coerceAtLeast(1)
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, inner)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .build()
            canvas.save()
            canvas.translate((width - inner) / 2f, (height - layout.height) / 2f)
            layout.draw(canvas)
            canvas.restore()
        }
        return bmp
    }

    private fun compress(bmp: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray {
        val bos = ByteArrayOutputStream()
        check(bmp.compress(format, quality, bos)) { "This device could not encode the image" }
        return bos.toByteArray()
    }

    fun png(bmp: Bitmap): ByteArray = compress(bmp, Bitmap.CompressFormat.PNG, 100)

    fun jpeg(bmp: Bitmap): ByteArray = compress(bmp, Bitmap.CompressFormat.JPEG, 92)

    @Suppress("DEPRECATION")
    fun webp(bmp: Bitmap): ByteArray =
        if (Build.VERSION.SDK_INT >= 30) compress(bmp, Bitmap.CompressFormat.WEBP_LOSSLESS, 100)
        else compress(bmp, Bitmap.CompressFormat.WEBP, 95)

    fun bmp(bmp: Bitmap): ByteArray {
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return FileBuilders.bmp24(bmp.width, bmp.height, px)
    }

    fun gif(bmp: Bitmap): ByteArray {
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return FileBuilders.gif(bmp.width, bmp.height, px)
    }

    fun base64(content: String): ByteArray =
        Base64.encode(content.toByteArray(Charsets.UTF_8), Base64.DEFAULT)

    /** A4 PDF with the text word-wrapped and paginated, using the chosen font, size and per-line size markers. */
    fun pdf(content: String, style: DocStyle = DocStyle()): ByteArray {
        val pageW = 595; val pageH = 842; val margin = 50f
        val maxW = pageW - 2 * margin

        class Row(val text: String, val size: Float, val bold: Boolean)
        val rows = ArrayList<Row>()
        for (raw in content.replace("\r\n", "\n").split('\n')) {
            val sl = parseStyledLine(raw, style.size)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sl.size; typeface = style.typeface(sl.bold) }
            if (sl.text.isEmpty()) { rows.add(Row("", sl.size, sl.bold)); continue }
            var line = StringBuilder()
            for (word in sl.text.split(' ')) {
                var w = word
                while (paint.measureText(w) > maxW) {          // break very long words
                    val n = paint.breakText(w, true, maxW, null).coerceAtLeast(1)
                    if (line.isNotEmpty()) { rows.add(Row(line.toString(), sl.size, sl.bold)); line = StringBuilder() }
                    rows.add(Row(w.substring(0, n), sl.size, sl.bold)); w = w.substring(n)
                }
                val candidate = if (line.isEmpty()) w else "$line $w"
                if (paint.measureText(candidate) <= maxW) line = StringBuilder(candidate)
                else { rows.add(Row(line.toString(), sl.size, sl.bold)); line = StringBuilder(w) }
            }
            rows.add(Row(line.toString(), sl.size, sl.bold))
        }

        val doc = PdfDocument()
        try {
            var index = 0
            var pageNo = 1
            do {
                val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
                var y = margin
                var count = 0
                while (index < rows.size) {
                    val r = rows[index]
                    val h = r.size * 1.35f
                    if (count > 0 && y + h > pageH - margin) break
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = r.size; typeface = style.typeface(r.bold) }
                    page.canvas.drawText(r.text, margin, y + r.size, paint)
                    y += h; index++; count++
                }
                doc.finishPage(page)
                pageNo++
            } while (index < rows.size)
            val bos = ByteArrayOutputStream()
            doc.writeTo(bos)
            return bos.toByteArray()
        } finally {
            doc.close()
        }
    }

    /** Splits SQL on ';' while ignoring semicolons inside quotes. */
    private fun splitSql(sql: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var quote = 0.toChar()
        for (ch in sql) {
            if (quote != 0.toChar()) {
                sb.append(ch); if (ch == quote) quote = 0.toChar()
            } else if (ch == '\'' || ch == '"') {
                quote = ch; sb.append(ch)
            } else if (ch == ';') {
                if (sb.isNotBlank()) out.add(sb.toString().trim()); sb.setLength(0)
            } else sb.append(ch)
        }
        if (sb.isNotBlank()) out.add(sb.toString().trim())
        return out
    }

    /** Creates a real SQLite database file by running [sql], and returns its bytes. */
    fun sqlite(context: Context, sql: String): ByteArray {
        val file = File(context.cacheDir, "creator_${System.nanoTime()}.db")
        try {
            val db = SQLiteDatabase.openOrCreateDatabase(file, null)
            try {
                db.beginTransaction()
                try {
                    for (stmt in splitSql(sql)) db.execSQL(stmt)
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            } finally {
                db.close()
            }
            return file.readBytes()
        } finally {
            file.delete()
            File(file.path + "-journal").delete()
            File(file.path + "-wal").delete()
            File(file.path + "-shm").delete()
        }
    }
}
