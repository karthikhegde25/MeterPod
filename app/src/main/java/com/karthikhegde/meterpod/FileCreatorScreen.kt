package com.karthikhegde.meterpod

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color as AColor
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karthikhegde.meterpod.compose.DropdownField
import com.karthikhegde.meterpod.compose.FieldLabel
import com.karthikhegde.meterpod.compose.MP
import com.karthikhegde.meterpod.compose.MeterTextField
import com.karthikhegde.meterpod.compose.RealButton
import com.karthikhegde.meterpod.filecreator.AndroidBuilders
import com.karthikhegde.meterpod.filecreator.DocFonts
import com.karthikhegde.meterpod.filecreator.DocStyle
import com.karthikhegde.meterpod.filecreator.FileBuilders
import com.karthikhegde.meterpod.filecreator.FileFormat
import com.karthikhegde.meterpod.filecreator.FileFormats
import com.karthikhegde.meterpod.filecreator.Kind

private const val MAX_SIDE = 2048

/** Builds the bytes for [format] from the user's inputs. Throws IllegalArgumentException with a friendly message on bad input. */
private fun buildFile(
    ctx: Context, format: FileFormat, content: String, innerName: String,
    width: String, height: String, bgHex: String, fgHex: String, freq: String, seconds: String,
    doc: DocStyle = DocStyle()
): ByteArray {
    fun color(hex: String, what: String): Int = try {
        AColor.parseColor(if (hex.startsWith("#")) hex else "#$hex")
    } catch (e: Exception) {
        throw IllegalArgumentException("$what must be a hex colour like #4C8DFF")
    }
    return when (format.kind) {
        Kind.TEXT -> FileBuilders.text(content)
        Kind.RTF -> FileBuilders.rtf(content)
        Kind.PNG, Kind.JPEG, Kind.WEBP, Kind.BMP, Kind.GIF -> {
            val w = width.toIntOrNull(); val h = height.toIntOrNull()
            require(w != null && h != null && w in 1..MAX_SIDE && h in 1..MAX_SIDE) { "Width and height must be 1–$MAX_SIDE" }
            val bmp = AndroidBuilders.renderBitmap(w, h, color(bgHex, "Background"), content, color(fgHex, "Text colour"))
            try {
                when (format.kind) {
                    Kind.PNG -> AndroidBuilders.png(bmp)
                    Kind.JPEG -> AndroidBuilders.jpeg(bmp)
                    Kind.WEBP -> AndroidBuilders.webp(bmp)
                    Kind.BMP -> AndroidBuilders.bmp(bmp)
                    else -> AndroidBuilders.gif(bmp)
                }
            } finally {
                bmp.recycle()
            }
        }
        Kind.WAV -> {
            val f = freq.toDoubleOrNull(); val s = seconds.toDoubleOrNull()
            require(f != null && f in 20.0..20000.0) { "Frequency must be 20–20000 Hz" }
            require(s != null && s in 0.1..60.0) { "Duration must be 0.1–60 seconds" }
            FileBuilders.wav(f, s)
        }
        Kind.ZIP -> FileBuilders.zip(innerName.ifBlank { "file.txt" }, content.toByteArray(Charsets.UTF_8))
        Kind.TAR -> FileBuilders.tar(innerName.ifBlank { "file.txt" }, content.toByteArray(Charsets.UTF_8))
        Kind.GZIP -> FileBuilders.gzip(content.toByteArray(Charsets.UTF_8))
        Kind.BASE64 -> AndroidBuilders.base64(content)
        Kind.SQLITE -> try {
            AndroidBuilders.sqlite(ctx, content)
        } catch (e: android.database.SQLException) {
            throw IllegalArgumentException("SQL error: ${e.message}")
        }
        Kind.BIN -> FileBuilders.hexToBytes(content)
        Kind.PDF -> AndroidBuilders.pdf(content, doc)
        Kind.DOCX -> FileBuilders.docx(content, doc)
        Kind.XLSX -> FileBuilders.xlsx(content)
    }
}

private fun contentHint(kind: Kind): String = when (kind) {
    Kind.BIN -> "Hex bytes, e.g. 48 65 6C 6C 6F"
    Kind.SQLITE -> "SQL statements, separated by ;"
    Kind.XLSX -> "CSV rows (first row = header)"
    Kind.ZIP, Kind.TAR -> "Content of the file inside the archive"
    Kind.PNG, Kind.JPEG, Kind.WEBP, Kind.BMP, Kind.GIF -> "Optional text drawn in the middle of the image"
    else -> "File content"
}

