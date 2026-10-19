package com.karthikhegde.meterpod

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karthikhegde.meterpod.compose.DropdownField
import com.karthikhegde.meterpod.compose.FieldLabel
import com.karthikhegde.meterpod.compose.MP
import com.karthikhegde.meterpod.compose.MeterTextField
import com.karthikhegde.meterpod.compose.RealButton
import com.karthikhegde.meterpod.fileconverter.DataConverter
import com.karthikhegde.meterpod.fileconverter.DataFormat
import com.karthikhegde.meterpod.fileconverter.ImageConverter
import com.karthikhegde.meterpod.fileconverter.ImgFormat

private const val MAX_TEXT_BYTES = 5 * 1024 * 1024
private const val PREVIEW_CHARS = 30000

private fun displayName(ctx: Context, uri: Uri): String {
    try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0) ?: ""
        }
    } catch (_: Exception) { }
    return uri.lastPathSegment?.substringAfterLast('/') ?: ""
}

private fun baseName(name: String, fallback: String): String =
    name.substringBeforeLast('.', name).ifBlank { fallback }

/** Runs [work] on a background thread and hands the result (or error text) to [done] on the main thread. */
private fun <T> runBg(handler: Handler, work: () -> T, done: (T?, String?) -> Unit) {
    Thread {
        var value: T? = null
        var error: String? = null
        try {
            value = work()
        } catch (e: IllegalArgumentException) {
            error = e.message
        } catch (e: OutOfMemoryError) {
            error = "Not enough memory for this file"
        } catch (e: Throwable) {
            error = "Conversion failed: ${e.message}"
        }
        handler.post { done(value, error) }
    }.start()
}

