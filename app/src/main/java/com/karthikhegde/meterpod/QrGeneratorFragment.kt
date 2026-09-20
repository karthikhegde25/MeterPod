package com.karthikhegde.meterpod

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.karthikhegde.meterpod.color.ColorUtils
import com.karthikhegde.meterpod.qr.QrCodeRenderer
import com.karthikhegde.meterpod.qr.QrDotShape
import com.karthikhegde.meterpod.qr.QrErrorCorrection
import com.karthikhegde.meterpod.qr.QrFileSaver
import com.karthikhegde.meterpod.qr.QrPayloadBuilder
import com.karthikhegde.meterpod.ui.HsvPickerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * QR Generator: builds a scannable QR code from any of nine content types
 * (plain text, website, Wi-Fi network, phone number, SMS, email, geo
 * location, contact card, calendar event) with adjustable colors, dot
 * shape, error-correction level and an optional center logo, then lets you
 * save the result to the gallery or share it.
 *
 * The set of content types and the exact wire format each one encodes
 * (WIFI:, SMSTO:, geo:, MATMSG:, tel:, VCARD, VEVENT) follows Image
 * Toolbox's QR feature (github.com/T8RIN/ImageToolbox) so a code generated
 * here decodes identically there and in any standard QR reader. The actual
 * bit-matrix encoding is done by ZXing rather than Image Toolbox's own
 * Compose-based qrose renderer, since MeterPod is a plain-View app - see
 * `qr/QrCodeRenderer.kt`.
 */
class QrGeneratorFragment : Fragment() {

    private lateinit var qrPreviewImage: ImageView
    private lateinit var qrPreviewHint: TextView
    private lateinit var saveButton: Button
    private lateinit var shareButton: Button
    private lateinit var contentTypeSpinner: Spinner
    private lateinit var dynamicFieldsContainer: LinearLayout
    private lateinit var darkColorSwatch: View
    private lateinit var lightColorSwatch: View
    private lateinit var dotShapeSpinner: Spinner
    private lateinit var errorCorrectionSpinner: Spinner
    private lateinit var logoThumbnail: ImageView
    private lateinit var pickLogoButton: Button
    private lateinit var removeLogoButton: Button
    private lateinit var logoSizeRow: LinearLayout
    private lateinit var logoSizeSeekBar: SeekBar
    private lateinit var generateButton: Button

    private val fieldViews = mutableMapOf<String, View>()

    private var darkColor = Color.BLACK
    private var lightColor = Color.WHITE
    private var dotShape = QrDotShape.SQUARE
    private var errorCorrection = QrErrorCorrection.M
    private var logoBitmap: Bitmap? = null
    private var generatedBitmap: Bitmap? = null

    private var eventStart: Date? = null
    private var eventEnd: Date? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private val typeNames = listOf(
        "Plain text", "Website URL", "Wi‑Fi network", "Phone number", "SMS",
        "Email", "Location (geo)", "Contact card", "Calendar event"
    )

    private val pickLogo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                if (bitmap != null) {
                    logoBitmap = bitmap
                    logoThumbnail.setImageBitmap(bitmap)
                    logoThumbnail.visibility = View.VISIBLE
                    removeLogoButton.visibility = View.VISIBLE
                    logoSizeRow.visibility = View.VISIBLE
                } else {
                    Toast.makeText(requireContext(), "Couldn't load that image.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Couldn't load that image.", Toast.LENGTH_SHORT).show()
        }
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

        qrPreviewImage = view.findViewById(R.id.qrPreviewImage)
        qrPreviewHint = view.findViewById(R.id.qrPreviewHint)
        saveButton = view.findViewById(R.id.saveButton)
        shareButton = view.findViewById(R.id.shareButton)
        contentTypeSpinner = view.findViewById(R.id.contentTypeSpinner)
        dynamicFieldsContainer = view.findViewById(R.id.dynamicFieldsContainer)
        darkColorSwatch = view.findViewById(R.id.darkColorSwatch)
        lightColorSwatch = view.findViewById(R.id.lightColorSwatch)
        dotShapeSpinner = view.findViewById(R.id.dotShapeSpinner)
        errorCorrectionSpinner = view.findViewById(R.id.errorCorrectionSpinner)
        logoThumbnail = view.findViewById(R.id.logoThumbnail)
        pickLogoButton = view.findViewById(R.id.pickLogoButton)
        removeLogoButton = view.findViewById(R.id.removeLogoButton)
        logoSizeRow = view.findViewById(R.id.logoSizeRow)
        logoSizeSeekBar = view.findViewById(R.id.logoSizeSeekBar)
        generateButton = view.findViewById(R.id.generateButton)

