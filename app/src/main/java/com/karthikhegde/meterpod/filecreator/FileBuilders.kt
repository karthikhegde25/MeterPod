package com.karthikhegde.meterpod.filecreator

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Pure-JVM writers for the binary / packaged file formats of the File Creator tool. They only use
 * java.* / kotlin.* (no Android classes, no external libraries) so they are easy to unit test.
 * Android-specific formats (PNG/JPEG/WebP, PDF, SQLite, Base64) live in AndroidBuilders.kt.
 */
object FileBuilders {

    // ------------------------------------------------------------------ text-ish

    fun text(content: String): ByteArray = content.toByteArray(Charsets.UTF_8)

    /** Wraps plain text into a minimal valid RTF document (escapes \ { } and non-ASCII as \uN?). */
    fun rtf(content: String): ByteArray {
        val sb = StringBuilder("{\\rtf1\\ansi\\ansicpg1252\\deff0{\\fonttbl{\\f0\\fswiss Arial;}}\\f0\\fs24 ")
        for (ch in content) {
            when {
                ch == '\\' -> sb.append("\\\\")
                ch == '{' -> sb.append("\\{")
                ch == '}' -> sb.append("\\}")
                ch == '\n' -> sb.append("\\par\n")
                ch == '\r' -> Unit
                ch == '\t' -> sb.append("\\tab ")
                ch.code in 32..126 -> sb.append(ch)
                ch.code < 32 -> Unit
                else -> sb.append("\\u").append(ch.code.toShort().toInt()).append('?')
            }
        }
        sb.append("\\par\n}")
        return sb.toString().toByteArray(Charsets.US_ASCII)
    }