@Composable
fun FileConverterScreen() {
    val ctx = LocalContext.current
    val handler = remember { Handler(Looper.getMainLooper()) }
    var imageMode by remember { mutableStateOf(false) }
    val pending = remember { arrayOfNulls<ByteArray>(1) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()

    // ---------------- data / text state
    val readFormats = remember { DataFormat.values().filter { it.canRead } }
    val writeFormats = remember { DataFormat.values().filter { it.canWrite } }
    var fromIndex by remember { mutableStateOf(0) }            // 0 = auto-detect, then readFormats
    var toIndex by remember { mutableStateOf(writeFormats.indexOf(DataFormat.YAML).coerceAtLeast(0)) }
    var input by remember { mutableStateOf("") }
    var inputName by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    val fullOutput = remember { arrayOfNulls<String>(1) }
    val outFormat = remember { arrayOfNulls<DataFormat>(1) }

    // ---------------- image state
    val imgFormats = remember { ImgFormat.values().toList() }
    var imgTo by remember { mutableStateOf(0) }
    var quality by remember { mutableStateOf("90") }
    var maxSide by remember { mutableStateOf("") }
    var imgName by remember { mutableStateOf("") }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imgInfo by remember { mutableStateOf("") }

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

    fun launchSave(bytes: ByteArray, name: String, mime: String) {
        pending[0] = bytes
        saver.launch(
            Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(mime)
                .putExtra(Intent.EXTRA_TITLE, name)
        )
    }

    val textPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uri = r.data?.data
        if (r.resultCode != Activity.RESULT_OK || uri == null) return@rememberLauncherForActivityResult
        busy = true
        runBg(handler, {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalArgumentException("Could not open that file")
            require(bytes.size <= MAX_TEXT_BYTES) { "File is larger than 5 MB" }
            val text = String(bytes, Charsets.UTF_8)
            require(!text.contains('\u0000')) { "That looks like a binary file, not text" }
            Pair(displayName(ctx, uri), text)
        }) { v, err ->
            busy = false
            if (v == null) toast(err ?: "Could not open file") else {
                inputName = v.first
                input = v.second
                output = ""
                fullOutput[0] = null
                val det = DataConverter.detect(v.second, v.first)
                fromIndex = readFormats.indexOf(det).let { if (it >= 0) it + 1 else 0 }
                status = "Opened ${v.first} (${v.second.length} characters, looks like ${det.label})"
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uri = r.data?.data
        if (r.resultCode != Activity.RESULT_OK || uri == null) return@rememberLauncherForActivityResult
        busy = true
        runBg(handler, {
            val bmp = ImageConverter.decode(ctx, uri)
            Pair(displayName(ctx, uri), bmp)
        }) { v, err ->
            busy = false
            if (v == null) toast(err ?: "Could not open image") else {
                bitmap = v.second
                imgName = v.first
                imgInfo = "${v.first} · ${v.second.width} × ${v.second.height} px"
                status = ""
            }
        }
    }

    fun convertText() {
        if (busy) return
        val text = input
        val from = if (fromIndex == 0) null else readFormats[fromIndex - 1]
        val to = writeFormats[toIndex]
        val name = inputName
        busy = true
        runBg(handler, {
            val src = from ?: DataConverter.detect(text, name)
            Triple(src, DataConverter.convert(text, src, to, name), to)
        }) { v, err ->
            busy = false
            if (v == null) {
                toast(err ?: "Conversion failed")
                status = err ?: ""
            } else {
                fullOutput[0] = v.second
                outFormat[0] = v.third
                output = if (v.second.length > PREVIEW_CHARS) v.second.take(PREVIEW_CHARS) + "\n… (preview cut, Save writes the whole result)" else v.second
                status = "${v.first.label} → ${v.third.label} · ${v.second.length} characters"
            }
        }
    }

    fun convertImage() {
        if (busy) return
        val bmp = bitmap
        if (bmp == null) { toast("Choose an image first"); return }
        val q = quality.toIntOrNull()
        if (q == null || q !in 1..100) { toast("Quality must be 1–100"); return }
        val ms = if (maxSide.isBlank()) 0 else (maxSide.toIntOrNull() ?: -1)
        if (ms < 0 || (ms in 1..15)) { toast("Max size must be empty or at least 16 px"); return }
        val fmt = imgFormats[imgTo]
        val outName = baseName(imgName, "converted") + "." + fmt.ext
        busy = true
        runBg(handler, {
            val scaled = ImageConverter.resize(bmp, ms)
            try {
                ImageConverter.encode(scaled, fmt, q)
            } finally {
                if (scaled !== bmp) scaled.recycle()
            }
        }) { bytes, err ->
            busy = false
            if (bytes == null) toast(err ?: "Conversion failed") else {
                status = "$outName · ${bytes.size} bytes"
                launchSave(bytes, outName, fmt.mime)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MP.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, androidx.compose.ui.Alignment.CenterHorizontally)) {
            RealButton("Text & Data", onClick = { imageMode = false; status = "" }, primary = !imageMode, compact = true)
            RealButton("Image", onClick = { imageMode = true; status = "" }, primary = imageMode, compact = true)
        }

        if (!imageMode) {
            FieldLabel("Convert from")
            DropdownField(
                options = listOf("Auto-detect") + readFormats.map { it.label },
                selectedIndex = fromIndex, onSelected = { fromIndex = it }, modifier = Modifier.fillMaxWidth()
            )
            FieldLabel("Convert to")
            DropdownField(
                options = writeFormats.map { "${it.label}  (.${it.ext})" },
                selectedIndex = toIndex, onSelected = { toIndex = it }, modifier = Modifier.fillMaxWidth()
            )
            FieldLabel("Input (type, paste or open a file)")
            MeterTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(), hint = "Paste JSON, CSV, XML, YAML, …",
                textSize = 13f, fontFamily = FontFamily.Monospace, minLines = 6, maxLines = 10,
                contentPadding = PaddingValues(14.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, androidx.compose.ui.Alignment.CenterHorizontally)
            ) {
                RealButton("Open file", onClick = {
                    textPicker.launch(Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"))
                }, compact = true, enabled = !busy)
                RealButton("Clear", onClick = { input = ""; output = ""; fullOutput[0] = null; inputName = ""; status = "" },
                    compact = true, textColor = MP.Needle)
                RealButton(if (busy) "Working…" else "Convert", onClick = { convertText() }, primary = true, compact = true, enabled = !busy)
            }
            if (output.isNotEmpty()) {
                FieldLabel("Result")
                MeterTextField(
                    value = output, onValueChange = { },
                    modifier = Modifier.fillMaxWidth(), readOnly = true,
                    textSize = 13f, fontFamily = FontFamily.Monospace, minLines = 6, maxLines = 14,
                    contentPadding = PaddingValues(14.dp)
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, androidx.compose.ui.Alignment.CenterHorizontally)
                ) {
                    RealButton("Copy", onClick = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("MeterPod", fullOutput[0] ?: output))
                        toast("Copied")
                    }, compact = true)
                    RealButton("Save", onClick = {
                        val full = fullOutput[0]
                        val f = outFormat[0]
                        if (full != null && f != null) {
                            launchSave(full.toByteArray(Charsets.UTF_8), baseName(inputName, "converted") + "." + f.ext, f.mime)
                        }
                    }, primary = true, compact = true)
                }
            }
            BasicText(
                text = "Reads JSON, CSV, TSV, XML, YAML, Properties, INI, Markdown/HTML tables and text. " +
                    "TOML and SQL can be created but not read. Tables are built from lists of records; nested values become dotted columns.",
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                style = TextStyle(color = MP.TextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
            )
        } else {
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                RealButton("Choose image", onClick = {
                    imagePicker.launch(Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"))
                }, compact = true, enabled = !busy)
            }
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Selected image",
                    modifier = Modifier.fillMaxWidth().height(200.dp).padding(top = 14.dp),
                    contentScale = ContentScale.Fit
                )
                BasicText(
                    text = imgInfo,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    style = TextStyle(color = MP.TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                )
            }
            FieldLabel("Convert to")
            DropdownField(
                options = imgFormats.map { "${it.label}  (.${it.ext})" },
                selectedIndex = imgTo, onSelected = { imgTo = it }, modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    FieldLabel("Quality (1–100, JPG/WebP)")
                    MeterTextField(
                        value = quality, onValueChange = { quality = it.filter(Char::isDigit).take(3) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, contentPadding = PaddingValues(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel("Max size px (optional)")
                    MeterTextField(
                        value = maxSide, onValueChange = { maxSide = it.filter(Char::isDigit).take(5) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, hint = "original",
                        contentPadding = PaddingValues(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.Center) {
                RealButton(if (busy) "Working…" else "Convert & Save", onClick = { convertImage() }, primary = true, enabled = !busy)
            }
            BasicText(
                text = "Opens PNG, JPG, WebP, GIF (first frame), BMP and any other image your phone can decode. " +
                    "Transparent areas become white for JPG, BMP and GIF. GIF output uses 256 fixed colours.",
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                style = TextStyle(color = MP.TextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
            )
        }

        if (status.isNotEmpty()) {
            BasicText(
                text = status,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                style = TextStyle(color = MP.TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
            )
        }
    }
}
