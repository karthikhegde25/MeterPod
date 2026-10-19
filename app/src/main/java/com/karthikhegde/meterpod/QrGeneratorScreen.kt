package com.karthikhegde.meterpod

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.karthikhegde.meterpod.compose.DropdownField
import com.karthikhegde.meterpod.compose.FieldLabel
import com.karthikhegde.meterpod.compose.KeyGeometries
import com.karthikhegde.meterpod.compose.KeyPalettes
import com.karthikhegde.meterpod.compose.Key3D
import com.karthikhegde.meterpod.compose.MP
import com.karthikhegde.meterpod.compose.MeterTextField
import com.karthikhegde.meterpod.compose.RealButton
import com.karthikhegde.meterpod.compose.SectionLabel
import com.karthikhegde.meterpod.compose.colorSwatch
import com.karthikhegde.meterpod.qr.QrCodeRenderer
import com.karthikhegde.meterpod.qr.QrContentType
import com.karthikhegde.meterpod.qr.QrDotShape
import com.karthikhegde.meterpod.qr.QrErrorCorrection
import com.karthikhegde.meterpod.qr.QrEyeShape
import com.karthikhegde.meterpod.qr.QrFileSaver
import com.karthikhegde.meterpod.qr.QrPayloadBuilder
import com.karthikhegde.meterpod.ui.HsvPickerView
import kotlin.math.roundToInt

/** One dynamic form field (was addField(...) in the fragment). */
private class QrFieldSpec(
    val key: String,
    val hint: String,
    val multiline: Boolean = false,
    /** null = plain EditText (no explicit inputType); otherwise a single-line field with this keyboard. */
    val keyboardType: KeyboardType? = null,
    val defaultValue: String = ""
)

/** Fields shown below the content-type spinner for the selected type (was buildFieldsFor). */
private fun qrFieldSpecsFor(type: QrContentType): List<QrFieldSpec> = when (type) {
    QrContentType.TEXT -> listOf(QrFieldSpec("text", "Text", multiline = true))
    QrContentType.URL -> listOf(QrFieldSpec("url", "URL (e.g. example.com)"))
    QrContentType.WIFI -> listOf(
        QrFieldSpec("ssid", "Network name (SSID)"),
        QrFieldSpec("password", "Password"),
        QrFieldSpec("encryption", "Encryption (WPA / WEP / nopass)", defaultValue = "WPA")
    )
    QrContentType.PHONE -> listOf(QrFieldSpec("number", "Phone number", keyboardType = KeyboardType.Phone))
    QrContentType.SMS -> listOf(
        QrFieldSpec("number", "Phone number", keyboardType = KeyboardType.Phone),
        QrFieldSpec("message", "Message", multiline = true)
    )
    QrContentType.EMAIL -> listOf(
        QrFieldSpec("address", "Email address", keyboardType = KeyboardType.Email),
        QrFieldSpec("subject", "Subject"),
        QrFieldSpec("body", "Body", multiline = true)
    )
    QrContentType.GEO -> listOf(
        QrFieldSpec("latitude", "Latitude", keyboardType = KeyboardType.Decimal),
        QrFieldSpec("longitude", "Longitude", keyboardType = KeyboardType.Decimal)
    )
    QrContentType.CONTACT -> listOf(
        QrFieldSpec("name", "Full name"),
        QrFieldSpec("phone", "Phone", keyboardType = KeyboardType.Phone),
        QrFieldSpec("email", "Email", keyboardType = KeyboardType.Email),
        QrFieldSpec("organization", "Organization"),
        QrFieldSpec("url", "Website")
    )
    QrContentType.EVENT -> listOf(
        QrFieldSpec("title", "Event title"),
        QrFieldSpec("location", "Location"),
        QrFieldSpec("start", "Start (YYYYMMDDTHHMMSS)"),
        QrFieldSpec("end", "End (YYYYMMDDTHHMMSS)"),
        QrFieldSpec("description", "Description", multiline = true)
    )
}

