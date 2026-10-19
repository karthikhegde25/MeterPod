package com.karthikhegde.meterpod

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.karthikhegde.meterpod.color.ColorUtils
import com.karthikhegde.meterpod.compose.MP
import com.karthikhegde.meterpod.compose.RealButton
import com.karthikhegde.meterpod.ui.HsvPickerView
import com.karthikhegde.meterpod.ui.MagnifierView
import java.io.FileNotFoundException
import kotlin.math.max

/**
 * Magnifier + color picker. Two states:
 *
 * - Picker state (default): top half offers a photo source (camera or
 *   gallery), bottom half is a manual HSV gradient picker for choosing a
 *   color directly without an image at all.
 * - Magnifier state (once an image is loaded): the picker hides, and the
 *   image fills the screen for pinch-zoom/pan; tapping a pixel shows its
 *   exact color (hex + nearest named color) in a bar along the bottom
 *   edge.
 *
 * Images are downsampled before display (gallery photos especially can be
 * many megapixels) so individual source pixels are large enough to be
 * meaningfully zoomed into and tapped, rather than each pixel being far
 * smaller than a finger could ever target.
 */
@Composable
fun MagnifierScreen() {
    val context = LocalContext.current

    // true = magnifier group visible, false = picker group visible (both stay composed, like View.GONE,
    // so the HSV picker keeps its hue/saturation/value and the magnifier its size).
    var magnifierShown by remember { mutableStateOf(false) }

    val white = android.graphics.Color.WHITE
    var pickerColor by remember { mutableIntStateOf(white) }
    var pickerHexText by remember { mutableStateOf(ColorUtils.toHex(white)) }
    var pickerNameText by remember {
        mutableStateOf(
            ColorUtils.nearestName(white).let { (name, exact) -> if (exact) name else "~ $name" }
        )
    }

    var pixelColor by remember { mutableIntStateOf(android.graphics.Color.parseColor("#1B2127")) }
    var pixelHexText by remember { mutableStateOf("Tap the image to pick a pixel") }
    var pixelNameText by remember { mutableStateOf("") }

    val magnifierHolder = remember { arrayOfNulls<MagnifierView>(1) }
    val pickerHolder = remember { arrayOfNulls<HsvPickerView>(1) }

    fun copyHex(hex: String) {
        if (!hex.startsWith("#")) { Toast.makeText(context, "Pick a colour first", Toast.LENGTH_SHORT).show(); return }
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
            ?.setPrimaryClip(ClipData.newPlainText("Colour", hex))
        Toast.makeText(context, "Copied $hex", Toast.LENGTH_SHORT).show()
    }

    fun updatePickerColorInfo(color: Int) {
        pickerColor = color
        pickerHexText = ColorUtils.toHex(color)
        val (name, exact) = ColorUtils.nearestName(color)
        pickerNameText = if (exact) name else "~ $name"
    }

    fun updatePixelColorInfo(color: Int) {
        pixelColor = color
        pixelHexText = ColorUtils.toHex(color)
        val (name, exact) = ColorUtils.nearestName(color)
        pixelNameText = if (exact) name else "~ $name (closest match)"
    }

    fun showImage(bitmap: Bitmap) {
        magnifierHolder[0]?.setBitmap(bitmap)
        magnifierShown = true
        pixelHexText = "Tap the image to pick a pixel"
        pixelNameText = ""
        pixelColor = android.graphics.Color.parseColor("#1B2127")
    }

    fun showPicker() {
        magnifierShown = false
        magnifierHolder[0]?.clear()
    }

    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let { showImage(it) }
    }

    val chooseFromGallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val bitmap = loadDownsampledBitmap(context, uri)
            if (bitmap != null) {
                showImage(bitmap)
            } else {
                Toast.makeText(context, "Couldn't load that image.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MP.Background)
    ) {
        // State A: choose an image source (top half) + manual HSV color picker (bottom half)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .goneIf(magnifierShown)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                BasicText(
                    text = "Magnifier + Color Picker",
                    style = TextStyle(color = MP.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                )
                BasicText(
                    text = "Load a photo to zoom in and pick a color from any pixel, or use the gradient picker below to choose one manually.",
                    modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
                    style = TextStyle(color = MP.TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
                )
                RealButton(
                    text = "Take Photo",
                    onClick = { takePhoto.launch(null) },
                    modifier = Modifier.padding(bottom = 10.dp),
                    primary = true,
                    textColor = MP.TextPrimary
                )
                RealButton(
                    text = "Choose from Gallery",
                    onClick = { chooseFromGallery.launch("image/*") },
                    primary = false,
                    textColor = MP.TextPrimary
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(pickerColor))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        BasicText(
                            text = pickerHexText,
                            style = TextStyle(color = MP.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        )
                        BasicText(
                            text = pickerNameText,
                            style = TextStyle(color = MP.TextSecondary, fontSize = 12.sp)
                        )
                    }
                }

                HexEntry(
                    shown = pickerHexText,
                    onCopy = { copyHex(pickerHexText) },
                    onApply = { text ->
                        val c = ColorUtils.parseHex(text)
                        if (c == null) Toast.makeText(context, "Enter a hex colour like #FF8800", Toast.LENGTH_SHORT).show()
                        else { pickerHolder[0]?.setColor(c); updatePickerColorInfo(c) }
                    }
                )

                AndroidView(
                    factory = { ctx ->
                        HsvPickerView(ctx).also { picker ->
                            pickerHolder[0] = picker
                            picker.setColor(white)
                            picker.onColorChanged = { color -> updatePickerColorInfo(color) }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }

        // State B: zoomed image with pixel-level color picking
        Box(
            modifier = Modifier
                .fillMaxSize()
                .goneIf(!magnifierShown)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        MagnifierView(ctx).also { mv ->
                            mv.setBackgroundColor(MP.Surface.toArgb())
                            mv.onPixelSelected = { _, _, color -> updatePixelColorInfo(color) }
                            magnifierHolder[0] = mv
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MP.Surface)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(pixelColor))
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        BasicText(
                            text = pixelHexText,
                            style = TextStyle(color = MP.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        )
                        BasicText(
                            text = pixelNameText,
                            style = TextStyle(color = MP.TextSecondary, fontSize = 12.sp)
                        )
                    }
                }
                HexEntry(
                    shown = pixelHexText,
                    modifier = Modifier.background(MP.Surface).padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                    onCopy = { copyHex(pixelHexText) },
                    onApply = { text ->
                        val c = ColorUtils.parseHex(text)
                        if (c == null) Toast.makeText(context, "Enter a hex colour like #FF8800", Toast.LENGTH_SHORT).show()
                        else magnifierHolder[0]?.selectNearest(c)
                    }
                )
            }

            RealButton(
                text = "Change Image",
                onClick = { showPicker() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                primary = false,
                compact = true,
                textColor = MP.TextPrimary
            )
        }
    }
}

