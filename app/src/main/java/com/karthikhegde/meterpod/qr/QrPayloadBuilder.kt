package com.karthikhegde.meterpod.qr

/**
 * Builds the raw text payload that gets QR-encoded for each supported
 * content type. The formats for Wi-Fi / phone / SMS / email / geo /
 * contact / calendar follow the same conventions used by most QR
 * scanners and generators (including the Image Toolbox app, whose QR
 * feature this tool's content types are adapted from).
 */
enum class QrContentType(val label: String) {
    TEXT("Plain Text"),
    URL("Website URL"),
    WIFI("Wi‑Fi Network"),
    PHONE("Phone Number"),
    SMS("SMS Message"),
    EMAIL("Email"),
    GEO("Geo Location"),
    CONTACT("Contact (vCard)"),
    EVENT("Calendar Event")
}

object QrPayloadBuilder {

    fun plainText(text: String): String = text

    fun url(url: String): String {
        return if (url.startsWith("http://", true) || url.startsWith("https://", true)) {
            url
        } else {
            "https://$url"
        }
    }

    fun wifi(ssid: String, password: String, encryption: String, hidden: Boolean): String {
        val esc = { s: String -> s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"") }
        return "WIFI:T:$encryption;S:${esc(ssid)};P:${esc(password)};H:${if (hidden) "true" else "false"};;"
    }

    fun phone(number: String): String = "tel:$number"

    fun sms(number: String, message: String): String = "SMSTO:$number:$message"

    fun email(address: String, subject: String, body: String): String {
        return "MATMSG:TO:$address;SUB:$subject;BODY:$body;;"
    }

    fun geo(latitude: String, longitude: String): String = "geo:$latitude,$longitude"

    fun contact(
        name: String,
        phone: String,
        email: String,
        organization: String,
        url: String
    ): String {
        return buildString {
            append("BEGIN:VCARD\n")
            append("VERSION:3.0\n")
            append("N:$name\n")
            append("FN:$name\n")
            if (organization.isNotBlank()) append("ORG:$organization\n")
            if (phone.isNotBlank()) append("TEL:$phone\n")
            if (email.isNotBlank()) append("EMAIL:$email\n")
            if (url.isNotBlank()) append("URL:$url\n")
            append("END:VCARD")
        }
    }

    fun event(
        title: String,
        location: String,
        startDateTime: String,
        endDateTime: String,
        description: String
    ): String {
        return buildString {
            append("BEGIN:VEVENT\n")
            append("SUMMARY:$title\n")
            if (location.isNotBlank()) append("LOCATION:$location\n")
            if (startDateTime.isNotBlank()) append("DTSTART:$startDateTime\n")
            if (endDateTime.isNotBlank()) append("DTEND:$endDateTime\n")
            if (description.isNotBlank()) append("DESCRIPTION:$description\n")
            append("END:VEVENT")
        }
    }
}