private fun contrastRatio(a: Int, b: Int): Double {
    val la = androidx.core.graphics.ColorUtils.calculateLuminance(a)
    val lb = androidx.core.graphics.ColorUtils.calculateLuminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/**
 * Builds a QR code from one of several structured content types, with
 * custom color/dot-shape/error-correction/logo styling, then lets the
 * person save it to the gallery or share it.
 *
 * Generation runs on a background thread (QR encoding + Path building
 * for Rounded/Circle dot shapes over a large payload can take a visible
 * moment) while the Generate button shows a spinner and disables itself;
 * the result bitmap is posted back to the main thread. Any failure shows
 * an AlertDialog with the real exception message (not just a silent
 * no-op or a vague toast), and is logged for logcat too.
 */
@Composable
fun QrGeneratorScreen() {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    // fieldInputs: current text of each dynamic field, keyed like the fragment's fieldInputs map.
    val fieldInputs: SnapshotStateMap<String, String> = remember {
        mutableStateMapOf<String, String>().apply {
            qrFieldSpecsFor(QrContentType.TEXT).forEach { this[it.key] = it.defaultValue }
        }
    }
    var selectedContentType by remember { mutableStateOf(QrContentType.TEXT) }
    var selectedDotShape by remember { mutableStateOf(QrDotShape.SQUARE) }
    var selectedEyeShape by remember { mutableStateOf(QrEyeShape.SQUARE) }
    var selectedFrameShape by remember { mutableStateOf(QrEyeShape.SQUARE) }
    var pixelRounding by remember { mutableFloatStateOf(0f) } // percent, 0..100
    var selectedErrorCorrection by remember { mutableStateOf(QrErrorCorrection.MEDIUM) }
    var foregroundColor by remember { mutableIntStateOf(android.graphics.Color.BLACK) }
    var backgroundColor by remember { mutableIntStateOf(android.graphics.Color.WHITE) }
    var logoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var lastGeneratedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var logoSize by remember { mutableFloatStateOf(20f) } // SeekBar max=40, progress=20
    var generating by remember { mutableStateOf(false) }

    // isAdded equivalent: false once the screen has left composition.
    val alive = remember { booleanArrayOf(true) }
    DisposableEffect(Unit) { onDispose { alive[0] = false } }

    var foregroundPickerOpen by remember { mutableStateOf(false) }
    var backgroundPickerOpen by remember { mutableStateOf(false) }
    val foregroundPickerRef = remember { arrayOfNulls<HsvPickerView>(1) }
    val backgroundPickerRef = remember { arrayOfNulls<HsvPickerView>(1) }

    /** Rebuilds the form fields below the content-type spinner for the selected type. */
    fun buildFieldsFor(type: QrContentType) {
        fieldInputs.clear()
        qrFieldSpecsFor(type).forEach { fieldInputs[it.key] = it.defaultValue }
    }

    fun fieldValue(key: String): String = fieldInputs[key]?.trim().orEmpty()

    fun buildPayload(): String {
        return when (selectedContentType) {
            QrContentType.TEXT -> QrPayloadBuilder.plainText(fieldValue("text"))
            QrContentType.URL -> QrPayloadBuilder.url(fieldValue("url"))
            QrContentType.WIFI -> QrPayloadBuilder.wifi(
                fieldValue("ssid"), fieldValue("password"), fieldValue("encryption").ifBlank { "WPA" }, hidden = false
            )
            QrContentType.PHONE -> QrPayloadBuilder.phone(fieldValue("number"))
            QrContentType.SMS -> QrPayloadBuilder.sms(fieldValue("number"), fieldValue("message"))
            QrContentType.EMAIL -> QrPayloadBuilder.email(fieldValue("address"), fieldValue("subject"), fieldValue("body"))
            QrContentType.GEO -> QrPayloadBuilder.geo(fieldValue("latitude"), fieldValue("longitude"))
            QrContentType.CONTACT -> QrPayloadBuilder.contact(
                fieldValue("name"), fieldValue("phone"), fieldValue("email"), fieldValue("organization"), fieldValue("url")
            )
            QrContentType.EVENT -> QrPayloadBuilder.event(
                fieldValue("title"), fieldValue("location"), fieldValue("start"), fieldValue("end"), fieldValue("description")
            )
        }
    }

    fun onGenerateSuccess(bitmap: Bitmap) {
        lastGeneratedBitmap = bitmap
        generating = false
    }

    fun onGenerateFailure(e: Exception) {
        generating = false
        if (alive[0]) {
            AlertDialog.Builder(context)
                .setTitle("Couldn't generate QR code")
                .setMessage(e.message ?: e.toString())
                .setPositiveButton("OK", null)
                .show()
        }
    }

    fun generate() {
        val payload = buildPayload()
        if (payload.isBlank()) {
            Toast.makeText(context, "Fill in the content fields first", Toast.LENGTH_SHORT).show()
            return
        }

        if (contrastRatio(foregroundColor, backgroundColor) < 2.5) {
            Toast.makeText(context, "Low contrast between colours - the QR code may not scan", Toast.LENGTH_LONG).show()
        }

        generating = true

        val logo = logoBitmap
        val logoFraction = logoSize.roundToInt().coerceAtLeast(10) / 100f
        val shape = selectedDotShape
        val eye = selectedEyeShape
        val frame = selectedFrameShape
        val rounding = pixelRounding / 100f
        val ec = selectedErrorCorrection
        val fg = foregroundColor
        val bg = backgroundColor

        Thread {
            try {
                val bitmap = QrCodeRenderer.render(
                    content = payload,
                    sizePx = 1024,
                    foregroundColor = fg,
                    backgroundColor = bg,
                    dotShape = shape,
                    eyeShape = eye,
                    frameShape = frame,
                    pixelRounding = rounding,
                    errorCorrection = ec,
                    logo = logo,
                    logoSizeFraction = logoFraction
                )
                mainHandler.post { onGenerateSuccess(bitmap) }
            } catch (e: Exception) {
                Log.e("QrGeneratorFragment", "QR generation failed", e)
                mainHandler.post { onGenerateFailure(e) }
            }
        }.start()
    }

    fun saveCurrent() {
        val bitmap = lastGeneratedBitmap ?: return
        val uri = QrFileSaver.saveToGallery(context, bitmap, "meterpod_qr_${System.currentTimeMillis()}")
        Toast.makeText(
            context,
            if (uri != null) "Saved to Pictures/MeterPod" else "Couldn't save the image",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun shareCurrent() {
        val bitmap = lastGeneratedBitmap ?: return
        val uri = QrFileSaver.cacheForSharing(context, bitmap, "meterpod_qr_share")
        context.startActivity(Intent.createChooser(QrFileSaver.shareIntent(uri), "Share QR code"))
    }

    fun loadLogo(uri: Uri) {
        try {
            val resolver = context.contentResolver
            val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            if (bitmap != null) {
                logoBitmap = bitmap
            } else {
                Toast.makeText(context, "Couldn't load that image", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("QrGeneratorFragment", "Logo load failed", e)
            Toast.makeText(context, "Couldn't load that image", Toast.LENGTH_SHORT).show()
        }
    }

    val pickLogoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) loadLogo(uri)
    }

    val dropdownLabels = listOf("Square", "Rounded", "Circle")
    val contentTypeLabels = remember { QrContentType.values().map { it.label } }
    val errorCorrectionLabels = remember { QrErrorCorrection.values().map { it.label } }

    Column(
        Modifier
            .fillMaxSize()
            .background(MP.Background)
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        // Preview
        Box(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(MP.Surface)
        ) {
            val preview = lastGeneratedBitmap
            if (preview != null) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(220.dp)
                        .align(Alignment.Center),
                    contentScale = ContentScale.Fit
                )
            }
            if (generating) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MP.Needle
                )
            }
        }

        // Content type
        SectionLabel("Content Type")
        DropdownField(
            options = contentTypeLabels,
            selectedIndex = selectedContentType.ordinal,
            onSelected = { position ->
                // Spinner only reports a selection that actually changes the position.
                if (position != selectedContentType.ordinal) {
                    selectedContentType = QrContentType.values()[position]
                    buildFieldsFor(selectedContentType)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            key(selectedContentType) {
                qrFieldSpecsFor(selectedContentType).forEach { spec ->
                    BasicText(
                        text = spec.hint,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                        style = TextStyle(color = Color(0xFF9AA5B1), fontSize = 12.sp)
                    )
                    MeterTextField(
                        value = fieldInputs[spec.key] ?: "",
                        onValueChange = { fieldInputs[spec.key] = it },
                        modifier = Modifier.fillMaxWidth(),
                        textSize = 18f,
                        textColor = Color(0xFFFFFFFF),
                        singleLine = spec.keyboardType != null,
                        minLines = if (spec.multiline) 2 else 1,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        keyboardOptions = if (spec.keyboardType != null) {
                            KeyboardOptions(keyboardType = spec.keyboardType)
                        } else KeyboardOptions.Default
                    )
                }
            }
        }

        // Style
        SectionLabel("Style")

        // Foreground (QR dots) colour
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FieldLabel("QR Colour (foreground)", Modifier.weight(1f))
            Box(
                Modifier
                    .size(28.dp)
                    .colorSwatch(Color(foregroundColor))
            )
        }
        QrPresetRow(
            hexes = listOf("#000000", "#0D47A1", "#1B5E20", "#B71C1C", "#4A148C", "#E65100", "#FFFFFF")
        ) { color ->
            foregroundColor = color
            foregroundPickerRef[0]?.setColor(color)
        }
        QrCustomColorToggle(open = foregroundPickerOpen, current = foregroundColor) { foregroundPickerOpen = !foregroundPickerOpen }
        if (foregroundPickerOpen) {
            QrColorDialog(
                title = "QR colour",
                initial = foregroundColor,
                onChange = { color -> foregroundColor = color },
                onDismiss = { foregroundPickerOpen = false }
            )
        }

        // Background colour
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FieldLabel("Background Colour", Modifier.weight(1f))
            Box(
                Modifier
                    .size(28.dp)
                    .colorSwatch(Color(backgroundColor))
            )
        }
        QrPresetRow(
            hexes = listOf("#FFFFFF", "#FFF8E1", "#E3F2FD", "#E8F5E9", "#FCE4EC", "#F5F5F5", "#000000")
        ) { color ->
            backgroundColor = color
            backgroundPickerRef[0]?.setColor(color)
        }
        QrCustomColorToggle(open = backgroundPickerOpen, current = backgroundColor) { backgroundPickerOpen = !backgroundPickerOpen }
        if (backgroundPickerOpen) {
            QrColorDialog(
                title = "Background colour",
                initial = backgroundColor,
                onChange = { color -> backgroundColor = color },
                onDismiss = { backgroundPickerOpen = false }
            )
        }

        QrContrastIndicator(foregroundColor, backgroundColor)

        FieldLabel("Dot Shape")
        DropdownField(
            options = dropdownLabels,
            selectedIndex = selectedDotShape.ordinal,
            onSelected = { position ->
                selectedDotShape = QrDotShape.values()[position]
                if (selectedDotShape == QrDotShape.ROUNDED && pixelRounding == 0f) pixelRounding = 30f
            },
            modifier = Modifier.fillMaxWidth()
        )

        val roundingEnabled = selectedDotShape != QrDotShape.CIRCLE
        FieldLabel(
            if (roundingEnabled) "Pixel corner rounding: ${pixelRounding.roundToInt()}%"
            else "Pixel corner rounding (not used for circle dots)"
        )
        Slider(
            value = pixelRounding,
            onValueChange = { pixelRounding = it.roundToInt().toFloat() },
            modifier = Modifier.fillMaxWidth(),
            enabled = roundingEnabled,
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = MP.Accent,
                activeTrackColor = MP.Accent,
                inactiveTrackColor = MP.SurfaceOutline,
                disabledThumbColor = MP.SurfaceOutline,
                disabledActiveTrackColor = MP.SurfaceOutline,
                disabledInactiveTrackColor = MP.SurfaceOutline
            )
        )

        FieldLabel("Frame Shape")
        DropdownField(
            options = dropdownLabels,
            selectedIndex = selectedFrameShape.ordinal,
            onSelected = { position -> selectedFrameShape = QrEyeShape.values()[position] },
            modifier = Modifier.fillMaxWidth()
        )

        FieldLabel("Eye Shape")
        DropdownField(
            options = dropdownLabels,
            selectedIndex = selectedEyeShape.ordinal,
            onSelected = { position -> selectedEyeShape = QrEyeShape.values()[position] },
            modifier = Modifier.fillMaxWidth()
        )

        FieldLabel("Error Correction")
        DropdownField(
            options = errorCorrectionLabels,
            selectedIndex = selectedErrorCorrection.ordinal,
            onSelected = { position -> selectedErrorCorrection = QrErrorCorrection.values()[position] },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(
                text = "Logo (optional)",
                modifier = Modifier.weight(1f),
                style = TextStyle(color = MP.TextPrimary, fontSize = 14.sp)
            )
            RealButton(
                text = "Choose",
                onClick = { pickLogoLauncher.launch("image/*") },
                primary = true,
                textColor = Color(0xFFFFFFFF),
                compact = true
            )
            if (logoBitmap != null) {
                RealButton(
                    text = "Remove",
                    onClick = { logoBitmap = null },
                    modifier = Modifier.padding(start = 8.dp),
                    primary = false,
                    textColor = MP.TextPrimary,
                    compact = true
                )
            }
        }

        if (logoBitmap != null) {
            Slider(
                value = logoSize,
                onValueChange = { logoSize = it.roundToInt().toFloat() },
                modifier = Modifier.fillMaxWidth(),
                valueRange = 0f..40f,
                steps = 39,
                colors = SliderDefaults.colors(
                    thumbColor = MP.Accent,
                    activeTrackColor = MP.Accent,
                    inactiveTrackColor = MP.SurfaceOutline,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                )
            )
        }

        // Actions
        QrBoldButton(
            text = if (generating) "Generating…" else "Generate",
            onClick = { generate() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
                .height(52.dp),
            enabled = !generating
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
        ) {
            RealButton(
                text = "Save",
                onClick = { saveCurrent() },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp)
                    .height(48.dp),
                primary = true,
                enabled = lastGeneratedBitmap != null,
                textColor = Color(0xFFFFFFFF)
            )
            RealButton(
                text = "Share",
                onClick = { shareCurrent() },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp)
                    .height(48.dp),
                primary = true,
                enabled = lastGeneratedBitmap != null,
                textColor = Color(0xFFFFFFFF)
            )
        }
    }
}