@Composable
fun FileCreatorScreen() {
    val ctx = LocalContext.current
    val formats = FileFormats.all
    var index by remember { mutableStateOf(0) }
    val format = formats[index]

    var fileName by remember { mutableStateOf("new_file") }
    var content by remember { mutableStateOf(format.template) }
    var innerName by remember { mutableStateOf("hello.txt") }
    var width by remember { mutableStateOf("512") }
    var height by remember { mutableStateOf("512") }
    var bgHex by remember { mutableStateOf("#4C8DFF") }
    var fgHex by remember { mutableStateOf("#FFFFFF") }
    var freq by remember { mutableStateOf("440") }
    var seconds by remember { mutableStateOf("2") }
    var docFont by remember { mutableStateOf(0) }
    var docSize by remember { mutableStateOf(12) }
    var docBold by remember { mutableStateOf(false) }
    var docItalic by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val pending = remember { arrayOfNulls<ByteArray>(1) }
    val handler = remember { Handler(Looper.getMainLooper()) }

    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uri = r.data?.data
        val bytes = pending[0]
        pending[0] = null
        if (r.resultCode == Activity.RESULT_OK && uri != null && bytes != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                status = "Saved ${bytes.size} bytes"
                toast("Saved")
            } catch (e: Exception) {
                toast("Could not save: ${e.message}")
            }
        }
    }

    fun create() {
        if (busy) return
        val fmt = format
        val outName = run {
            val base = fileName.trim().ifEmpty { "new_file" }
            val suffix = "." + fmt.ext
            if (base.endsWith(suffix, ignoreCase = true)) base else base + suffix
        }
        val snapshot = listOf(content, innerName, width, height, bgHex, fgHex, freq, seconds)
        val docStyle = DocStyle(docFont, docSize, docBold, docItalic)
        busy = true
        Thread {
            var bytes: ByteArray? = null
            var error: String? = null
            try {
                bytes = buildFile(
                    ctx.applicationContext, fmt, snapshot[0], snapshot[1], snapshot[2], snapshot[3],
                    snapshot[4], snapshot[5], snapshot[6], snapshot[7], docStyle
                )
            } catch (e: IllegalArgumentException) {
                error = e.message
            } catch (e: Throwable) {
                error = "Could not create file: ${e.message}"
            }
            handler.post {
                busy = false
                if (bytes == null) {
                    toast(error ?: "Could not create file")
                } else {
                    pending[0] = bytes
                    status = "$outName · ${bytes.size} bytes"
                    saver.launch(
                        Intent(Intent.ACTION_CREATE_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType(fmt.mime)
                            .putExtra(Intent.EXTRA_TITLE, outName)
                    )
                }
            }
        }.start()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MP.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        FieldLabel("File type", Modifier.padding(top = 0.dp))
        DropdownField(
            options = formats.map { "${it.label}  (.${it.ext})" },
            selectedIndex = index,
            onSelected = { i ->
                val old = format
                index = i
                // Replace the editor text only if the user hasn't changed the starter template.
                if (content == old.template || content.isBlank()) content = formats[i].template
            },
            modifier = Modifier.fillMaxWidth()
        )

        FieldLabel("File name (.${format.ext} is added automatically)")
        MeterTextField(
            value = fileName, onValueChange = { fileName = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            contentPadding = PaddingValues(14.dp)
        )

        if (format.kind == Kind.ZIP || format.kind == Kind.TAR) {
            FieldLabel("Name of the file inside the archive")
            MeterTextField(
                value = innerName, onValueChange = { innerName = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                contentPadding = PaddingValues(14.dp)
            )
        }

        if (format.isImage) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    FieldLabel("Width (px)")
                    MeterTextField(
                        value = width, onValueChange = { width = it.filter(Char::isDigit).take(4) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        contentPadding = PaddingValues(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel("Height (px)")
                    MeterTextField(
                        value = height, onValueChange = { height = it.filter(Char::isDigit).take(4) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        contentPadding = PaddingValues(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    FieldLabel("Background colour")
                    MeterTextField(
                        value = bgHex, onValueChange = { bgHex = it.take(9) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        contentPadding = PaddingValues(14.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel("Text colour")
                    MeterTextField(
                        value = fgHex, onValueChange = { fgHex = it.take(9) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        contentPadding = PaddingValues(14.dp)
                    )
                }
            }
        }

        if (format.kind == Kind.PDF || format.kind == Kind.DOCX) {
            FieldLabel("Font")
            DropdownField(
                options = DocFonts.all.map { it.label },
                selectedIndex = docFont,
                onSelected = { docFont = it },
                modifier = Modifier.fillMaxWidth()
            )
            FieldLabel("Letter size (pt)")
            DropdownField(
                options = DocFonts.sizes.map { "$it pt" },
                selectedIndex = DocFonts.sizes.indexOf(docSize).coerceAtLeast(0),
                onSelected = { docSize = DocFonts.sizes[it] },
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RealButton(if (docBold) "Bold: on" else "Bold: off", onClick = { docBold = !docBold }, compact = true, primary = docBold)
                RealButton(if (docItalic) "Italic: on" else "Italic: off", onClick = { docItalic = !docItalic }, compact = true, primary = docItalic)
            }
            BasicText(
                "Different sizes in one file: start a line with {28} to make that line 28 pt, or use #, ## or ### for headings (2x, 1.6x, 1.3x). Word files use the real font names; if a font is missing on the reader's computer, Word substitutes a similar one.",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                style = TextStyle(color = MP.TextSecondary, fontSize = 11.sp)
            )
        }

        if (format.kind == Kind.WAV) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    FieldLabel("Frequency (Hz)")
                    MeterTextField(
                        value = freq, onValueChange = { freq = it.filter { c -> c.isDigit() || c == '.' }.take(8) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        contentPadding = PaddingValues(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel("Duration (seconds)")
                    MeterTextField(
                        value = seconds, onValueChange = { seconds = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        contentPadding = PaddingValues(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            }
        } else {
            FieldLabel(contentHint(format.kind))
            MeterTextField(
                value = content, onValueChange = { content = it },
                modifier = Modifier.fillMaxWidth(), hint = contentHint(format.kind),
                textSize = 14f, fontFamily = FontFamily.Monospace,
                minLines = 8, maxLines = 14,
                contentPadding = PaddingValues(14.dp)
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 18.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            RealButton(
                text = if (busy) "Creating…" else "Create & Save",
                onClick = { create() },
                primary = true,
                enabled = !busy
            )
        }
        if (status.isNotEmpty()) {
            BasicText(
                text = status,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                style = TextStyle(color = MP.TextSecondary, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            )
        }
        BasicText(
            text = "MP3 and MP4 are not offered: they need an audio/video encoder, which Android does not include.",
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            style = TextStyle(color = MP.TextSecondary, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        )
    }
}
