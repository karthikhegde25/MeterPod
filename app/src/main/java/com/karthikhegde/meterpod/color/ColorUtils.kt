package com.karthikhegde.meterpod.color

import android.graphics.Color

object ColorUtils {

    fun toHex(argb: Int): String {
        return String.format("#%02X%02X%02X", Color.red(argb), Color.green(argb), Color.blue(argb))
    }

    /**
     * Finds the closest named color by simple Euclidean distance in RGB space.
     * Returns the name plus whether it was an exact match, so the caller can
     * show "Tomato" for an exact hit vs "~ Tomato" for a nearest guess.
     */
    fun nearestName(argb: Int): Pair<String, Boolean> {
        val r = Color.red(argb)
        val g = Color.green(argb)
        val b = Color.blue(argb)

        var bestName = NamedColors.all.first().name
        var bestDistance = Int.MAX_VALUE

        for (entry in NamedColors.all) {
            val dr = r - Color.red(entry.argb)
            val dg = g - Color.green(entry.argb)
            val db = b - Color.blue(entry.argb)
            val distance = dr * dr + dg * dg + db * db
            if (distance < bestDistance) {
                bestDistance = distance
                bestName = entry.name
            }
        }

        return bestName to (bestDistance == 0)
    }
}
