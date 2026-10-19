package com.karthikhegde.meterpod.uuid

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Turns a list of UUIDs into TXT, Markdown or PDF bytes for saving. */
object UuidExport {
    fun txt(ids: List<String>): ByteArray = ids.joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8)

    fun md(ids: List<String>, label: String): ByteArray {
        val sb = StringBuilder()
        sb.append("# UUIDs\n\n")
        sb.append("- Version: ").append(label).append('\n')
        sb.append("- Count: ").append(ids.size).append('\n')
        sb.append("- Generated: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())).append("\n\n")
        sb.append("```\n")
        for (u in ids) sb.append(u).append('\n')
        sb.append("```\n")
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    /** A4 pages, three columns of monospace ids with numbering, a title on the first page. */
    fun pdf(ids: List<String>, label: String): ByteArray {
        val pageW = 595; val pageH = 842; val margin = 36f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; textSize = 6.4f; color = 0xFF111111.toInt() }
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 15f; isFakeBoldText = true; color = 0xFF111111.toInt() }
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = 0xFF555555.toInt() }
        val lineH = 9f
        val cols = if (ids.firstOrNull()?.length ?: 36 > 32) 3 else 4
        val colW = (pageW - 2 * margin) / cols
        val doc = PdfDocument()
        try {
            var i = 0; var pageNo = 1
            do {
                val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
                val c = page.canvas
                var top = margin + 8f
                if (pageNo == 1) {
                    c.drawText("UUIDs ($label)", margin, top + 8f, title)
                    c.drawText("${ids.size} ids - " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()), margin, top + 22f, sub)
                    top += 34f
                }
                val rows = ((pageH - margin - top) / lineH).toInt()
                for (col in 0 until cols) {
                    for (r in 0 until rows) {
                        if (i >= ids.size) break
                        c.drawText(ids[i], margin + col * colW, top + (r + 1) * lineH, paint)
                        i++
                    }
                }
                doc.finishPage(page)
                pageNo++
            } while (i < ids.size)
            val bos = ByteArrayOutputStream()
            doc.writeTo(bos)
            return bos.toByteArray()
        } finally { doc.close() }
    }
}
