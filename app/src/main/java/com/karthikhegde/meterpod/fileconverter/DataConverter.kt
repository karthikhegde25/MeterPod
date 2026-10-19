package com.karthikhegde.meterpod.fileconverter

/**
 * Text / data format converter. Everything is parsed into a small generic tree
 * (Map<String, Any?>, List<Any?>, String, Long, Double, Boolean, null) and written out again in
 * the requested format. Pure Kotlin/JVM: no Android classes and no external libraries.
 */
enum class DataFormat(val label: String, val ext: String, val mime: String, val canRead: Boolean, val canWrite: Boolean) {
    JSON("JSON", "json", "application/json", true, true),
    JSON_MIN("JSON (minified)", "json", "application/json", false, true),
    CSV("CSV", "csv", "text/csv", true, true),
    TSV("TSV (tab separated)", "tsv", "text/tab-separated-values", true, true),
    XML("XML", "xml", "application/xml", true, true),
    YAML("YAML", "yaml", "application/x-yaml", true, true),
    TOML("TOML", "toml", "application/toml", false, true),
    PROPERTIES("Properties", "properties", "text/x-java-properties", true, true),
    INI("INI", "ini", "text/plain", true, true),
    MARKDOWN("Markdown table", "md", "text/markdown", true, true),
    HTML("HTML table", "html", "text/html", true, true),
    SQL("SQL (CREATE + INSERT)", "sql", "application/sql", false, true),
    TXT("Plain text", "txt", "text/plain", true, true);
}

object DataConverter {

    // ===================================================================== public API

    fun detect(text: String, fileName: String? = null): DataFormat {
        val ext = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        when (ext) {
            "json", "jsonc", "json5" -> return DataFormat.JSON
            "csv" -> return DataFormat.CSV
            "tsv", "tab" -> return DataFormat.TSV
            "xml", "xsd", "svg", "plist", "rss", "atom" -> return DataFormat.XML
            "yaml", "yml" -> return DataFormat.YAML
            "properties" -> return DataFormat.PROPERTIES
            "ini", "cfg", "conf" -> return DataFormat.INI
            "html", "htm" -> return DataFormat.HTML
            "md", "markdown" -> return DataFormat.MARKDOWN
        }
        val t = text.trim()
        if (t.isEmpty()) return DataFormat.TXT
        if (t.startsWith("{") || t.startsWith("[")) {
            try { JsonReader(t).readDocument(); return DataFormat.JSON } catch (_: Exception) { }
        }
        if (t.startsWith("<")) return if (t.contains("<table", true)) DataFormat.HTML else DataFormat.XML
        val lines = t.lines().filter { it.isNotBlank() }
        if (lines.size >= 2 && lines.all { it.contains('|') } && lines.getOrNull(1)?.matches(Regex("[|\\s:\\-]+")) == true) return DataFormat.MARKDOWN
        if (lines.first().contains('\t')) return DataFormat.TSV
        if (lines.size >= 2 && lines.first().contains(',') && !lines.first().contains(": ")) return DataFormat.CSV
        if (lines.first().trim().startsWith("[") && lines.first().trim().endsWith("]")) return DataFormat.INI
        if (lines.all { it.trim().startsWith("#") || it.contains(": ") || it.trim().startsWith("- ") || it.trim().endsWith(":") || it.startsWith(" ") }) return DataFormat.YAML
        if (lines.all { it.trim().startsWith("#") || it.contains('=') }) return DataFormat.PROPERTIES
        return DataFormat.TXT
    }

    fun parse(text: String, from: DataFormat): Any? {
        val s = text.removePrefix("\uFEFF")
        return when (from) {
            DataFormat.JSON, DataFormat.JSON_MIN -> JsonReader(s).readDocument()
            DataFormat.CSV -> tableToTree(parseDelimited(s, ','))
            DataFormat.TSV -> tableToTree(parseDelimited(s, '\t'))
            DataFormat.XML -> XmlReader(s).readDocument()
            DataFormat.YAML -> YamlReader(s).readDocument()
            DataFormat.PROPERTIES -> parseProperties(s)
            DataFormat.INI -> parseIni(s)
            DataFormat.MARKDOWN -> parseMarkdownTable(s)
            DataFormat.HTML -> parseHtmlTable(s)
            DataFormat.TXT -> s.replace("\r\n", "\n").replace('\r', '\n').removeSuffix("\n").let { if (it.isEmpty()) emptyList<Any?>() else it.split('\n') }
            DataFormat.TOML, DataFormat.SQL -> throw IllegalArgumentException("${from.label} can only be created, not read")
        }
    }

    fun write(tree: Any?, to: DataFormat): String = when (to) {
        DataFormat.JSON -> toJson(tree, true)
        DataFormat.JSON_MIN -> toJson(tree, false)
        DataFormat.CSV -> writeDelimited(toTable(tree), ',')
        DataFormat.TSV -> writeDelimited(toTable(tree), '\t')
        DataFormat.XML -> toXml(tree)
        DataFormat.YAML -> toYaml(tree)
        DataFormat.TOML -> toToml(tree)
        DataFormat.PROPERTIES -> toProperties(tree)
        DataFormat.INI -> toIni(tree)
        DataFormat.MARKDOWN -> toMarkdown(toTable(tree))
        DataFormat.HTML -> toHtml(toTable(tree))
        DataFormat.SQL -> toSql(toTable(tree))
        DataFormat.TXT -> toTxt(tree)
    }

    fun convert(text: String, from: DataFormat?, to: DataFormat, fileName: String? = null): String {
        val src = from ?: detect(text, fileName)
        require(text.isNotBlank()) { "Enter or open some text to convert" }
        return write(parse(text, src), to)
    }

    // ===================================================================== scalars

    private val numberRe = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")

    /** Turns "42" -> 42, "3.5" -> 3.5, "true" -> true; anything else stays a string. */
    fun infer(s: String): Any {
        val t = s.trim()
        if (t == "true") return true
        if (t == "false") return false
        if (numberRe.matches(t)) {
            if (!t.contains('.') && !t.contains('e') && !t.contains('E') && t.length <= 18) return t.toLong()
            val d = t.toDoubleOrNull()
            if (d != null && d.isFinite()) return d
        }
        return s
    }