    /** Parses a hex string ("DE AD be-ef", "0xDEADBEEF", ...) into bytes. Throws IllegalArgumentException if invalid. */
    fun hexToBytes(input: String): ByteArray {
        val cleaned = input.replace(Regex("0[xX]"), "").filter { !it.isWhitespace() && it != ',' && it != '-' && it != ':' }
        require(cleaned.isNotEmpty()) { "Enter some hex digits (for example: 48 65 6C 6C 6F)" }
        require(cleaned.length % 2 == 0) { "Hex input needs an even number of digits" }
        val out = ByteArray(cleaned.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(cleaned[i * 2], 16)
            val lo = Character.digit(cleaned[i * 2 + 1], 16)
            require(hi >= 0 && lo >= 0) { "'${cleaned[i * 2]}${cleaned[i * 2 + 1]}' is not valid hex" }
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    // ------------------------------------------------------------------ archives

    fun zip(entryName: String, data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            zos.putNextEntry(ZipEntry(entryName))
            zos.write(data)
            zos.closeEntry()
        }
        return bos.toByteArray()
    }

    fun gzip(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    /** A POSIX ustar archive holding one file. */
    fun tar(entryName: String, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val header = ByteArray(512)

        fun put(offset: Int, value: String, maxLen: Int) {
            val b = value.toByteArray(Charsets.UTF_8)
            System.arraycopy(b, 0, header, offset, min(b.size, maxLen))
        }
        fun octal(value: Long, digits: Int): String = value.toString(8).padStart(digits, '0')

        put(0, entryName, 100)
        put(100, octal(420, 7) + "\u0000", 8)            // mode 0644
        put(108, octal(0, 7) + "\u0000", 8)              // uid
        put(116, octal(0, 7) + "\u0000", 8)              // gid
        put(124, octal(data.size.toLong(), 11) + "\u0000", 12)
        put(136, octal(System.currentTimeMillis() / 1000, 11) + "\u0000", 12)
        for (i in 148 until 156) header[i] = ' '.code.toByte()   // checksum placeholder
        header[156] = '0'.code.toByte()                  // regular file
        put(257, "ustar\u0000", 6)
        put(263, "00", 2)

        var sum = 0
        for (b in header) sum += b.toInt() and 0xFF
        put(148, octal(sum.toLong(), 6) + "\u0000 ", 8)

        out.write(header)
        out.write(data)
        val pad = (512 - data.size % 512) % 512
        out.write(ByteArray(pad))
        out.write(ByteArray(1024))                       // end-of-archive marker
        return out.toByteArray()
    }

    // ------------------------------------------------------------------ audio

    /** 16-bit mono PCM sine tone as a canonical 44-byte-header WAV. */
    fun wav(frequencyHz: Double, seconds: Double, sampleRate: Int = 44100): ByteArray {
        val samples = (seconds * sampleRate).toInt()
        val dataSize = samples * 2
        val bb = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray(Charsets.US_ASCII))
        bb.putInt(36 + dataSize)
        bb.put("WAVE".toByteArray(Charsets.US_ASCII))
        bb.put("fmt ".toByteArray(Charsets.US_ASCII))
        bb.putInt(16)                 // PCM chunk size
        bb.putShort(1)                // PCM
        bb.putShort(1)                // mono
        bb.putInt(sampleRate)
        bb.putInt(sampleRate * 2)     // byte rate
        bb.putShort(2)                // block align
        bb.putShort(16)               // bits per sample
        bb.put("data".toByteArray(Charsets.US_ASCII))
        bb.putInt(dataSize)
        val fade = min(sampleRate / 100, samples / 2).coerceAtLeast(1)   // 10 ms fade in/out, avoids clicks
        for (i in 0 until samples) {
            var amp = 0.8 * sin(2.0 * PI * frequencyHz * i / sampleRate)
            if (i < fade) amp *= i.toDouble() / fade
            if (samples - 1 - i < fade) amp *= (samples - 1 - i).toDouble() / fade
            bb.putShort((amp * Short.MAX_VALUE).toInt().toShort())
        }
        return bb.array()
    }

    // ------------------------------------------------------------------ images

    /** 24-bit uncompressed BMP from ARGB pixels (alpha ignored). */
    fun bmp24(width: Int, height: Int, argb: IntArray): ByteArray {
        val rowSize = (width * 3 + 3) / 4 * 4
        val imageSize = rowSize * height
        val bb = ByteBuffer.allocate(54 + imageSize).order(ByteOrder.LITTLE_ENDIAN)
        bb.put('B'.code.toByte()).put('M'.code.toByte())
        bb.putInt(54 + imageSize)
        bb.putInt(0)
        bb.putInt(54)                 // pixel data offset
        bb.putInt(40)                 // BITMAPINFOHEADER
        bb.putInt(width)
        bb.putInt(height)             // positive = bottom-up rows
        bb.putShort(1)
        bb.putShort(24)
        bb.putInt(0)                  // BI_RGB
        bb.putInt(imageSize)
        bb.putInt(2835)               // ~72 dpi
        bb.putInt(2835)
        bb.putInt(0)
        bb.putInt(0)
        val padding = rowSize - width * 3
        for (y in height - 1 downTo 0) {
            for (x in 0 until width) {
                val p = argb[y * width + x]
                bb.put((p and 0xFF).toByte())             // B
                bb.put(((p shr 8) and 0xFF).toByte())     // G
                bb.put(((p shr 16) and 0xFF).toByte())    // R
            }
            for (i in 0 until padding) bb.put(0)
        }
        return bb.array()
    }

    /**
     * Single-frame GIF89a. Colours are quantised to a fixed 256-entry RGB 3-3-2 palette, then
     * LZW-compressed as the format requires.
     */
    fun gif(width: Int, height: Int, argb: IntArray): ByteArray {
        val out = ByteArrayOutputStream()
        fun u16(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }

        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        u16(width); u16(height)
        out.write(0xF7)               // global colour table, 8 bits/colour, 256 entries
        out.write(0)                  // background colour index
        out.write(0)                  // pixel aspect ratio
        for (i in 0 until 256) {      // palette: index = RRRGGGBB
            out.write(((i shr 5) and 7) * 255 / 7)
            out.write(((i shr 2) and 7) * 255 / 7)
            out.write((i and 3) * 255 / 3)
        }
        out.write(0x2C)               // image descriptor
        u16(0); u16(0); u16(width); u16(height)
        out.write(0)

        val indices = ByteArray(width * height)
        for (i in indices.indices) {
            val p = argb[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            indices[i] = (((r shr 5) shl 5) or ((g shr 5) shl 2) or (b shr 6)).toByte()
        }

        out.write(8)                  // LZW minimum code size
        val lzw = lzwEncode(indices, 8)
        var pos = 0
        while (pos < lzw.size) {      // data sub-blocks of at most 255 bytes
            val n = min(255, lzw.size - pos)
            out.write(n)
            out.write(lzw, pos, n)
            pos += n
        }
        out.write(0)                  // block terminator
        out.write(0x3B)               // trailer
        return out.toByteArray()
    }

    private fun lzwEncode(indices: ByteArray, minCodeSize: Int): ByteArray {
        val clearCode = 1 shl minCodeSize
        val endCode = clearCode + 1
        val out = ByteArrayOutputStream()
        var bitBuffer = 0
        var bitCount = 0

        fun emit(code: Int, size: Int) {
            bitBuffer = bitBuffer or (code shl bitCount)
            bitCount += size
            while (bitCount >= 8) {
                out.write(bitBuffer and 0xFF)
                bitBuffer = bitBuffer ushr 8
                bitCount -= 8
            }
        }

        var codeSize = minCodeSize + 1
        var nextCode = endCode + 1
        val dictionary = HashMap<Int, Int>()
        emit(clearCode, codeSize)

        if (indices.isNotEmpty()) {
            var prefix = indices[0].toInt() and 0xFF
            for (i in 1 until indices.size) {
                val k = indices[i].toInt() and 0xFF
                val key = (prefix shl 8) or k
                val found = dictionary[key]
                if (found != null) {
                    prefix = found
                } else {
                    emit(prefix, codeSize)
                    if (nextCode >= (1 shl codeSize) && codeSize < 12) codeSize++
                    if (nextCode < 4096) {
                        dictionary[key] = nextCode++
                    } else {
                        emit(clearCode, codeSize)
                        dictionary.clear()
                        codeSize = minCodeSize + 1
                        nextCode = endCode + 1
                    }
                    prefix = k
                }
            }
            emit(prefix, codeSize)
        }
        emit(endCode, codeSize)
        if (bitCount > 0) out.write(bitBuffer and 0xFF)
        return out.toByteArray()
    }

    // ------------------------------------------------------------------ Office Open XML

    private fun xmlEscape(s: String): String {
        val sb = StringBuilder(s.length + 16)
        for (ch in s) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> if (ch.code >= 32 || ch == '\t' || ch == '\n' || ch == '\r') sb.append(ch)
            }
        }
        return sb.toString()
    }