/** A row of tappable round preset colours above a picker. */
@Composable
private fun QrPresetRow(hexes: List<String>, onPick: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
    ) {
        hexes.forEach { hex ->
            val color = android.graphics.Color.parseColor(hex)
            Box(
                Modifier
                    .padding(end = 10.dp)
                    .size(30.dp)
                    .colorSwatch(Color(color))
                    .clickable { onPick(color) }
            )
        }
    }
}

/** Rainbow circle that opens / closes the full colour picker for a colour; shows the current colour inside. */
@Composable
private fun QrCustomColorToggle(open: Boolean, current: Int, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(30.dp)
                .background(
                    Brush.sweepGradient(
                        listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                    ),
                    CircleShape
                )
                .border(if (open) 3.dp else 2.dp, if (open) MP.Accent else MP.TextSecondary, CircleShape)
                .clickable { onToggle() },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(12.dp).background(Color(current), CircleShape).border(1.dp, Color.White, CircleShape))
        }
        BasicText(
            text = "Custom colour",
            modifier = Modifier.padding(start = 10.dp),
            style = TextStyle(color = MP.TextSecondary, fontSize = 12.sp)
        )
    }
}

/** Contrast ratio between the QR colour and its background, with a plain-language verdict. */
@Composable
private fun QrContrastIndicator(foreground: Int, background: Int) {
    val ratio = contrastRatio(foreground, background)
    val (verdict, tint) = when {
        ratio >= 4.0 -> "Good" to Color(0xFF4CAF50)
        ratio >= 2.5 -> "Fair - may be hard to scan" to Color(0xFFFFB300)
        else -> "Low - likely will not scan" to MP.Needle
    }
    val inverted = androidx.core.graphics.ColorUtils.calculateLuminance(foreground) >
        androidx.core.graphics.ColorUtils.calculateLuminance(background)
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(tint, CircleShape))
            BasicText(
                text = String.format("Contrast %.1f : 1  -  %s", ratio, verdict),
                modifier = Modifier.padding(start = 8.dp),
                style = TextStyle(color = MP.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            )
        }
        if (inverted) {
            BasicText(
                text = "Light dots on a dark background: some scanners cannot read inverted codes.",
                modifier = Modifier.padding(top = 4.dp),
                style = TextStyle(color = MP.TextSecondary, fontSize = 11.sp)
            )
        }
    }
}

