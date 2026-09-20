package com.karthikhegde.meterpod.qr

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Builds the raw string payload encoded into the QR code for each content
 * type MeterPod's QR Generator supports. The nine content types and their
 * wire formats (WIFI:, SMSTO:, geo:, MATMSG:, tel:, VCARD, VEVENT) mirror
 * what Image Toolbox's QR generator (github.com/T8RIN/ImageToolbox) reads
 * back in its own scanner, so codes made here scan correctly there and in
 * any standard QR reader (Google Lens, ZXing-based scanners, etc).
 */
object QrPayloadBuilder {

    enum class WifiEncryption { OPEN, WPA, WEP }

    /** Plain free-form text - encoded exactly as typed. */
    fun plainText(text: String): String = text

    /** A website URL; adds an https:// scheme if the user left it off. */
    fun url(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        return if (trimmed.contains("://")) trimmed else "https://$trimmed"
    }

    fun wifi(ssid: String, password: String, encryption: WifiEncryption, hidden: Boolean): String {
        return buildString {
            append("WIFI:")
            append("S:").append(escape(ssid)).append(";")
            if (encryption != WifiEncryption.OPEN) {
                append("T:").append(encryption.name).append(";")
                append("P:").append(escape(password)).append(";")
            }
            if (hidden) append("H:true;")
            append(";")
        }
    }

    fun sms(phoneNumber: String, message: String): String = "SMSTO:$phoneNumber:$message"

    fun phone(number: String): String = "tel:$number"

    fun geo(latitude: String, longitude: String): String = "geo:$latitude,$longitude"

    fun email(address: String, subject: String, body: String): String {
        return "MATMSG:TO:${escape(address)};SUB:${escape(subject)};BODY:${escape(body)};;"
    }

    /**
     * A minimal but complete vCard 3.0 - covers name, org, title, phone(s),
     * email(s), address(es), website(s) and a note, same fields Image
     * Toolbox's Contact QR type carries.
     */
    fun contact(
        firstName: String,
        lastName: String,
        organization: String,
        title: String,
        phones: List<String>,
        emails: List<String>,
        addresses: List<String>,
        urls: List<String>,
        note: String
    ): String {
        val formattedName = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { organization }
        return buildString {
            append("BEGIN:VCARD\n")
            append("VERSION:3.0\n")
            append("N:").append(escapeVCard(lastName)).append(";").append(escapeVCard(firstName)).append(";;;\n")
            append("FN:").append(escapeVCard(formattedName)).append("\n")
            if (organization.isNotBlank()) append("ORG:").append(escapeVCard(organization)).append("\n")
            if (title.isNotBlank()) append("TITLE:").append(escapeVCard(title)).append("\n")
            phones.filter { it.isNotBlank() }.forEach { append("TEL;TYPE=CELL:").append(it.trim()).append("\n") }
            emails.filter { it.isNotBlank() }.forEach { append("EMAIL;TYPE=INTERNET:").append(it.trim()).append("\n") }
            addresses.filter { it.isNotBlank() }.forEach { append("ADR;TYPE=HOME:;;").append(escapeVCard(it.trim())).append(";;;;\n") }
            urls.filter { it.isNotBlank() }.forEach { append("URL:").append(it.trim()).append("\n") }
            if (note.isNotBlank()) append("NOTE:").append(escapeVCard(note)).append("\n")
            append("END:VCARD")
        }
    }

    /** A calendar event (VEVENT) - start/end are required, everything else is optional. */
    fun calendarEvent(
        summary: String,
        description: String,
        location: String,
        organizer: String,
        start: Date?,
        end: Date?
    ): String {
        val format = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return buildString {
            append("BEGIN:VEVENT\n")
            if (summary.isNotBlank()) append("SUMMARY:").append(summary).append("\n")
            if (description.isNotBlank()) append("DESCRIPTION:").append(description).append("\n")
            if (location.isNotBlank()) append("LOCATION:").append(location).append("\n")
            if (organizer.isNotBlank()) append("ORGANIZER:").append(organizer).append("\n")
            append("DTSTART:").append(format.format(start ?: Date())).append("\n")
            append("DTEND:").append(format.format(end ?: start ?: Date())).append("\n")
            append("END:VEVENT")
        }
    }

    /** Escaping required by the WIFI:/MATMSG: MeCard-style formats: \\, ; , and : are special. */
    private fun escape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace(":", "\\:")
    }

    /** vCard only needs backslash, comma, semicolon and newline escaped. */
    private fun escapeVCard(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\n", "\\n")
    }
}