    private const val XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"

    private fun zipParts(parts: List<Pair<String, String>>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            for ((name, body) in parts) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(body.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    /** A minimal Word document: each line of [content] becomes one paragraph. */
    fun docx(content: String, style: DocStyle = DocStyle()): ByteArray {
        val body = StringBuilder()
        val fn = xmlEscape(style.font.wordName)
        for (raw in content.replace("\r\n", "\n").split('\n')) {
            val sl = parseStyledLine(raw, style.size)
            if (sl.text.isEmpty()) {
                body.append("<w:p/>")
            } else {
                val half = Math.round(sl.size * 2)
                val rpr = "<w:rPr><w:rFonts w:ascii=\"$fn\" w:hAnsi=\"$fn\" w:cs=\"$fn\"/>" +
                    (if (style.bold || sl.bold) "<w:b/>" else "") + (if (style.italic) "<w:i/>" else "") +
                    "<w:sz w:val=\"$half\"/><w:szCs w:val=\"$half\"/></w:rPr>"
                body.append("<w:p><w:r>").append(rpr).append("<w:t xml:space=\"preserve\">").append(xmlEscape(sl.text)).append("</w:t></w:r></w:p>")
            }
        }
        val contentTypes = XML_HEADER +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
            "</Types>"
        val rels = XML_HEADER +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
            "</Relationships>"
        val document = XML_HEADER +
            "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>" +
            body + "<w:sectPr/></w:body></w:document>"
        return zipParts(listOf("[Content_Types].xml" to contentTypes, "_rels/.rels" to rels, "word/document.xml" to document))
    }

    /** Parses CSV text (quotes, escaped quotes, commas/newlines inside quotes). */
    fun parseCsv(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        val s = text.replace("\r\n", "\n").replace('\r', '\n')
        var fieldStarted = false
        while (i < s.length) {
            val c = s[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < s.length && s[i + 1] == '"') { field.append('"'); i++ } else inQuotes = false
                } else field.append(c)
            } else {
                when (c) {
                    '"' -> { inQuotes = true; fieldStarted = true }
                    ',' -> { row.add(field.toString()); field.setLength(0); fieldStarted = false }
                    '\n' -> {
                        row.add(field.toString()); field.setLength(0); fieldStarted = false
                        rows.add(row); row = ArrayList()
                    }
                    else -> { field.append(c); fieldStarted = true }
                }
            }
            i++
        }
        if (field.isNotEmpty() || fieldStarted || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows
    }

    private fun columnName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (n >= 0) {
            sb.append('A' + n % 26)
            n = n / 26 - 1
        }
        return sb.reverse().toString()
    }

    private val numberPattern = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?")

    /** A minimal Excel workbook with one sheet filled from CSV text (numbers stay numeric, the rest is text). */
    fun xlsx(csv: String): ByteArray {
        val rows = parseCsv(csv)
        val sheet = StringBuilder()
        sheet.append(XML_HEADER)
        sheet.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
        rows.forEachIndexed { r, cells ->
            sheet.append("<row r=\"").append(r + 1).append("\">")
            cells.forEachIndexed { c, value ->
                if (value.isEmpty()) return@forEachIndexed
                val ref = columnName(c) + (r + 1)
                if (numberPattern.matches(value)) {
                    sheet.append("<c r=\"").append(ref).append("\"><v>").append(value).append("</v></c>")
                } else {
                    sheet.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        .append(xmlEscape(value)).append("</t></is></c>")
                }
            }
            sheet.append("</row>")
        }
        sheet.append("</sheetData></worksheet>")

        val contentTypes = XML_HEADER +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
            "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
            "</Types>"
        val rels = XML_HEADER +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
            "</Relationships>"
        val workbook = XML_HEADER +
            "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
            "<sheets><sheet name=\"Sheet1\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>"
        val workbookRels = XML_HEADER +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
            "</Relationships>"
        return zipParts(
            listOf(
                "[Content_Types].xml" to contentTypes,
                "_rels/.rels" to rels,
                "xl/workbook.xml" to workbook,
                "xl/_rels/workbook.xml.rels" to workbookRels,
                "xl/worksheets/sheet1.xml" to sheet.toString()
            )
        )
    }
}
