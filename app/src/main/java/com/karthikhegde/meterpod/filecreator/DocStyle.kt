package com.karthikhegde.meterpod.filecreator

import android.graphics.Typeface

/** One selectable font: the name Word uses (DOCX) and the closest built-in Android typeface (PDF). */
class DocFont(val label: String, val wordName: String, val androidFamily: String)

object DocFonts {
    val all = listOf(
        DocFont("Arial (sans-serif)", "Arial", "sans-serif"),
        DocFont("Times New Roman (serif)", "Times New Roman", "serif"),
        DocFont("Courier New (monospace)", "Courier New", "monospace"),
        DocFont("Calibri (light sans)", "Calibri", "sans-serif-light"),
        DocFont("Verdana (medium sans)", "Verdana", "sans-serif-medium"),
        DocFont("Cambria (serif)", "Cambria", "serif"),
        DocFont("Georgia (serif)", "Georgia", "serif"),
        DocFont("Trebuchet (condensed)", "Trebuchet MS", "sans-serif-condensed"),
        DocFont("Comic Sans (casual)", "Comic Sans MS", "casual"),
        DocFont("Script (cursive)", "Segoe Script", "cursive")
    )
    val sizes = listOf(8, 9, 10, 11, 12, 14, 16, 18, 20, 24, 28, 32, 36, 48, 72)
}

/** Whole-document text style for PDF and Word files. */
data class DocStyle(val fontIndex: Int = 0, val size: Int = 12, val bold: Boolean = false, val italic: Boolean = false) {
    val font get() = DocFonts.all[fontIndex.coerceIn(0, DocFonts.all.lastIndex)]
    fun typeface(extraBold: Boolean = false): Typeface {
        val b = bold || extraBold
        val style = if (b && italic) Typeface.BOLD_ITALIC else if (b) Typeface.BOLD else if (italic) Typeface.ITALIC else Typeface.NORMAL
        return Typeface.create(font.androidFamily, style)
    }
}

/** A line of the document after reading its size markers. */
class StyledLine(val text: String, val size: Float, val bold: Boolean)

/**
 * Reads the per-line size markers: "{24} text" sets that line to 24 pt; "# ", "## ", "### " make headings
 * (2x, 1.6x, 1.3x the base size, bold). Everything else uses the base size.
 */
fun parseStyledLine(raw: String, baseSize: Int): StyledLine {
    Regex("^\\{(\\d{1,3})\\}\\s?(.*)$").find(raw)?.let {
        return StyledLine(it.groupValues[2], it.groupValues[1].toInt().coerceIn(4, 200).toFloat(), false)
    }
    Regex("^(#{1,3})\\s+(.*)$").find(raw)?.let {
        val mult = when (it.groupValues[1].length) { 1 -> 2.0f; 2 -> 1.6f; else -> 1.3f }
        return StyledLine(it.groupValues[2], baseSize * mult, true)
    }
    return StyledLine(raw, baseSize.toFloat(), false)
}
