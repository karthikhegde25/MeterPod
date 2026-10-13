package com.karthikhegde.meterpod

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.karthikhegde.meterpod.color.ColorUtils
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
class MagnifierFragment : Fragment() {

    private lateinit var pickerGroup: View
    private lateinit var magnifierGroup: View

    private lateinit var hsvPicker: HsvPickerView
    private lateinit var pickerSwatch: View
    private lateinit var pickerHexText: TextView
    private lateinit var pickerNameText: TextView

    private lateinit var magnifierView: MagnifierView
    private lateinit var pixelSwatch: View
    private lateinit var pixelHexText: TextView
    private lateinit var pixelNameText: TextView

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let { showImage(it) }
    }

    private val chooseFromGallery = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        val bitmap = loadDownsampledBitmap(uri)
        if (bitmap != null) {
            showImage(bitmap)
        } else {
            Toast.makeText(requireContext(), "Couldn't load that image.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_magnifier, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        pickerGroup = view.findViewById(R.id.pickerGroup)
        magnifierGroup = view.findViewById(R.id.magnifierGroup)

        hsvPicker = view.findViewById(R.id.hsvPicker)
        pickerSwatch = view.findViewById(R.id.pickerSwatch)
        pickerHexText = view.findViewById(R.id.pickerHexText)
        pickerNameText = view.findViewById(R.id.pickerNameText)

        magnifierView = view.findViewById(R.id.magnifierView)
        pixelSwatch = view.findViewById(R.id.pixelSwatch)
        pixelHexText = view.findViewById(R.id.pixelHexText)
        pixelNameText = view.findViewById(R.id.pixelNameText)

        hsvPicker.setColor(Color.WHITE)
        updatePickerColorInfo(Color.WHITE)
        hsvPicker.onColorChanged = { color -> updatePickerColorInfo(color) }

        view.findViewById<Button>(R.id.takePhotoButton).setOnClickListener {
            takePhoto.launch(null)
        }
        view.findViewById<Button>(R.id.chooseGalleryButton).setOnClickListener {
            chooseFromGallery.launch("image/*")
        }
        view.findViewById<Button>(R.id.changeImageButton).setOnClickListener {
            showPicker()
        }

        magnifierView.onPixelSelected = { _, _, color -> updatePixelColorInfo(color) }
    }

    private fun showImage(bitmap: Bitmap) {
        magnifierView.setBitmap(bitmap)
        pickerGroup.visibility = View.GONE
        magnifierGroup.visibility = View.VISIBLE
        pixelHexText.text = "Tap the image to pick a pixel"
        pixelNameText.text = ""
        pixelSwatch.setBackgroundColor(Color.parseColor("#1B2127"))
    }

    private fun showPicker() {
        magnifierGroup.visibility = View.GONE
        pickerGroup.visibility = View.VISIBLE
        magnifierView.clear()
    }

    private fun updatePickerColorInfo(color: Int) {
        pickerSwatch.setBackgroundColor(color)
        pickerHexText.text = ColorUtils.toHex(color)
        val (name, exact) = ColorUtils.nearestName(color)
        pickerNameText.text = if (exact) name else "~ $name"
    }

    private fun updatePixelColorInfo(color: Int) {
        pixelSwatch.setBackgroundColor(color)
        pixelHexText.text = ColorUtils.toHex(color)
        val (name, exact) = ColorUtils.nearestName(color)
        pixelNameText.text = if (exact) name else "~ $name (closest match)"
    }

    /** Decodes a gallery image URI at a manageable size instead of full (potentially huge) resolution. */
    private fun loadDownsampledBitmap(uri: Uri): Bitmap? {
        val maxDimension = 800

        return try {
            // Pass 1: measure only. BitmapFactory.decodeStream() intentionally
            // returns null here because inJustDecodeBounds=true - that's expected,
            // not a failure, so we must NOT treat this null as "decoding failed".
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val boundsStream = openInputStreamSafely(uri) ?: return null
            boundsStream.use { BitmapFactory.decodeStream(it, null, boundsOptions) }

            var sampleSize = 1
            val longestSide = max(boundsOptions.outWidth, boundsOptions.outHeight)
            while (longestSide / sampleSize > maxDimension) {
                sampleSize *= 2
            }

            // Pass 2: the real decode, needs a fresh stream since the first was consumed.
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decodeStream = openInputStreamSafely(uri) ?: return null
            decodeStream.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
        } catch (e: FileNotFoundException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun openInputStreamSafely(uri: Uri) = try {
        requireContext().contentResolver.openInputStream(uri)
    } catch (e: Exception) {
        null
    }
}