        setupContentTypeSpinner()
        setupStyleControls()

        pickLogoButton.setOnClickListener { pickLogo.launch("image/*") }
        removeLogoButton.setOnClickListener {
            logoBitmap = null
            logoThumbnail.visibility = View.GONE
            removeLogoButton.visibility = View.GONE
            logoSizeRow.visibility = View.GONE
        }

        generateButton.setOnClickListener { generate() }
        saveButton.setOnClickListener { saveCurrent() }
        shareButton.setOnClickListener { shareCurrent() }

        renderFieldsForType(0)
    }

    // ---------------------------------------------------------------------
    // Content type selection + dynamic fields
    // ---------------------------------------------------------------------

    private fun setupContentTypeSpinner() {
        contentTypeSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, typeNames)
        contentTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                renderFieldsForType(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun renderFieldsForType(position: Int) {
        dynamicFieldsContainer.removeAllViews()
        fieldViews.clear()
        eventStart = null
        eventEnd = null

        when (position) {
            0 -> addMultilineField("text", "Text", "Anything you want encoded")
            1 -> addField("url", "Website URL", "example.com or https://example.com", InputType.TYPE_TEXT_VARIATION_URI)
            2 -> {
                addField("ssid", "Network name (SSID)", "MyWiFi")
                addField("password", "Password", "Leave blank for open networks")
                addSpinnerField("encryption", "Security", listOf("WPA/WPA2", "WEP", "Open (no password)"))
                addCheckboxField("hidden", "Hidden network")
            }
            3 -> addField("phone", "Phone number", "+1 555 123 4567", InputType.TYPE_CLASS_PHONE)
            4 -> {
                addField("smsPhone", "Phone number", "+1 555 123 4567", InputType.TYPE_CLASS_PHONE)
                addMultilineField("smsMessage", "Message", "Pre-filled text message")
            }
            5 -> {
                addField("emailAddress", "To", "someone@example.com", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
                addField("emailSubject", "Subject", "")
                addMultilineField("emailBody", "Body", "")
            }
            6 -> {
                addField("lat", "Latitude", "37.4220", InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_CLASS_NUMBER)
                addField("lng", "Longitude", "-122.0841", InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_CLASS_NUMBER)
            }
            7 -> {
                addField("firstName", "First name", "")
                addField("lastName", "Last name", "")
                addField("organization", "Organization", "")
                addField("jobTitle", "Job title", "")
                addField("phones", "Phone number(s)", "comma-separated for more than one", InputType.TYPE_CLASS_PHONE)
                addField("emails", "Email address(es)", "comma-separated for more than one", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
                addField("addresses", "Address(es)", "comma-separated for more than one")
                addField("urls", "Website(s)", "comma-separated for more than one", InputType.TYPE_TEXT_VARIATION_URI)
                addMultilineField("note", "Note", "")
            }
            8 -> {
                addField("summary", "Title", "Team meeting")
                addField("location", "Location", "")
                addField("organizer", "Organizer", "")
                addMultilineField("description", "Description", "")
                addDateTimeField("start", "Start") { eventStart = it }
                addDateTimeField("end", "End") { eventEnd = it }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Payload building
    // ---------------------------------------------------------------------

    private fun buildPayload(): String? {
        val position = contentTypeSpinner.selectedItemPosition
        return when (position) {
            0 -> QrPayloadBuilder.plainText(textOf("text")).ifBlank { null }
            1 -> QrPayloadBuilder.url(textOf("url")).ifBlank { null }
            2 -> {
                val ssid = textOf("ssid")
                if (ssid.isBlank()) return null
                val encryptionIndex = (fieldViews["encryption"] as? Spinner)?.selectedItemPosition ?: 0
                val encryption = when (encryptionIndex) {
                    0 -> QrPayloadBuilder.WifiEncryption.WPA
                    1 -> QrPayloadBuilder.WifiEncryption.WEP
                    else -> QrPayloadBuilder.WifiEncryption.OPEN
                }
                val hidden = (fieldViews["hidden"] as? CheckBox)?.isChecked ?: false
                QrPayloadBuilder.wifi(ssid, textOf("password"), encryption, hidden)
            }
            3 -> textOf("phone").ifBlank { null }?.let { QrPayloadBuilder.phone(it) }
            4 -> {
                val phone = textOf("smsPhone")
                if (phone.isBlank()) return null
                QrPayloadBuilder.sms(phone, textOf("smsMessage"))
            }
            5 -> {
                val address = textOf("emailAddress")
                if (address.isBlank()) return null
                QrPayloadBuilder.email(address, textOf("emailSubject"), textOf("emailBody"))
            }
            6 -> {
                val lat = textOf("lat")
                val lng = textOf("lng")
                if (lat.isBlank() || lng.isBlank()) return null
                QrPayloadBuilder.geo(lat, lng)
            }
            7 -> {
                val first = textOf("firstName")
                val last = textOf("lastName")
                val org = textOf("organization")
                if (first.isBlank() && last.isBlank() && org.isBlank()) return null
                QrPayloadBuilder.contact(
                    firstName = first,
                    lastName = last,
                    organization = org,
                    title = textOf("jobTitle"),
                    phones = splitList(textOf("phones")),
                    emails = splitList(textOf("emails")),
                    addresses = splitList(textOf("addresses")),
                    urls = splitList(textOf("urls")),
                    note = textOf("note")
                )
            }
            8 -> {
                val summary = textOf("summary")
                if (summary.isBlank() && eventStart == null) return null
                QrPayloadBuilder.calendarEvent(
                    summary = summary,
                    description = textOf("description"),
                    location = textOf("location"),
                    organizer = textOf("organizer"),
                    start = eventStart,
                    end = eventEnd
                )
            }
            else -> null
        }
    }

    private fun splitList(raw: String): List<String> = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    private fun textOf(key: String): String = (fieldViews[key] as? EditText)?.text?.toString()?.trim().orEmpty()

    // ---------------------------------------------------------------------
    // Generate / save / share
    // ---------------------------------------------------------------------

    private fun generate() {
        val payload = buildPayload()
        if (payload.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Fill in the required fields first.", Toast.LENGTH_SHORT).show()
            return
        }

        // Rendering (especially the rounded/circle dot shapes, which fill a
        // Path full of curves instead of plain rectangles) can take a
        // noticeable moment on a big payload like a Contact card or
        // Calendar event. Doing it on the main thread with no feedback made
        // it look like nothing happened, so: disable the button, show
        // "Generating…", do the work on a background thread, then hand the
        // result back to the UI thread.
        val logoSizeFraction = logoSizeSeekBar.progress.coerceAtLeast(5) / 100f
        val currentDarkColor = darkColor
        val currentLightColor = lightColor
        val currentErrorCorrection = errorCorrection
        val currentDotShape = dotShape
        val currentLogo = logoBitmap

        generateButton.isEnabled = false
        generateButton.text = "Generating…"

        Thread {
            var result: Bitmap? = null
            var error: Throwable? = null
            try {
                result = QrCodeRenderer.render(
                    content = payload,
                    sizePx = 900,
                    darkColor = currentDarkColor,
                    lightColor = currentLightColor,
                    errorCorrection = currentErrorCorrection,
                    dotShape = currentDotShape,
                    logo = currentLogo,
                    logoSizeFraction = logoSizeFraction
                )
            } catch (t: Throwable) {
                error = t
                Log.e("QrGeneratorFragment", "QR generation failed", t)
            }

            mainHandler.post {
                if (!isAdded) return@post
                generateButton.isEnabled = true
                generateButton.text = "Generate QR code"

                if (result != null) {
                    generatedBitmap = result
                    qrPreviewImage.setImageBitmap(result)
                    qrPreviewHint.visibility = View.GONE
                    saveButton.isEnabled = true
                    shareButton.isEnabled = true
                } else {
                    val reason = error?.message?.takeIf { it.isNotBlank() }
                        ?: error?.javaClass?.simpleName
                        ?: "Unknown error"
                    AlertDialog.Builder(requireContext())
                        .setTitle("Couldn't generate QR code")
                        .setMessage(reason)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }.start()
    }

    private fun saveCurrent() {
        val bitmap = generatedBitmap ?: return
        val name = "MeterPod_QR_${System.currentTimeMillis()}"
        val ok = QrFileSaver.saveToGallery(requireContext(), bitmap, name)
        Toast.makeText(
            requireContext(),
            if (ok) "Saved to Pictures/MeterPod" else "Couldn't save the image.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun shareCurrent() {
        val bitmap = generatedBitmap ?: return
        val name = "MeterPod_QR_${System.currentTimeMillis()}"
        val intent = QrFileSaver.shareIntent(requireContext(), bitmap, name)
        startActivity(android.content.Intent.createChooser(intent, "Share QR code"))
    }

    // ---------------------------------------------------------------------
    // Style controls (colors, dot shape, error correction, logo size)
    // ---------------------------------------------------------------------

    private fun setupStyleControls() {
        darkColorSwatch.setBackgroundColor(darkColor)
        lightColorSwatch.setBackgroundColor(lightColor)
        darkColorSwatch.setOnClickListener { pickColor(darkColor) { color -> darkColor = color; darkColorSwatch.setBackgroundColor(color) } }
        lightColorSwatch.setOnClickListener { pickColor(lightColor) { color -> lightColor = color; lightColorSwatch.setBackgroundColor(color) } }

        dotShapeSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, listOf("Square", "Rounded", "Circle"))
        dotShapeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                dotShape = when (position) {
                    1 -> QrDotShape.ROUNDED
                    2 -> QrDotShape.CIRCLE
                    else -> QrDotShape.SQUARE
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val ecLabels = QrErrorCorrection.values().map { it.label }
        errorCorrectionSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, ecLabels)
        errorCorrectionSpinner.setSelection(QrErrorCorrection.values().indexOf(QrErrorCorrection.M))
        errorCorrectionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                errorCorrection = QrErrorCorrection.values()[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun pickColor(current: Int, onPicked: (Int) -> Unit) {
        val context = requireContext()
        val picker = HsvPickerView(context).apply { setColor(current) }
        val hexText = TextView(context).apply {
            text = ColorUtils.toHex(current)
            setTextColor(Color.parseColor("#9AA5B1"))
            textSize = 13f
            setPadding(0, dp(12), 0, 0)
        }
        picker.onColorChanged = { color -> hexText.text = ColorUtils.toHex(color) }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(4))
            addView(picker, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(220)))
            addView(hexText)
        }

        AlertDialog.Builder(context)
            .setTitle("Pick a color")
            .setView(container)
            .setPositiveButton("Use color") { _, _ -> onPicked(picker.currentColor()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------------------------------------------------------------------
    // Field builders
    // ---------------------------------------------------------------------

    private fun addField(key: String, label: String, hint: String, inputType: Int = InputType.TYPE_CLASS_TEXT) {
        addLabel(label)
        val editText = EditText(requireContext()).apply {
            this.hint = hint
            this.inputType = inputType
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#6B7480"))
            setBackgroundColor(Color.parseColor("#1B2127"))
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        fieldViews[key] = editText
        dynamicFieldsContainer.addView(editText, matchWidthWrapHeight().apply { topMargin = dp(4); bottomMargin = dp(10) })
    }

    private fun addMultilineField(key: String, label: String, hint: String) {
        addLabel(label)
        val editText = EditText(requireContext()).apply {
            this.hint = hint
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#6B7480"))
            setBackgroundColor(Color.parseColor("#1B2127"))
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        fieldViews[key] = editText
        dynamicFieldsContainer.addView(editText, matchWidthWrapHeight().apply { topMargin = dp(4); bottomMargin = dp(10) })
    }

    private fun addSpinnerField(key: String, label: String, options: List<String>) {
        addLabel(label)
        val spinner = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, options)
            setBackgroundColor(Color.parseColor("#1B2127"))
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        fieldViews[key] = spinner
        dynamicFieldsContainer.addView(spinner, matchWidthWrapHeight().apply { topMargin = dp(4); bottomMargin = dp(10) })
    }

    private fun addCheckboxField(key: String, label: String) {
        val checkBox = CheckBox(requireContext()).apply {
            text = label
            setTextColor(Color.parseColor("#9AA5B1"))
        }
        fieldViews[key] = checkBox
        dynamicFieldsContainer.addView(checkBox, matchWidthWrapHeight().apply { bottomMargin = dp(10) })
    }

    private fun addDateTimeField(key: String, label: String, onPicked: (Date) -> Unit) {
        addLabel(label)
        val format = SimpleDateFormat("EEE, MMM d yyyy · HH:mm", Locale.getDefault())
        val button = Button(requireContext()).apply {
            text = "Pick date & time"
        }
        button.setOnClickListener {
            val now = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, year, month, day ->
                TimePickerDialog(requireContext(), { _, hour, minute ->
                    val calendar = Calendar.getInstance().apply {
                        set(year, month, day, hour, minute, 0)
                    }
                    val date = calendar.time
                    onPicked(date)
                    button.text = format.format(date)
                }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
            }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show()
        }
        fieldViews[key] = button
        dynamicFieldsContainer.addView(button, matchWidthWrapHeight().apply { topMargin = dp(4); bottomMargin = dp(10) })
    }

    private fun addLabel(text: String) {
        val textView = TextView(requireContext()).apply {
            this.text = text
            setTextColor(Color.parseColor("#9AA5B1"))
            textSize = 13f
        }
        dynamicFieldsContainer.addView(textView, matchWidthWrapHeight().apply { topMargin = dp(6) })
    }

    private fun matchWidthWrapHeight() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int {
        val density = resources.displayMetrics.density
        return (value * density).toInt()
    }
}
