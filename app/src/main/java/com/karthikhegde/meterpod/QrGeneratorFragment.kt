package com.karthikhegde.meterpod

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.karthikhegde.meterpod.qr.QrCodeRenderer
import com.karthikhegde.meterpod.qr.QrContentType
import com.karthikhegde.meterpod.qr.QrDotShape
import com.karthikhegde.meterpod.qr.QrErrorCorrection
import com.karthikhegde.meterpod.qr.QrEyeShape
import com.karthikhegde.meterpod.qr.QrFileSaver
import com.karthikhegde.meterpod.qr.QrPayloadBuilder
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import com.karthikhegde.meterpod.ui.HsvPickerView

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
class QrGeneratorFragment : Fragment() {

    private lateinit var dynamicFieldsContainer: LinearLayout
    private lateinit var contentTypeSpinner: Spinner
    private lateinit var dotShapeSpinner: Spinner
    private lateinit var eyeShapeSpinner: Spinner
    private lateinit var errorCorrectionSpinner: Spinner
    private lateinit var foregroundColorPicker: HsvPickerView
    private lateinit var backgroundColorPicker: HsvPickerView
    private lateinit var foregroundSwatch: View
    private lateinit var backgroundSwatch: View
    private lateinit var foregroundPresets: LinearLayout
    private lateinit var backgroundPresets: LinearLayout
    private lateinit var qrPreviewImage: ImageView
    private lateinit var qrProgressBar: ProgressBar
    private lateinit var generateButton: Button
    private lateinit var saveButton: Button
    private lateinit var shareButton: Button
    private lateinit var pickLogoButton: Button
    private lateinit var removeLogoButton: Button
    private lateinit var logoSizeSeekBar: SeekBar

