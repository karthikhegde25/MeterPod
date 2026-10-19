package com.karthikhegde.meterpod.uuid

import java.security.MessageDigest
import java.security.SecureRandom

enum class UuidVersion(val label: String) { V1("v1"), V3("v3"), V4("v4"), V5("v5"), V6("v6"), V7("v7"), NIL("Nil"), MAX("Max") }

/** RFC 9562 UUID generators (v1, v3, v4, v5, v6, v7, plus the Nil and Max special values). */
object UuidGen {
    const val MAX_COUNT = 50_000

    val NS_DNS = "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
    val NS_URL = "6ba7b811-9dad-11d1-80b4-00c04fd430c8"
    val NS_OID = "6ba7b812-9dad-11d1-80b4-00c04fd430c8"
    val NS_X500 = "6ba7b814-9dad-11d1-80b4-00c04fd430c8"

    private val rnd = SecureRandom()
    private const val GREGORIAN_OFFSET = 0x01B21DD213814000L // 100 ns ticks from 1582-10-15 to 1970-01-01

    private var lastTs = 0L
    private val node = ByteArray(6).also { rnd.nextBytes(it); it[0] = (it[0].toInt() or 0x01).toByte() }
    private var clockSeq = rnd.nextInt(0x4000)
    private var lastMs = 0L
    private var seq12 = 0

    private fun fmt(b: ByteArray): String {
        val sb = StringBuilder(36)
        for (i in 0 until 16) {
            if (i == 4 || i == 6 || i == 8 || i == 10) sb.append('-')
            sb.append("0123456789abcdef"[(b[i].toInt() shr 4) and 0xF]).append("0123456789abcdef"[b[i].toInt() and 0xF])
        }
        return sb.toString()
    }

    private fun stamp(b: ByteArray, version: Int) {
        b[6] = ((b[6].toInt() and 0x0F) or (version shl 4)).toByte()
        b[8] = ((b[8].toInt() and 0x3F) or 0x80).toByte()
    }

