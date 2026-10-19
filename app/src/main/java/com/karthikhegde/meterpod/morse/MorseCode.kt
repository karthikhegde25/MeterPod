package com.karthikhegde.meterpod.morse

/** International Morse code: text <-> dots and dashes, plus the on/off timing used for sound, vibration and flash. */
object MorseCode {
    private val table: Map<Char, String> = mapOf(
        'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".", 'F' to "..-.", 'G' to "--.",
        'H' to "....", 'I' to "..", 'J' to ".---", 'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.",
        'O' to "---", 'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-", 'U' to "..-",
        'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--", 'Z' to "--..",
        '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-", '5' to ".....",
        '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----.",
        '.' to ".-.-.-", ',' to "--..--", '?' to "..--..", '\'' to ".----.", '!' to "-.-.--", '/' to "-..-.",
        '(' to "-.--.", ')' to "-.--.-", '&' to ".-...", ':' to "---...", ';' to "-.-.-.", '=' to "-...-",
        '+' to ".-.-.", '-' to "-....-", '_' to "..--.-", '"' to ".-..-.", '$' to "...-..-", '@' to ".--.-."
    )
    private val reverse: Map<String, Char> = table.entries.associate { it.value to it.key }

    /** Letters are separated by a space and words by " / ". Characters with no Morse equivalent are skipped. */
    fun encode(text: String): String =
        text.uppercase().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            .map { w -> w.mapNotNull { table[it] }.joinToString(" ") }
            .filter { it.isNotEmpty() }
            .joinToString(" / ")

    /** Accepts . and - (also · • – − _), letters split by spaces, words by / or 2+ spaces. Unknown groups become '?'. */
    fun decode(morse: String): String {
        val norm = morse.replace('·', '.').replace('•', '.').replace('–', '-').replace('−', '-').replace('—', '-').replace('_', '-')
            .replace(Regex("[^.\\-/ \\n]"), "")
        return norm.trim().split(Regex("\\s*/\\s*|\\s{3,}|\\n+")).joinToString(" ") { word ->
            word.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString("") { (reverse[it] ?: '?').toString() }
        }.trim()
    }

    /**
     * Alternating on/off durations in ms, starting with "on": dot = 1 unit, dash = 3, gap inside a letter = 1,
     * between letters = 3, between words = 7 (standard Morse timing).
     */
    fun timings(morse: String, unitMs: Long): LongArray {
        val out = ArrayList<Long>()
        fun gap(units: Int) {
            if (out.isEmpty()) return
            if (out.size % 2 == 1) out.add(units * unitMs) else out[out.size - 1] = maxOf(out.last(), units * unitMs)
        }
        val words = morse.trim().split(Regex("\\s*/\\s*"))
        for ((wi, w) in words.withIndex()) {
            if (wi > 0) gap(7)
            val letters = w.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            for ((li, l) in letters.withIndex()) {
                if (li > 0) gap(3)
                for ((si, ch) in l.withIndex()) {
                    if (ch != '.' && ch != '-') continue
                    if (si > 0) gap(1)
                    out.add(if (ch == '.') unitMs else 3 * unitMs)
                }
            }
        }
        if (out.size % 2 == 0 && out.isNotEmpty()) out.removeAt(out.size - 1)
        return out.toLongArray()
    }
}