    private val fieldInputs = mutableMapOf<String, EditText>()
    private var selectedContentType = QrContentType.TEXT
    private var selectedDotShape = QrDotShape.SQUARE
    private var selectedEyeShape = QrEyeShape.SQUARE
    private var selectedErrorCorrection = QrErrorCorrection.MEDIUM
    private var foregroundColor = Color.BLACK
    private var backgroundColor = Color.WHITE
    private var logoBitmap: Bitmap? = null
    private var lastGeneratedBitmap: Bitmap? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private val pickLogoLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) loadLogo(uri)
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_qr_generator, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dynamicFieldsContainer = view.findViewById(R.id.dynamicFieldsContainer)
        contentTypeSpinner = view.findViewById(R.id.contentTypeSpinner)
        dotShapeSpinner = view.findViewById(R.id.dotShapeSpinner)
        eyeShapeSpinner = view.findViewById(R.id.eyeShapeSpinner)
        errorCorrectionSpinner = view.findViewById(R.id.errorCorrectionSpinner)
        foregroundColorPicker = view.findViewById(R.id.foregroundColorPicker)
        backgroundColorPicker = view.findViewById(R.id.backgroundColorPicker)
        foregroundSwatch = view.findViewById(R.id.foregroundSwatch)
        backgroundSwatch = view.findViewById(R.id.backgroundSwatch)
        foregroundPresets = view.findViewById(R.id.foregroundPresets)
        backgroundPresets = view.findViewById(R.id.backgroundPresets)
        qrPreviewImage = view.findViewById(R.id.qrPreviewImage)
        qrProgressBar = view.findViewById(R.id.qrProgressBar)
        generateButton = view.findViewById(R.id.generateButton)
        saveButton = view.findViewById(R.id.saveButton)
        shareButton = view.findViewById(R.id.shareButton)
        pickLogoButton = view.findViewById(R.id.pickLogoButton)
        removeLogoButton = view.findViewById(R.id.removeLogoButton)
        logoSizeSeekBar = view.findViewById(R.id.logoSizeSeekBar)

        setupContentTypeSpinner()
        setupDotShapeSpinner()
        setupEyeShapeSpinner()
        setupErrorCorrectionSpinner()
        setupColorPicker()
        buildFieldsFor(selectedContentType)

        generateButton.setOnClickListener { generate() }
        saveButton.setOnClickListener { saveCurrent() }
        shareButton.setOnClickListener { shareCurrent() }
        pickLogoButton.setOnClickListener { pickLogoLauncher.launch("image/*") }
        removeLogoButton.setOnClickListener {
            logoBitmap = null
            removeLogoButton.visibility = View.GONE
            logoSizeSeekBar.visibility = View.GONE
        }
    }

    private fun setupContentTypeSpinner() {
        val labels = QrContentType.values().map { it.label }
        contentTypeSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, labels)
        contentTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                selectedContentType = QrContentType.values()[position]
                buildFieldsFor(selectedContentType)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupDotShapeSpinner() {
        val labels = listOf("Square", "Rounded", "Circle")
        dotShapeSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, labels)
        dotShapeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                selectedDotShape = QrDotShape.values()[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupEyeShapeSpinner() {
        val labels = listOf("Square", "Rounded", "Circle")
        eyeShapeSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, labels)
        eyeShapeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                selectedEyeShape = QrEyeShape.values()[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupErrorCorrectionSpinner() {
        val labels = QrErrorCorrection.values().map { it.label }
        errorCorrectionSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, labels)
        errorCorrectionSpinner.setSelection(1) // Medium
        errorCorrectionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                selectedErrorCorrection = QrErrorCorrection.values()[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupColorPicker() {
        foregroundColor = Color.BLACK
        foregroundColorPicker.setColor(foregroundColor)
        foregroundColorPicker.onColorChanged = { color ->
            foregroundColor = color
            paintSwatch(foregroundSwatch, color)
        }
        paintSwatch(foregroundSwatch, foregroundColor)
        buildPresetRow(foregroundPresets, listOf("#000000", "#0D47A1", "#1B5E20", "#B71C1C", "#4A148C", "#E65100", "#FFFFFF")) { color ->
            foregroundColor = color
            foregroundColorPicker.setColor(color)
            paintSwatch(foregroundSwatch, color)
        }

        backgroundColor = Color.WHITE
        backgroundColorPicker.setColor(backgroundColor)
        backgroundColorPicker.onColorChanged = { color ->
            backgroundColor = color
            paintSwatch(backgroundSwatch, color)
        }
        paintSwatch(backgroundSwatch, backgroundColor)
        buildPresetRow(backgroundPresets, listOf("#FFFFFF", "#FFF8E1", "#E3F2FD", "#E8F5E9", "#FCE4EC", "#F5F5F5", "#000000")) { color ->
            backgroundColor = color
            backgroundColorPicker.setColor(color)
            paintSwatch(backgroundSwatch, color)
        }
    }

    private fun paintSwatch(swatch: View, color: Int) {
        val layers = swatch.background?.mutate() as? LayerDrawable ?: return
        (layers.findDrawableByLayerId(R.id.swatchFill) as? GradientDrawable)?.setColor(color)
    }

    /** A row of tappable round preset colours above a picker. */
    private fun buildPresetRow(row: LinearLayout, hexes: List<String>, onPick: (Int) -> Unit) {
        row.removeAllViews()
        hexes.forEach { hex ->
            val color = Color.parseColor(hex)
            val chip = View(requireContext()).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    setStroke(dp(2), Color.parseColor("#8C98A6"))
                }
                layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(10) }
                setOnClickListener { onPick(color) }
            }
            row.addView(chip)
        }
    }

    /** Rebuilds the form fields below the content-type spinner for the selected type. */
    private fun buildFieldsFor(type: QrContentType) {
        dynamicFieldsContainer.removeAllViews()
        fieldInputs.clear()

        when (type) {
            QrContentType.TEXT -> addField("text", "Text", multiline = true)
            QrContentType.URL -> addField("url", "URL (e.g. example.com)")
            QrContentType.WIFI -> {
                addField("ssid", "Network name (SSID)")
                addField("password", "Password")
                addField("encryption", "Encryption (WPA / WEP / nopass)", defaultValue = "WPA")
            }
            QrContentType.PHONE -> addField("number", "Phone number", inputType = InputType.TYPE_CLASS_PHONE)
            QrContentType.SMS -> {
                addField("number", "Phone number", inputType = InputType.TYPE_CLASS_PHONE)
                addField("message", "Message", multiline = true)
            }
            QrContentType.EMAIL -> {
                addField("address", "Email address", inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
                addField("subject", "Subject")
                addField("body", "Body", multiline = true)
            }
            QrContentType.GEO -> {
                addField("latitude", "Latitude", inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL)
                addField("longitude", "Longitude", inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL)
            }
            QrContentType.CONTACT -> {
                addField("name", "Full name")
                addField("phone", "Phone", inputType = InputType.TYPE_CLASS_PHONE)
                addField("email", "Email", inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
                addField("organization", "Organization")
                addField("url", "Website")
            }
            QrContentType.EVENT -> {
                addField("title", "Event title")
                addField("location", "Location")
                addField("start", "Start (YYYYMMDDTHHMMSS)")
                addField("end", "End (YYYYMMDDTHHMMSS)")
                addField("description", "Description", multiline = true)
            }
        }
    }

    private fun addField(
        key: String,
        hint: String,
        multiline: Boolean = false,
        inputType: Int? = null,
        defaultValue: String = ""
    ) {
        val label = TextView(requireContext()).apply {
            text = hint
            setTextColor(Color.parseColor("#9AA5B1"))
            textSize = 12f
            setPadding(0, dp(10), 0, dp(4))
        }
        val editText = EditText(requireContext()).apply {
            setText(defaultValue)
            setHintTextColor(Color.parseColor("#5A6672"))
            setTextColor(Color.parseColor("#FFFFFF"))
            setBackgroundResource(R.drawable.bg_input_field)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            if (multiline) {
                this.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                minLines = 2
            } else if (inputType != null) {
                this.inputType = inputType
            }
        }
        fieldInputs[key] = editText
        dynamicFieldsContainer.addView(label)
        dynamicFieldsContainer.addView(editText)
    }

    private fun fieldValue(key: String): String = fieldInputs[key]?.text?.toString()?.trim().orEmpty()

    private fun buildPayload(): String {
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

    private fun generate() {
        val payload = buildPayload()
        if (payload.isBlank()) {
            Toast.makeText(requireContext(), "Fill in the content fields first", Toast.LENGTH_SHORT).show()
            return
        }

        if (contrastRatio(foregroundColor, backgroundColor) < 2.5) {
            Toast.makeText(requireContext(), "Low contrast between colours - the QR code may not scan", Toast.LENGTH_LONG).show()
        }

        generateButton.isEnabled = false
        generateButton.text = "Generating…"
        qrProgressBar.visibility = View.VISIBLE

        val logo = logoBitmap
        val logoFraction = logoSizeSeekBar.progress.coerceAtLeast(10) / 100f
        val shape = selectedDotShape
        val eye = selectedEyeShape
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

    private fun onGenerateSuccess(bitmap: Bitmap) {
        lastGeneratedBitmap = bitmap
        qrPreviewImage.setImageBitmap(bitmap)
        qrProgressBar.visibility = View.GONE
        generateButton.isEnabled = true
        generateButton.text = "Generate"
        saveButton.isEnabled = true
        shareButton.isEnabled = true
    }

    private fun onGenerateFailure(e: Exception) {
        qrProgressBar.visibility = View.GONE
        generateButton.isEnabled = true
        generateButton.text = "Generate"
        if (isAdded) {
            AlertDialog.Builder(requireContext())
                .setTitle("Couldn't generate QR code")
                .setMessage(e.message ?: e.toString())
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun saveCurrent() {
        val bitmap = lastGeneratedBitmap ?: return
        val uri = QrFileSaver.saveToGallery(requireContext(), bitmap, "meterpod_qr_${System.currentTimeMillis()}")
        Toast.makeText(
            requireContext(),
            if (uri != null) "Saved to Pictures/MeterPod" else "Couldn't save the image",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun shareCurrent() {
        val bitmap = lastGeneratedBitmap ?: return
        val uri = QrFileSaver.cacheForSharing(requireContext(), bitmap, "meterpod_qr_share")
        startActivity(Intent.createChooser(QrFileSaver.shareIntent(uri), "Share QR code"))
    }

    private fun loadLogo(uri: Uri) {
        try {
            val resolver = requireContext().contentResolver
            val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            if (bitmap != null) {
                logoBitmap = bitmap
                removeLogoButton.visibility = View.VISIBLE
                logoSizeSeekBar.visibility = View.VISIBLE
            } else {
                Toast.makeText(requireContext(), "Couldn't load that image", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("QrGeneratorFragment", "Logo load failed", e)
            Toast.makeText(requireContext(), "Couldn't load that image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun contrastRatio(a: Int, b: Int): Double {
        val la = androidx.core.graphics.ColorUtils.calculateLuminance(a)
        val lb = androidx.core.graphics.ColorUtils.calculateLuminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun dp(value: Int): Int {
        val density = resources.displayMetrics.density
        return (value * density).toInt()
    }
}