    private fun parse(s: String): ByteArray {
        val h = s.replace("-", "")
        return ByteArray(16) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    private fun nextGregorianTicks(): Long {
        var ts = System.currentTimeMillis() * 10_000L + GREGORIAN_OFFSET
        if (ts <= lastTs) ts = lastTs + 1
        lastTs = ts
        return ts
    }

    fun v4(): String { val b = ByteArray(16); rnd.nextBytes(b); stamp(b, 4); return fmt(b) }

    fun v1(): String {
        val ts = nextGregorianTicks()
        val b = ByteArray(16)
        val low = (ts and 0xFFFFFFFFL); val mid = (ts shr 32) and 0xFFFF; val hi = (ts shr 48) and 0x0FFF
        for (i in 0..3) b[i] = (low shr (24 - 8 * i)).toByte()
        b[4] = (mid shr 8).toByte(); b[5] = mid.toByte()
        b[6] = hi.shr(8).toByte(); b[7] = hi.toByte()
        b[8] = (clockSeq shr 8).toByte(); b[9] = clockSeq.toByte()
        System.arraycopy(node, 0, b, 10, 6)
        stamp(b, 1)
        return fmt(b)
    }

    /** v6 is v1 with the timestamp bytes reordered so the ids sort by time. */
    fun v6(): String {
        val ts = nextGregorianTicks()
        val b = ByteArray(16)
        val hi = (ts shr 28) and 0xFFFFFFFFL; val mid = (ts shr 12) and 0xFFFF; val lo = ts and 0x0FFF
        for (i in 0..3) b[i] = (hi shr (24 - 8 * i)).toByte()
        b[4] = (mid shr 8).toByte(); b[5] = mid.toByte()
        b[6] = lo.shr(8).toByte(); b[7] = lo.toByte()
        b[8] = (clockSeq shr 8).toByte(); b[9] = clockSeq.toByte()
        System.arraycopy(node, 0, b, 10, 6)
        stamp(b, 6)
        return fmt(b)
    }

    /** v7: 48-bit unix-ms timestamp + random; a counter keeps ids from one batch strictly increasing. */
    fun v7(): String {
        var ms = System.currentTimeMillis()
        if (ms <= lastMs) {
            ms = lastMs
            seq12++
            if (seq12 > 0xFFF) { ms++; seq12 = 0 }
        } else seq12 = rnd.nextInt(0x400)
        lastMs = ms
        val b = ByteArray(16)
        rnd.nextBytes(b)
        for (i in 0..5) b[i] = (ms shr (40 - 8 * i)).toByte()
        b[6] = (seq12 shr 8).toByte(); b[7] = seq12.toByte()
        stamp(b, 7)
        return fmt(b)
    }

    fun named(version: Int, namespace: String, name: String): String {
        val md = MessageDigest.getInstance(if (version == 3) "MD5" else "SHA-1")
        md.update(parse(namespace)); md.update(name.toByteArray(Charsets.UTF_8))
        val b = md.digest().copyOf(16)
        stamp(b, version)
        return fmt(b)
    }

    const val NIL = "00000000-0000-0000-0000-000000000000"
    const val MAXV = "ffffffff-ffff-ffff-ffff-ffffffffffff"

    /** Generates [count] ids. For v3/v5 each id hashes "name", or "name-N" when count > 1 (the same input always gives the same id). */
    fun generate(v: UuidVersion, count: Int, namespace: String, name: String, upper: Boolean, hyphens: Boolean, wrap: Int = 0): List<String> {
        val n = count.coerceIn(1, MAX_COUNT)
        val out = ArrayList<String>(n)
        for (i in 1..n) {
            var u = when (v) {
                UuidVersion.V1 -> v1(); UuidVersion.V4 -> v4(); UuidVersion.V6 -> v6(); UuidVersion.V7 -> v7()
                UuidVersion.V3 -> named(3, namespace, if (n > 1) "$name-$i" else name)
                UuidVersion.V5 -> named(5, namespace, if (n > 1) "$name-$i" else name)
                UuidVersion.NIL -> NIL; UuidVersion.MAX -> MAXV
            }
            if (!hyphens) u = u.replace("-", "")
            if (upper) u = u.uppercase()
            if (wrap == 1) u = "{" + u + "}" else if (wrap == 2) u = "urn:uuid:" + u
            out.add(u)
        }
        return out
    }

    /** Reads a pasted UUID and describes it (version, variant, and the time it encodes for v1, v6 and v7). */
    fun inspect(input: String): String {
        val h = input.trim().removePrefix("urn:uuid:").removePrefix("{").removeSuffix("}").replace("-", "")
        if (h.length != 32 || !h.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return "Not a valid UUID (need 32 hex digits)."
        val b = parse(h.substring(0, 8) + "-" + h.substring(8, 12) + "-" + h.substring(12, 16) + "-" + h.substring(16, 20) + "-" + h.substring(20))
        if (h.all { it == '0' }) return "Nil UUID (all zeros)."
        if (h.all { it == 'f' || it == 'F' }) return "Max UUID (all ones)."
        val ver = (b[6].toInt() shr 4) and 0xF
        val v = (b[8].toInt() shr 6) and 0x3
        val variant = if (v == 2) "RFC 9562 (standard)" else if (v == 3) "Microsoft/reserved" else "NCS (legacy)"
        val sb = StringBuilder("Version $ver, variant: $variant")
        fun ms(ticks: Long) = (ticks - GREGORIAN_OFFSET) / 10_000L
        val t: Long? = when (ver) {
            1 -> {
                val low = ((b[0].toLong() and 0xFF) shl 24) or ((b[1].toLong() and 0xFF) shl 16) or ((b[2].toLong() and 0xFF) shl 8) or (b[3].toLong() and 0xFF)
                val mid = ((b[4].toLong() and 0xFF) shl 8) or (b[5].toLong() and 0xFF)
                val hi = (((b[6].toLong() and 0x0F) shl 8) or (b[7].toLong() and 0xFF))
                ms((hi shl 48) or (mid shl 32) or low)
            }
            6 -> {
                val hi = ((b[0].toLong() and 0xFF) shl 24) or ((b[1].toLong() and 0xFF) shl 16) or ((b[2].toLong() and 0xFF) shl 8) or (b[3].toLong() and 0xFF)
                val mid = ((b[4].toLong() and 0xFF) shl 8) or (b[5].toLong() and 0xFF)
                val lo = (((b[6].toLong() and 0x0F) shl 8) or (b[7].toLong() and 0xFF))
                ms((hi shl 28) or (mid shl 12) or lo)
            }
            7 -> { var x = 0L; for (i in 0..5) x = (x shl 8) or (b[i].toLong() and 0xFF); x }
            else -> null
        }
        if (t != null) sb.append("\nCreated: ").append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(t)))
        when (ver) { 3 -> sb.append("\nName-based (MD5)"); 5 -> sb.append("\nName-based (SHA-1)"); 4 -> sb.append("\nRandom") }
        return sb.toString()
    }
}