/** Equivalent of View.GONE: the node takes no space and its children are neither measured, placed, drawn nor touched. */
private fun Modifier.goneIf(gone: Boolean): Modifier =
    if (gone) this.layout { _, _ -> layout(0, 0) {} } else this

/** Decodes a gallery image URI at a manageable size instead of full (potentially huge) resolution. */
private fun loadDownsampledBitmap(context: Context, uri: Uri): Bitmap? {
    val maxDimension = 800

    return try {
        // Pass 1: measure only. BitmapFactory.decodeStream() intentionally
        // returns null here because inJustDecodeBounds=true - that's expected,
        // not a failure, so we must NOT treat this null as "decoding failed".
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = openInputStreamSafely(context, uri) ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, boundsOptions) }

        var sampleSize = 1
        val longestSide = max(boundsOptions.outWidth, boundsOptions.outHeight)
        while (longestSide / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        // Pass 2: the real decode, needs a fresh stream since the first was consumed.
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decodeStream = openInputStreamSafely(context, uri) ?: return null
        decodeStream.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
    } catch (e: FileNotFoundException) {
        null
    } catch (e: Exception) {
        null
    }
}

private fun openInputStreamSafely(context: Context, uri: Uri) = try {
    context.contentResolver.openInputStream(uri)
} catch (e: Exception) {
    null
}


/** A hex text field with "Go" (move the pointer to that colour) and "Copy" buttons. */
@Composable
private fun HexEntry(
    shown: String,
    onCopy: () -> Unit,
    onApply: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .weight(1f)
                .border(1.dp, MP.SurfaceOutline, RoundedCornerShape(10.dp))
                .background(MP.SurfaceElevated, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 11.dp)
        ) {
            if (text.isEmpty()) BasicText("Type hex, e.g. #FF8800", style = TextStyle(color = MP.TextSecondary, fontSize = 14.sp))
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(7) },
                singleLine = true,
                textStyle = TextStyle(color = MP.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                cursorBrush = SolidColor(MP.Accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Go),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { onApply(text) }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        RealButton("Go", onClick = { onApply(text) }, primary = true, compact = true)
        RealButton("Copy", onClick = onCopy, compact = true, enabled = shown.startsWith("#"))
    }
}