/** RealButton (primary) with android:textStyle="bold", as on the XML Generate button. */
@Composable
private fun QrBoldButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Key3D(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = 88.dp, minHeight = 46.dp),
        geometry = KeyGeometries.Button,
        palette = KeyPalettes.Primary,
        enabled = enabled,
        contentPadding = PaddingValues(20.dp, 10.dp, 20.dp, 10.dp)
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = Color(0xFFFFFFFF),
                fontSize = 14.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        )
    }
}

/** Floating panel (a dialog over the whole screen) with a live preview and the full colour picker. */
@Composable
private fun QrColorDialog(title: String, initial: Int, onChange: (Int) -> Unit, onDismiss: () -> Unit) {
    var live by remember { mutableIntStateOf(initial) }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .background(MP.SurfaceElevated, androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                .border(1.dp, MP.SurfaceOutline, androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                .padding(18.dp)
        ) {
            BasicText(title, style = TextStyle(color = MP.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold))
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(56.dp)
                        .background(Color(live), androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                        .border(2.dp, MP.TextSecondary, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                )
                Column(Modifier.padding(start = 14.dp)) {
                    BasicText(
                        String.format("#%06X", live and 0xFFFFFF),
                        style = TextStyle(color = MP.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    )
                    BasicText("Drag on the square and the bar to change it", style = TextStyle(color = MP.TextSecondary, fontSize = 12.sp))
                }
            }
            AndroidView(
                factory = { ctx ->
                    HsvPickerView(ctx).also { picker ->
                        picker.setColor(initial)
                        picker.onColorChanged = { color -> live = color; onChange(color) }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(220.dp).padding(top = 14.dp)
            )
            RealButton(
                text = "Done",
                onClick = onDismiss,
                primary = true,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            )
        }
    }
}