    private fun numStr(n: Number): String {
        if (n is Double) {
            if (n.isNaN() || n.isInfinite()) return "null"
            if (n == Math.floor(n) && Math.abs(n) < 1e15) return n.toLong().toString()
            return n.toString()
        }
        return n.toString()
    }

    private fun scalarStr(v: Any?): String = when (v) {
        null -> ""
        is Number -> numStr(v)
        else -> v.toString()
    }

    private fun isContainer(v: Any?) = v is Map<*, *> || v is List<*>

    // ===================================================================== JSON

    private class JsonReader(private val s: String) {
        private var i = 0
        fun readDocument(): Any? {
            ws()
            if (i >= s.length) fail("empty input")
            val v = value()
            ws()
            if (i < s.length) fail("unexpected '${s[i]}'")
            return v
        }
        private fun fail(msg: String): Nothing = throw IllegalArgumentException("Invalid JSON at position $i: $msg")
        private fun ws() {
            while (i < s.length) {
                val c = s[i]
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') i++
                else if (c == '/' && i + 1 < s.length && s[i + 1] == '/') { while (i < s.length && s[i] != '\n') i++ }
                else if (c == '/' && i + 1 < s.length && s[i + 1] == '*') { val e = s.indexOf("*/", i + 2); i = if (e < 0) s.length else e + 2 }
                else break
            }
        }
        private fun value(): Any? {
            ws()
            if (i >= s.length) fail("unexpected end")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else fail("unexpected '$c'")
            }
        }
        private fun lit(w: String, v: Any?): Any? {
            if (!s.startsWith(w, i)) fail("unexpected token")
            i += w.length; return v
        }
        private fun num(): Any {
            val st = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            val t = s.substring(st, i)
            if (!t.contains('.') && !t.contains('e') && !t.contains('E')) t.toLongOrNull()?.let { return it }
            return t.toDoubleOrNull() ?: fail("bad number '$t'")
        }
        private fun str(): String {
            i++
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) fail("unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) fail("bad escape")
                        when (val e = s[i++]) {
                            'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                            '/' -> sb.append('/'); '\\' -> sb.append('\\'); '"' -> sb.append('"')
                            'u' -> {
                                if (i + 4 > s.length) fail("bad \\u escape")
                                sb.append(s.substring(i, i + 4).toIntOrNull(16)?.toChar() ?: fail("bad \\u escape")); i += 4
                            }
                            else -> fail("bad escape \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }
        private fun arr(): List<Any?> {
            i++
            val out = ArrayList<Any?>()
            ws()
            if (i < s.length && s[i] == ']') { i++; return out }
            while (true) {
                out.add(value()); ws()
                if (i >= s.length) fail("unterminated array")
                if (s[i] == ',') { i++; ws(); if (i < s.length && s[i] == ']') { i++; return out }; continue }
                if (s[i] == ']') { i++; return out }
                fail("expected ',' or ']'")
            }
        }
        private fun obj(): Map<String, Any?> {
            i++
            val out = LinkedHashMap<String, Any?>()
            ws()
            if (i < s.length && s[i] == '}') { i++; return out }
            while (true) {
                ws()
                if (i >= s.length || s[i] != '"') fail("expected a quoted key")
                val k = str(); ws()
                if (i >= s.length || s[i] != ':') fail("expected ':'")
                i++
                out[k] = value(); ws()
                if (i >= s.length) fail("unterminated object")
                if (s[i] == ',') { i++; ws(); if (i < s.length && s[i] == '}') { i++; return out }; continue }
                if (s[i] == '}') { i++; return out }
                fail("expected ',' or '}'")
            }
        }
    }

    private fun jsonStr(s: String): String {
        val sb = StringBuilder(s.length + 2).append('"')
        for (c in s) when {
            c == '"' -> sb.append("\\\"")
            c == '\\' -> sb.append("\\\\")
            c == '\n' -> sb.append("\\n")
            c == '\r' -> sb.append("\\r")
            c == '\t' -> sb.append("\\t")
            c.code < 0x20 -> sb.append(String.format("\\u%04x", c.code))
            else -> sb.append(c)
        }
        return sb.append('"').toString()
    }

    fun toJson(v: Any?, pretty: Boolean): String {
        val sb = StringBuilder()
        fun w(x: Any?, ind: Int) {
            when (x) {
                null -> sb.append("null")
                is Boolean -> sb.append(x)
                is Number -> sb.append(numStr(x))
                is Map<*, *> -> {
                    if (x.isEmpty()) { sb.append("{}"); return }
                    sb.append('{')
                    var first = true
                    for ((k, y) in x) {
                        if (!first) sb.append(',')
                        first = false
                        if (pretty) sb.append('\n').append("  ".repeat(ind + 1))
                        sb.append(jsonStr(k.toString())).append(if (pretty) ": " else ":")
                        w(y, ind + 1)
                    }
                    if (pretty) sb.append('\n').append("  ".repeat(ind))
                    sb.append('}')
                }
                is List<*> -> {
                    if (x.isEmpty()) { sb.append("[]"); return }
                    sb.append('[')
                    var first = true
                    for (y in x) {
                        if (!first) sb.append(',')
                        first = false
                        if (pretty) sb.append('\n').append("  ".repeat(ind + 1))
                        w(y, ind + 1)
                    }
                    if (pretty) sb.append('\n').append("  ".repeat(ind))
                    sb.append(']')
                }
                else -> sb.append(jsonStr(x.toString()))
            }
        }
        w(v, 0)
        return sb.append('\n').toString().let { if (pretty) it else it.trimEnd('\n') }
    }

    // ===================================================================== tables

    class Table(val headers: List<String>, val rows: List<List<String>>)

    /** Splits delimited text honouring quotes. */
    private fun parseDelimited(text: String, delim: Char): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQ = false; var started = false
        val s = text.replace("\r\n", "\n").replace('\r', '\n')
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (inQ) {
                if (c == '"') { if (i + 1 < s.length && s[i + 1] == '"') { field.append('"'); i++ } else inQ = false }
                else field.append(c)
            } else when {
                c == '"' -> { inQ = true; started = true }
                c == delim -> { row.add(field.toString()); field.setLength(0); started = false }
                c == '\n' -> {
                    row.add(field.toString()); field.setLength(0); started = false
                    if (!(row.size == 1 && row[0].isEmpty())) rows.add(row)
                    row = ArrayList()
                }
                else -> { field.append(c); started = true }
            }
            i++
        }
        if (inQ) throw IllegalArgumentException("A quoted field is never closed")
        if (field.isNotEmpty() || started || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows
    }

    private fun uniqueHeaders(raw: List<String>): List<String> {
        val seen = HashSet<String>()
        return raw.mapIndexed { idx, h ->
            var name = h.trim().ifEmpty { "column ${idx + 1}" }
            var n = 2
            val base = name
            while (!seen.add(name)) name = "${base}_${n++}"
            name
        }
    }

    private fun tableToTree(rows: List<List<String>>): Any? {
        if (rows.isEmpty()) return emptyList<Any?>()
        val headers = uniqueHeaders(rows[0])
        val out = ArrayList<Any?>()
        for (r in rows.drop(1)) {
            val m = LinkedHashMap<String, Any?>()
            for (c in headers.indices) m[headers[c]] = infer(r.getOrElse(c) { "" })
            out.add(m)
        }
        return out
    }

    /** Flattens nested values to dotted keys: {a:{b:1},c:[2,3]} -> a.b=1, c.0=2, c.1=3 */
    private fun flatten(v: Any?, prefix: String, out: LinkedHashMap<String, String>) {
        when (v) {
            is Map<*, *> -> if (v.isEmpty() && prefix.isNotEmpty()) out[prefix] = "" else for ((k, x) in v) flatten(x, if (prefix.isEmpty()) k.toString() else "$prefix.$k", out)
            is List<*> -> if (v.isEmpty() && prefix.isNotEmpty()) out[prefix] = "" else v.forEachIndexed { idx, x -> flatten(x, if (prefix.isEmpty()) idx.toString() else "$prefix.$idx", out) }
            else -> out[if (prefix.isEmpty()) "value" else prefix] = scalarStr(v)
        }
    }

    private fun tableFromRecords(records: List<Any?>): Table {
        val flat = records.map { r -> LinkedHashMap<String, String>().also { flatten(r, "", it) } }
        val headers = LinkedHashSet<String>()
        flat.forEach { headers.addAll(it.keys) }
        val h = headers.toList()
        return Table(h, flat.map { m -> h.map { m[it] ?: "" } })
    }

    fun toTable(tree: Any?): Table {
        var t = tree
        // Unwrap {"root": {"item": [ ... ]}} style wrappers.
        while (t is Map<*, *> && t.size == 1 && isContainer(t.values.first())) t = t.values.first()
        return when {
            t is List<*> && t.isEmpty() -> Table(emptyList(), emptyList())
            t is List<*> && t.all { it is Map<*, *> } -> tableFromRecords(t)
            t is List<*> && t.all { it is List<*> } -> {
                val rows = t.map { r -> (r as List<*>).map { scalarStr(it) } }
                val w = rows.maxOf { it.size }
                Table((1..w).map { "column $it" }, rows.map { r -> r + List(w - r.size) { "" } })
            }
            t is List<*> -> Table(listOf("value"), t.map { listOf(if (isContainer(it)) toJson(it, false) else scalarStr(it)) })
            t is Map<*, *> -> {
                val flat = LinkedHashMap<String, String>().also { flatten(t, "", it) }
                Table(listOf("key", "value"), flat.map { listOf(it.key, it.value) })
            }
            else -> Table(listOf("value"), listOf(listOf(scalarStr(t))))
        }
    }

    private fun writeDelimited(t: Table, d: Char): String {
        fun q(s: String) = if (s.any { it == d || it == '"' || it == '\n' || it == '\r' } || s != s.trim() && s.isNotEmpty())
            "\"" + s.replace("\"", "\"\"") + "\"" else s
        val sb = StringBuilder()
        sb.append(t.headers.joinToString(d.toString()) { q(it) }).append('\n')
        for (r in t.rows) sb.append(r.joinToString(d.toString()) { q(it) }).append('\n')
        return sb.toString()
    }

    private fun toMarkdown(t: Table): String {
        fun c(s: String) = s.replace("|", "\\|").replace("\r\n", "<br>").replace("\n", "<br>")
        if (t.headers.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("| ").append(t.headers.joinToString(" | ") { c(it) }).append(" |\n")
        sb.append("| ").append(t.headers.joinToString(" | ") { "---" }).append(" |\n")
        for (r in t.rows) sb.append("| ").append(r.joinToString(" | ") { c(it) }).append(" |\n")
        return sb.toString()
    }

    private fun parseMarkdownTable(text: String): Any? {
        val rows = ArrayList<List<String>>()
        for (raw in text.replace("\r\n", "\n").split('\n')) {
            val line = raw.trim()
            if (!line.contains('|')) continue
            var body = line
            if (body.startsWith("|")) body = body.substring(1)
            if (body.endsWith("|") && !body.endsWith("\\|")) body = body.dropLast(1)
            val cells = ArrayList<String>()
            val cur = StringBuilder()
            var i = 0
            while (i < body.length) {
                if (body[i] == '\\' && i + 1 < body.length && body[i + 1] == '|') { cur.append('|'); i += 2; continue }
                if (body[i] == '|') { cells.add(cur.toString().trim()); cur.setLength(0) } else cur.append(body[i])
                i++
            }
            cells.add(cur.toString().trim())
            if (cells.all { it.matches(Regex(":?-{1,}:?")) }) continue
            rows.add(cells.map { it.replace("<br>", "\n") })
        }
        require(rows.isNotEmpty()) { "No Markdown table found (rows need | separators)" }
        return tableToTree(rows)
    }

    private fun htmlEsc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun toHtml(t: Table): String {
        val sb = StringBuilder("<table>\n  <thead>\n    <tr>")
        t.headers.forEach { sb.append("<th>").append(htmlEsc(it)).append("</th>") }
        sb.append("</tr>\n  </thead>\n  <tbody>\n")
        for (r in t.rows) {
            sb.append("    <tr>")
            r.forEach { sb.append("<td>").append(htmlEsc(it)).append("</td>") }
            sb.append("</tr>\n")
        }
        return sb.append("  </tbody>\n</table>\n").toString()
    }

    private fun decodeEntities(s: String): String {
        if (!s.contains('&')) return s
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            if (s[i] == '&') {
                val e = s.indexOf(';', i)
                if (e in (i + 2)..(i + 10)) {
                    val name = s.substring(i + 1, e)
                    val rep: String? = when {
                        name == "lt" -> "<"; name == "gt" -> ">"; name == "amp" -> "&"; name == "quot" -> "\""
                        name == "apos" -> "'"; name == "nbsp" -> " "
                        name.startsWith("#x") || name.startsWith("#X") -> name.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
                        name.startsWith("#") -> name.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) }
                        else -> null
                    }
                    if (rep != null) { sb.append(rep); i = e + 1; continue }
                }
            }
            sb.append(s[i++])
        }
        return sb.toString()
    }

    private fun parseHtmlTable(text: String): Any? {
        val rowRe = Regex("<tr[^>]*>(.*?)</tr>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val cellRe = Regex("<t[hd][^>]*>(.*?)</t[hd]>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val tagRe = Regex("<[^>]+>")
        val rows = rowRe.findAll(text).map { m ->
            cellRe.findAll(m.groupValues[1]).map { decodeEntities(it.groupValues[1].replace(tagRe, "")).trim() }.toList()
        }.filter { it.isNotEmpty() }.toList()
        require(rows.isNotEmpty()) { "No <table> rows found in the HTML" }
        return tableToTree(rows)
    }

    private fun sqlIdent(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    private fun toSql(t: Table): String {
        require(t.headers.isNotEmpty()) { "Nothing to convert into SQL" }
        val types = t.headers.indices.map { c ->
            val vals = t.rows.map { it.getOrElse(c) { "" } }.filter { it.isNotEmpty() }
            when {
                vals.isNotEmpty() && vals.all { it.toLongOrNull() != null && numberRe.matches(it) } -> "INTEGER"
                vals.isNotEmpty() && vals.all { it.toDoubleOrNull() != null && numberRe.matches(it) } -> "REAL"
                else -> "TEXT"
            }
        }
        val sb = StringBuilder("CREATE TABLE data (\n")
        sb.append(t.headers.indices.joinToString(",\n") { "  ${sqlIdent(t.headers[it])} ${types[it]}" }).append("\n);\n")
        for (r in t.rows) {
            val vals = t.headers.indices.joinToString(", ") { c ->
                val v = r.getOrElse(c) { "" }
                if (types[c] != "TEXT") (if (v.isEmpty()) "NULL" else v) else "'" + v.replace("'", "''") + "'"
            }
            sb.append("INSERT INTO data (").append(t.headers.joinToString(", ") { sqlIdent(it) }).append(") VALUES (").append(vals).append(");\n")
        }
        return sb.toString()
    }

    // ===================================================================== properties / INI

    /** Builds nested maps from dotted keys. Numeric-only children become lists. */
    private fun unflatten(flat: Map<String, Any?>): Any? {
        val root = LinkedHashMap<String, Any?>()
        for ((k, v) in flat) {
            val parts = k.split('.')
            var cur = root
            for (idx in 0 until parts.size - 1) {
                val existing = cur[parts[idx]]
                @Suppress("UNCHECKED_CAST")
                cur = if (existing is MutableMap<*, *>) existing as MutableMap<String, Any?> as LinkedHashMap<String, Any?>
                else LinkedHashMap<String, Any?>().also { cur[parts[idx]] = it }
            }
            cur[parts.last()] = v
        }
        fun fix(x: Any?): Any? {
            if (x is Map<*, *>) {
                val m = LinkedHashMap<String, Any?>()
                for ((k, y) in x) m[k.toString()] = fix(y)
                if (m.isNotEmpty() && m.keys.indices.all { m.containsKey(it.toString()) } && m.size == m.keys.size) return m.keys.sortedBy { it.toInt() }.map { m[it] }
                return m
            }
            return x
        }
        return fix(root)
    }

    private fun unescapeProp(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r'); 'f' -> sb.append('\u000C')
                    'u' -> if (i + 5 < s.length + 0 && s.substring(i + 2, minOf(i + 6, s.length)).length == 4) {
                        sb.append(s.substring(i + 2, i + 6).toIntOrNull(16)?.toChar() ?: 'u'); i += 4
                    } else sb.append('u')
                    else -> sb.append(n)
                }
                i += 2
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }

    private fun parseProperties(text: String): Any? {
        val flat = LinkedHashMap<String, Any?>()
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        var i = 0
        while (i < lines.size) {
            var line = lines[i++].trimStart()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) continue
            while (line.endsWith("\\") && !line.endsWith("\\\\") && i < lines.size) line = line.dropLast(1) + lines[i++].trimStart()
            var sep = -1
            var j = 0
            while (j < line.length) {
                val c = line[j]
                if (c == '\\') { j += 2; continue }
                if (c == '=' || c == ':' || c == ' ' || c == '\t') { sep = j; break }
                j++
            }
            val key: String; var value: String
            if (sep < 0) { key = line; value = "" } else {
                key = line.substring(0, sep)
                var r = sep
                while (r < line.length && (line[r] == ' ' || line[r] == '\t')) r++
                if (r < line.length && (line[r] == '=' || line[r] == ':')) r++
                while (r < line.length && (line[r] == ' ' || line[r] == '\t')) r++
                value = line.substring(r)
            }
            flat[unescapeProp(key)] = infer(unescapeProp(value))
        }
        return unflatten(flat)
    }

    private fun escProp(s: String, key: Boolean): String {
        val sb = StringBuilder()
        for ((idx, c) in s.withIndex()) when {
            c == '\\' -> sb.append("\\\\")
            c == '\n' -> sb.append("\\n")
            c == '\r' -> sb.append("\\r")
            c == '\t' -> sb.append("\\t")
            c == ' ' && (key || idx == 0) -> sb.append("\\ ")
            key && (c == '=' || c == ':' || c == '#' || c == '!') -> sb.append('\\').append(c)
            else -> sb.append(c)
        }
        return sb.toString()
    }

    private fun toProperties(tree: Any?): String {
        val flat = LinkedHashMap<String, String>().also { flatten(tree, "", it) }
        return flat.entries.joinToString("") { "${escProp(it.key, true)}=${escProp(it.value, false)}\n" }
    }

    private fun parseIni(text: String): Any? {
        val root = LinkedHashMap<String, Any?>()
        var cur: LinkedHashMap<String, Any?> = root
        for (raw in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) continue
            if (line.startsWith("[") && line.endsWith("]")) {
                val name = line.substring(1, line.length - 1).trim()
                cur = LinkedHashMap()
                root[name] = cur
                continue
            }
            val eq = line.indexOfFirst { it == '=' || it == ':' }
            if (eq < 0) cur[line] = "" else {
                var v = line.substring(eq + 1).trim()
                if (v.length >= 2 && (v.startsWith("\"") && v.endsWith("\"") || v.startsWith("'") && v.endsWith("'"))) v = v.substring(1, v.length - 1)
                cur[line.substring(0, eq).trim()] = infer(v)
            }
        }
        return root
    }

    private fun toIni(tree: Any?): String {
        val root: Map<*, *> = when (tree) {
            is Map<*, *> -> tree
            is List<*> -> LinkedHashMap<String, Any?>().also { m -> tree.forEachIndexed { n, x -> m[(n + 1).toString()] = x } }
            else -> mapOf("data" to tree)
        }
        val sb = StringBuilder()
        for ((k, v) in root) if (!isContainer(v)) sb.append(k).append('=').append(scalarStr(v)).append('\n')
        for ((k, v) in root) if (isContainer(v)) {
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append('[').append(k).append("]\n")
            val flat = LinkedHashMap<String, String>().also { flatten(v, "", it) }
            for ((fk, fv) in flat) sb.append(fk).append('=').append(fv.replace("\n", "\\n")).append('\n')
        }
        return sb.toString()
    }

    // ===================================================================== XML

    private class XmlReader(private val s: String) {
        private var i = 0
        private fun fail(msg: String): Nothing = throw IllegalArgumentException("Invalid XML at position $i: $msg")

        fun readDocument(): Any? {
            skipMisc()
            if (i >= s.length || s[i] != '<') fail("no root element found")
            val (name, value) = element()
            skipMisc()
            if (i < s.length) fail("extra content after the root element")
            return linkedMapOf<String, Any?>(name to value)
        }

        private fun skipMisc() {
            while (true) {
                while (i < s.length && s[i].isWhitespace()) i++
                when {
                    s.startsWith("<?", i) -> { val e = s.indexOf("?>", i); if (e < 0) fail("unterminated <? ?>"); i = e + 2 }
                    s.startsWith("<!--", i) -> { val e = s.indexOf("-->", i); if (e < 0) fail("unterminated comment"); i = e + 3 }
                    s.startsWith("<!", i) && !s.startsWith("<![CDATA[", i) -> {
                        var depth = 0
                        while (i < s.length) { if (s[i] == '<') depth++; if (s[i] == '>') { depth--; if (depth == 0) { i++; break } }; i++ }
                    }
                    else -> return
                }
            }
        }

        private fun name(): String {
            val st = i
            while (i < s.length && (s[i].isLetterOrDigit() || s[i] in "_-.:")) i++
            if (st == i) fail("expected a name")
            return s.substring(st, i)
        }

        private fun element(): Pair<String, Any?> {
            i++ // <
            val tag = name()
            val attrs = LinkedHashMap<String, Any?>()
            while (true) {
                while (i < s.length && s[i].isWhitespace()) i++
                if (i >= s.length) fail("unterminated tag <$tag")
                if (s[i] == '/' ) { if (!s.startsWith("/>", i)) fail("bad tag end"); i += 2; return tag to build(attrs, emptyList(), "") }
                if (s[i] == '>') { i++; break }
                val an = name()
                while (i < s.length && s[i].isWhitespace()) i++
                if (i >= s.length || s[i] != '=') fail("attribute '$an' needs =")
                i++
                while (i < s.length && s[i].isWhitespace()) i++
                if (i >= s.length || (s[i] != '"' && s[i] != '\'')) fail("attribute '$an' needs quotes")
                val q = s[i++]
                val e = s.indexOf(q, i)
                if (e < 0) fail("unterminated attribute value")
                attrs["@$an"] = decodeEntities(s.substring(i, e)); i = e + 1
            }
            val children = ArrayList<Pair<String, Any?>>()
            val text = StringBuilder()
            while (true) {
                if (i >= s.length) fail("missing closing tag </$tag>")
                if (s.startsWith("</", i)) {
                    i += 2
                    val close = name()
                    while (i < s.length && s[i].isWhitespace()) i++
                    if (i >= s.length || s[i] != '>') fail("bad closing tag")
                    i++
                    if (close != tag) fail("</$close> does not match <$tag>")
                    return tag to build(attrs, children, text.toString())
                } else if (s.startsWith("<!--", i)) {
                    val e = s.indexOf("-->", i); if (e < 0) fail("unterminated comment"); i = e + 3
                } else if (s.startsWith("<![CDATA[", i)) {
                    val e = s.indexOf("]]>", i); if (e < 0) fail("unterminated CDATA"); text.append(s, i + 9, e); i = e + 3
                } else if (s.startsWith("<?", i)) {
                    val e = s.indexOf("?>", i); if (e < 0) fail("unterminated <? ?>"); i = e + 2
                } else if (s[i] == '<') {
                    children.add(element())
                } else {
                    val e = s.indexOf('<', i).let { if (it < 0) s.length else it }
                    text.append(decodeEntities(s.substring(i, e))); i = e
                }
            }
        }

        private fun build(attrs: Map<String, Any?>, children: List<Pair<String, Any?>>, text: String): Any? {
            if (attrs.isEmpty() && children.isEmpty()) return if (text.isBlank()) "" else infer(text.trim())
            val m = LinkedHashMap<String, Any?>(attrs)
            for ((n, v) in children) {
                if (m.containsKey(n)) {
                    val old = m[n]
                    if (old is MutableList<*>) { @Suppress("UNCHECKED_CAST") (old as MutableList<Any?>).add(v) }
                    else m[n] = arrayListOf(old, v)
                } else m[n] = v
            }
            if (text.isNotBlank()) m["#text"] = infer(text.trim())
            return m
        }
    }

    private fun xmlName(s: String): String {
        var n = s.replace(Regex("[^A-Za-z0-9_.\\-:]"), "_")
        if (n.isEmpty() || !(n[0].isLetter() || n[0] == '_')) n = "_$n"
        return n
    }

    private fun xmlEsc(s: String): String {
        val sb = StringBuilder()
        for (c in s) when {
            c == '&' -> sb.append("&amp;"); c == '<' -> sb.append("&lt;"); c == '>' -> sb.append("&gt;")
            c == '"' -> sb.append("&quot;")
            c.code < 32 && c != '\n' && c != '\r' && c != '\t' -> Unit
            else -> sb.append(c)
        }
        return sb.toString()
    }

    private fun toXml(tree: Any?): String {
        val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        fun emit(name: String, v: Any?, ind: Int) {
            val pad = "  ".repeat(ind)
            val n = xmlName(name)
            when (v) {
                is List<*> -> { if (v.isEmpty()) sb.append(pad).append('<').append(n).append("/>\n") else for (x in v) emit(name, x, ind) }
                is Map<*, *> -> {
                    val attrs = v.filterKeys { it.toString().startsWith("@") }
                    val body = v.filterKeys { !it.toString().startsWith("@") }
                    sb.append(pad).append('<').append(n)
                    for ((k, a) in attrs) sb.append(' ').append(xmlName(k.toString().substring(1))).append("=\"").append(xmlEsc(scalarStr(a))).append('"')
                    if (body.isEmpty()) { sb.append("/>\n"); return }
                    val textOnly = body.size == 1 && body.containsKey("#text")
                    if (textOnly) { sb.append('>').append(xmlEsc(scalarStr(body["#text"]))).append("</").append(n).append(">\n"); return }
                    sb.append(">\n")
                    for ((k, c) in body) {
                        if (k.toString() == "#text") sb.append(pad).append("  ").append(xmlEsc(scalarStr(c))).append('\n')
                        else emit(k.toString(), c, ind + 1)
                    }
                    sb.append(pad).append("</").append(n).append(">\n")
                }
                null -> sb.append(pad).append('<').append(n).append("/>\n")
                else -> sb.append(pad).append('<').append(n).append('>').append(xmlEsc(scalarStr(v))).append("</").append(n).append(">\n")
            }
        }
        val root = tree
        if (root is Map<*, *> && root.size == 1 && root.keys.first().toString().let { !it.startsWith("@") && it != "#text" } && root.values.first() !is List<*>) {
            emit(root.keys.first().toString(), root.values.first(), 0)
        } else emit("root", if (root is List<*>) mapOf("item" to root) else root, 0)
        return sb.toString()
    }

    // ===================================================================== YAML

    private class YamlReader(text: String) {
        private class Line(var indent: Int, var text: String, val raw: String)
        private val lines = ArrayList<Line>()
        private var idx = 0

        init {
            for (raw in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
                val t = raw.replace("\t", "  ")
                val ind = t.length - t.trimStart().length
                lines.add(Line(ind, t.trim(), t))
            }
        }

        private fun fail(msg: String): Nothing = throw IllegalArgumentException("Invalid YAML near line ${idx + 1}: $msg")

        private fun stripComment(s: String): String {
            var q = 0.toChar()
            for (k in s.indices) {
                val c = s[k]
                if (q != 0.toChar()) { if (c == q) q = 0.toChar() }
                else if (c == '"' || c == '\'') q = c
                else if (c == '#' && (k == 0 || s[k - 1] == ' ')) return s.substring(0, k).trimEnd()
            }
            return s
        }

        /** Skips blanks / comments / document markers; returns the current structural line or null. */
        private fun cur(): Line? {
            while (idx < lines.size) {
                val l = lines[idx]
                if (l.text.isEmpty() || l.text.startsWith("#") || l.text == "---" || l.text == "...") { idx++; continue }
                if (l.text.startsWith("%")) { idx++; continue }
                return l
            }
            return null
        }

        fun readDocument(): Any? {
            val first = cur() ?: return null
            if (first.text.startsWith("--- ")) first.text = first.text.substring(4).trim()
            val v = block(first.indent)
            val rest = cur()
            if (rest != null) fail("unexpected content '${rest.text}' (check the indentation)")
            return v
        }

        private fun isSeq(t: String) = t == "-" || t.startsWith("- ")

        private fun block(indent: Int): Any? {
            val l = cur() ?: return null
            val t = stripComment(l.text)
            return if (isSeq(t)) seq(indent) else if (splitKey(t) != null) map(indent) else { idx++; scalar(t) }
        }

        private fun seq(indent: Int): Any? {
            val out = ArrayList<Any?>()
            while (true) {
                val l = cur() ?: break
                val t = stripComment(l.text)
                if (l.indent != indent || !isSeq(t)) break
                val item = t.removePrefix("-").trimStart()
                if (item.isEmpty()) {
                    idx++
                    val nx = cur()
                    out.add(if (nx != null && nx.indent > indent) block(nx.indent) else null)
                } else if (isSeq(item) || (splitKey(item) != null && !item.startsWith("{") && !item.startsWith("[") && !item.startsWith("\"") .let { false })) {
                    val virt = indent + (t.length - item.length)
                    l.indent = virt; l.text = item
                    out.add(block(virt))
                } else { idx++; out.add(scalarOrBlockText(item, indent)) }
            }
            return out
        }

        private fun map(indent: Int): Any? {
            val out = LinkedHashMap<String, Any?>()
            while (true) {
                val l = cur() ?: break
                val t = stripComment(l.text)
                if (l.indent != indent || isSeq(t)) break
                val kv = splitKey(t) ?: fail("expected 'key: value' but found '$t'")
                val key = kv.first; val rest = kv.second
                idx++
                if (rest.isEmpty()) {
                    val nx = cur()
                    out[key] = when {
                        nx == null -> null
                        nx.indent > indent -> block(nx.indent)
                        nx.indent == indent && isSeq(stripComment(nx.text)) -> seq(indent)
                        else -> null
                    }
                } else out[key] = scalarOrBlockText(rest, indent)
            }
            return out
        }

        /** Handles | and > block scalars (idx already points after the header line). */
        private fun scalarOrBlockText(rest: String, parentIndent: Int): Any? {
            if (rest.startsWith("|") || rest.startsWith(">")) {
                val folded = rest.startsWith(">")
                val chomp = rest.drop(1).firstOrNull { it == '-' || it == '+' }
                val body = ArrayList<String>()
                var blockIndent = -1
                while (idx < lines.size) {
                    val l = lines[idx]
                    if (l.raw.isBlank()) { body.add(""); idx++; continue }
                    if (l.indent <= parentIndent) break
                    if (blockIndent < 0) blockIndent = l.indent
                    body.add(l.raw.substring(minOf(blockIndent, l.raw.length)))
                    idx++
                }
                while (body.isNotEmpty() && body.last().isEmpty()) body.removeAt(body.size - 1)
                var s = if (folded) body.joinToString("\n").replace(Regex("(?<!\n)\n(?!\n)"), " ").replace("\n\n", "\n") else body.joinToString("\n")
                if (chomp != '-') s += "\n"
                return s
            }
            return scalar(rest)
        }

        /** Splits `key: value` (colon outside quotes, followed by space or end). */
        private fun splitKey(t: String): Pair<String, String>? {
            if (t.startsWith("{") || t.startsWith("[")) return null
            var q = 0.toChar()
            for (k in t.indices) {
                val c = t[k]
                if (q != 0.toChar()) { if (c == q) q = 0.toChar(); continue }
                if ((c == '"' || c == '\'') && k == 0) { q = c; continue }
                if (c == ':' && (k == t.length - 1 || t[k + 1] == ' ')) {
                    val rawKey = t.substring(0, k).trim()
                    if (rawKey.isEmpty()) return null
                    val key = if (rawKey.length >= 2 && (rawKey[0] == '"' || rawKey[0] == '\'') && rawKey.last() == rawKey[0]) unquote(rawKey) else rawKey
                    return key to t.substring(k + 1).trim().let { stripComment(it) }
                }
            }
            return null
        }

        private fun unquote(s: String): String {
            if (s.startsWith("'")) return s.substring(1, s.length - 1).replace("''", "'")
            return JsonReader(s).readDocument() as? String ?: s.substring(1, s.length - 1)
        }

        private fun scalar(sRaw: String): Any? {
            val s = sRaw.trim()
            if (s.isEmpty() || s == "~" || s == "null" || s == "Null" || s == "NULL") return null
            if (s.startsWith("[") || s.startsWith("{")) return Flow(s).readAll()
            if ((s.startsWith("\"") && s.endsWith("\"") && s.length >= 2)) return try { unquote(s) } catch (_: Exception) { s.substring(1, s.length - 1) }
            if ((s.startsWith("'") && s.endsWith("'") && s.length >= 2)) return unquote(s)
            return plain(s)
        }

        private fun plain(s: String): Any? = when (s) {
            "true", "True", "TRUE" -> true
            "false", "False", "FALSE" -> false
            "null", "Null", "NULL", "~" -> null
            else -> infer(s)
        }

        private inner class Flow(private val s: String) {
            private var p = 0
            fun readAll(): Any? { val v = value(); ws(); if (p < s.length) fail("unexpected '${s[p]}' in flow collection"); return v }
            private fun ws() { while (p < s.length && s[p].isWhitespace()) p++ }
            private fun value(): Any? {
                ws()
                if (p >= s.length) fail("unterminated flow collection")
                return when (s[p]) {
                    '[' -> {
                        p++; val out = ArrayList<Any?>(); ws()
                        if (p < s.length && s[p] == ']') { p++; return out }
                        while (true) {
                            out.add(value()); ws()
                            if (p < s.length && s[p] == ',') { p++; ws(); if (p < s.length && s[p] == ']') { p++; break }; continue }
                            if (p < s.length && s[p] == ']') { p++; break }
                            fail("expected , or ]")
                        }
                        out
                    }
                    '{' -> {
                        p++; val out = LinkedHashMap<String, Any?>(); ws()
                        if (p < s.length && s[p] == '}') { p++; return out }
                        while (true) {
                            ws()
                            val k = keyToken(); ws()
                            if (p >= s.length || s[p] != ':') fail("expected : in flow mapping")
                            p++
                            out[k] = value(); ws()
                            if (p < s.length && s[p] == ',') { p++; ws(); if (p < s.length && s[p] == '}') { p++; break }; continue }
                            if (p < s.length && s[p] == '}') { p++; break }
                            fail("expected , or }")
                        }
                        out
                    }
                    '"', '\'' -> quoted()
                    else -> {
                        val st = p
                        while (p < s.length && s[p] !in ",]}") p++
                        plain(s.substring(st, p).trim())
                    }
                }
            }
            private fun quoted(): String {
                val q = s[p]; val st = p; p++
                while (p < s.length && s[p] != q) { if (q == '"' && s[p] == '\\') p++; p++ }
                if (p >= s.length) fail("unterminated string")
                p++
                return unquote(s.substring(st, p))
            }
            private fun keyToken(): String {
                if (p < s.length && (s[p] == '"' || s[p] == '\'')) return quoted()
                val st = p
                while (p < s.length && s[p] != ':' && s[p] != ',' && s[p] != '}') p++
                return s.substring(st, p).trim()
            }
        }
    }

    private fun yamlScalar(v: Any?): String = when (v) {
        null -> "null"
        is Boolean -> v.toString()
        is Number -> numStr(v)
        else -> {
            val s = v.toString()
            val needs = s.isEmpty() || s != s.trim() || s.contains('\n') || s.contains(": ") || s.contains(" #") ||
                s.endsWith(":") || s[0] in "-?:,[]{}#&*!|>'\"%@`" ||
                s in setOf("null", "Null", "NULL", "~", "true", "false", "True", "False", "TRUE", "FALSE", "yes", "no", "on", "off") ||
                numberRe.matches(s) || Regex("[-+.]?[0-9][0-9_.:,xXa-fA-FoOeE+-]*").matches(s)
            if (needs) jsonStr(s) else s
        }
    }

    private fun toYaml(tree: Any?): String {
        fun render(v: Any?, ind: Int): List<String> {
            val pad = " ".repeat(ind)
            val out = ArrayList<String>()
            when (v) {
                is Map<*, *> -> for ((k, x) in v) {
                    val key = yamlScalar(k.toString()).let { if (k.toString().isNotEmpty() && it == k.toString()) it else it }
                    when {
                        x is Map<*, *> && x.isNotEmpty() -> { out.add("$pad$key:"); out.addAll(render(x, ind + 2)) }
                        x is List<*> && x.isNotEmpty() -> { out.add("$pad$key:"); out.addAll(render(x, ind + 2)) }
                        x is Map<*, *> -> out.add("$pad$key: {}")
                        x is List<*> -> out.add("$pad$key: []")
                        else -> out.add("$pad$key: ${yamlScalar(x)}")
                    }
                }
                is List<*> -> for (x in v) {
                    when {
                        (x is Map<*, *> && x.isNotEmpty()) || (x is List<*> && x.isNotEmpty()) -> {
                            val sub = render(x, ind + 2)
                            out.add(pad + "- " + sub[0].substring(ind + 2))
                            out.addAll(sub.drop(1))
                        }
                        x is Map<*, *> -> out.add("$pad- {}")
                        x is List<*> -> out.add("$pad- []")
                        else -> out.add("$pad- ${yamlScalar(x)}")
                    }
                }
                else -> out.add(pad + yamlScalar(v))
            }
            return out
        }
        if (tree is Map<*, *> && tree.isEmpty()) return "{}\n"
        if (tree is List<*> && tree.isEmpty()) return "[]\n"
        return render(tree, 0).joinToString("\n", postfix = "\n")
    }

    // ===================================================================== TOML

    private fun tomlKey(k: String) = if (k.isNotEmpty() && k.all { it.isLetterOrDigit() && it.code < 128 || it == '_' || it == '-' }) k else jsonStr(k)

    private fun tomlInline(v: Any?): String = when (v) {
        null -> "\"\""
        is Boolean -> v.toString()
        is Number -> numStr(v).let { if (it == "null") "nan" else it }
        is List<*> -> v.joinToString(", ", "[", "]") { tomlInline(it) }
        is Map<*, *> -> v.entries.filter { it.value != null }.joinToString(", ", "{ ", " }") { "${tomlKey(it.key.toString())} = ${tomlInline(it.value)}" }.let { if (v.isEmpty()) "{}" else it }
        else -> jsonStr(v.toString())
    }

    private fun toToml(tree: Any?): String {
        val root: Map<*, *> = if (tree is Map<*, *>) tree else mapOf("data" to tree)
        val sb = StringBuilder()
        fun tableLike(v: Any?) = v is Map<*, *> || (v is List<*> && v.isNotEmpty() && v.all { it is Map<*, *> })
        fun write(path: String, m: Map<*, *>) {
            for ((k, v) in m) if (!tableLike(v) && v != null) sb.append(tomlKey(k.toString())).append(" = ").append(tomlInline(v)).append('\n')
            for ((k, v) in m) {
                val p = if (path.isEmpty()) tomlKey(k.toString()) else path + "." + tomlKey(k.toString())
                if (v is Map<*, *>) { sb.append("\n[").append(p).append("]\n"); write(p, v) }
                else if (v is List<*> && tableLike(v)) for (e in v) { sb.append("\n[[").append(p).append("]]\n"); write(p, e as Map<*, *>) }
            }
        }
        write("", root)
        return sb.toString().trimStart('\n')
    }

    // ===================================================================== plain text

    private fun toTxt(tree: Any?): String {
        val sb = StringBuilder()
        fun w(v: Any?, ind: Int, dashes: Boolean) {
            val pad = "  ".repeat(ind)
            when (v) {
                is Map<*, *> -> for ((k, x) in v) {
                    if (isContainer(x) && !(x is Map<*, *> && x.isEmpty()) && !(x is List<*> && x.isEmpty())) { sb.append(pad).append(k).append(":\n"); w(x, ind + 1, true) }
                    else sb.append(pad).append(k).append(':').append(scalarStr(if (isContainer(x)) "" else x).let { if (it.isEmpty()) "" else " $it" }).append('\n')
                }
                is List<*> -> for (x in v) {
                    if (isContainer(x)) { sb.append(pad).append("-\n"); w(x, ind + 1, true) }
                    else sb.append(pad).append(if (dashes) "- " else "").append(scalarStr(x)).append('\n')
                }
                else -> sb.append(pad).append(scalarStr(v)).append('\n')
            }
        }
        if (tree is List<*> && tree.isNotEmpty() && tree.all { it is Map<*, *> }) {
            tree.forEachIndexed { n, r -> if (n > 0) sb.append('\n'); w(r, 0, true) }
        } else w(tree, 0, false)
        return sb.toString()
    }
}
