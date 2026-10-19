package com.karthikhegde.meterpod

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karthikhegde.meterpod.compose.MP
import com.karthikhegde.meterpod.compose.RealButton
import com.karthikhegde.meterpod.uuid.UuidExport
import com.karthikhegde.meterpod.uuid.UuidGen
import com.karthikhegde.meterpod.uuid.UuidVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SHOW_LIMIT = 200

/** UUID generator: v1/v3/v4/v5/v6/v7/Nil/Max, up to 50,000 at once, export to TXT, PDF or Markdown. */
@Composable
fun UuidScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var version by remember { mutableStateOf(UuidVersion.V4) }
    var countText by remember { mutableStateOf("10") }
    var upper by remember { mutableStateOf(false) }
    var hyphens by remember { mutableStateOf(true) }
    var wrap by remember { mutableStateOf(0) } // 0 plain, 1 {braces}, 2 urn:uuid:
    var checkText by remember { mutableStateOf("") }
    var ns by remember { mutableStateOf(UuidGen.NS_DNS) }
    var nsName by remember { mutableStateOf("DNS") }
    var name by remember { mutableStateOf("example.com") }
    var ids by remember { mutableStateOf<List<String>>(emptyList()) }
    var usedLabel by remember { mutableStateOf("v4") }
    var busy by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) } // bumped when the same version is tapped again, to get fresh ids

    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()

    fun generate() {
        val n = countText.toIntOrNull()
        if (n == null || n < 1) { toast("Enter how many (1 - ${UuidGen.MAX_COUNT})"); return }
        val count = n.coerceAtMost(UuidGen.MAX_COUNT)
        if (n > UuidGen.MAX_COUNT) { countText = count.toString(); toast("Limited to ${UuidGen.MAX_COUNT}") }
        busy = true
        val v = version; val nsv = ns; val nm = name; val up = upper; val hy = hyphens; val wr = wrap
        scope.launch {
            ids = withContext(Dispatchers.Default) { UuidGen.generate(v, count, nsv, nm, up, hy, wr) }
            usedLabel = v.label
            busy = false
        }
    }

    fun save(uri: Uri?, kind: String) {
        if (uri == null) return
        val list = ids; val label = usedLabel
        scope.launch {
            busy = true
            val ok = withContext(Dispatchers.Default) {
                try {
                    val bytes = when (kind) { "pdf" -> UuidExport.pdf(list, label); "md" -> UuidExport.md(list, label); else -> UuidExport.txt(list) }
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    true
                } catch (e: Exception) { false }
            }
            busy = false
            toast(if (ok) "Saved ${list.size} UUIDs" else "Could not save the file")
        }
    }
    // Like other generators: show UUIDs straight away and refresh them whenever an option changes.
    LaunchedEffect(version, upper, hyphens, wrap, ns, nsName, name, countText, tick) {
        delay(250) // debounce typing in the count / name fields
        if (countText.toIntOrNull() != null) generate()
    }

    val txtSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { save(it, "txt") }
    val pdfSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { save(it, "pdf") }
    val mdSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { save(it, "md") }

    Column(
        Modifier.fillMaxSize().background(MP.Background).verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Label("Version")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(UuidVersion.V1, UuidVersion.V3, UuidVersion.V4, UuidVersion.V5).forEach { v ->
                RealButton(v.label, onClick = { version = v; tick++ }, primary = version == v, compact = true)
            }
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(UuidVersion.V6, UuidVersion.V7, UuidVersion.NIL, UuidVersion.MAX).forEach { v ->
                RealButton(v.label, onClick = { version = v; tick++ }, primary = version == v, compact = true)
            }
        }
        BasicText(
            "Tap a version again for new ones.  " + when (version) {
                UuidVersion.V1 -> "Time-based (timestamp + random node)."
                UuidVersion.V3 -> "Name-based using MD5: the same namespace and name always give the same UUID."
                UuidVersion.V4 -> "Random. The most common choice."
                UuidVersion.V5 -> "Name-based using SHA-1: the same namespace and name always give the same UUID."
                UuidVersion.V6 -> "Like v1 but reordered so ids sort by time."
                UuidVersion.V7 -> "Unix-time based and sortable; good for database keys."
                UuidVersion.NIL -> "All zeros."
                UuidVersion.MAX -> "All ones."
            },
            modifier = Modifier.padding(top = 8.dp),
            style = TextStyle(color = MP.TextSecondary, fontSize = 12.sp)
        )

        if (version == UuidVersion.V3 || version == UuidVersion.V5) {
            Label("Namespace")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("DNS" to UuidGen.NS_DNS, "URL" to UuidGen.NS_URL, "OID" to UuidGen.NS_OID, "X500" to UuidGen.NS_X500).forEach { (n, v) ->
                    RealButton(n, onClick = { ns = v; nsName = n }, primary = nsName == n, compact = true)
                }
            }
            Label("Name")
            Field(name, { name = it }, "example.com", KeyboardType.Text)
            BasicText("With more than one UUID, the name gets -1, -2, ... added so each one differs.",
                modifier = Modifier.padding(top = 4.dp), style = TextStyle(color = MP.TextSecondary, fontSize = 11.sp))
        }

        run {
            Label("How many (up to ${UuidGen.MAX_COUNT})")
            Field(countText, { countText = it.filter(Char::isDigit).take(6) }, "10", KeyboardType.Number)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1, 10, 100, 1000, 10000, 50000).forEach { n ->
                    RealButton(if (n >= 1000) "${n / 1000}k" else "$n", onClick = { countText = n.toString() }, compact = true, primary = countText == n.toString())
                }
            }
        }

        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RealButton(if (upper) "UPPERCASE: on" else "UPPERCASE: off", onClick = { upper = !upper }, compact = true, primary = upper)
            RealButton(if (hyphens) "Hyphens: on" else "Hyphens: off", onClick = { hyphens = !hyphens }, compact = true, primary = hyphens)
        }

        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RealButton(when (wrap) { 1 -> "Braces: on"; 2 -> "urn:uuid: on"; else -> "Braces / urn" }, onClick = { wrap = (wrap + 1) % 3 }, compact = true, primary = wrap != 0)
        }


        if (ids.isNotEmpty()) {
            Label("Result: ${ids.size} UUID" + (if (ids.size == 1) "" else "s") + " (" + usedLabel + ")")
            Box(
                Modifier.fillMaxWidth().background(MP.Surface, RoundedCornerShape(12.dp))
                    .border(1.dp, MP.SurfaceOutline, RoundedCornerShape(12.dp)).padding(12.dp)
            ) {
                BasicText(
                    ids.take(SHOW_LIMIT).joinToString("\n"),
                    style = TextStyle(color = MP.TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                )
            }
            if (ids.size > SHOW_LIMIT) BasicText(
                "Showing the first $SHOW_LIMIT. Export to get all ${ids.size}.",
                modifier = Modifier.padding(top = 6.dp), style = TextStyle(color = MP.TextSecondary, fontSize = 11.sp)
            )
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RealButton("Copy", onClick = {
                    if (ids.size > 5000) toast("Too many to copy - use Export")
                    else {
                        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                            ?.setPrimaryClip(ClipData.newPlainText("UUIDs", ids.joinToString("\n")))
                        toast("Copied ${ids.size}")
                    }
                }, compact = true)
            }
            Label("Export all together")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RealButton("TXT", onClick = { if (!busy) txtSaver.launch("uuids.txt") }, primary = true, compact = true, enabled = !busy)
                RealButton("PDF", onClick = { if (!busy) pdfSaver.launch("uuids.pdf") }, primary = true, compact = true, enabled = !busy)
                RealButton("MD", onClick = { if (!busy) mdSaver.launch("uuids.md") }, primary = true, compact = true, enabled = !busy)
            }
        }
        UuidChecker(checkText) { checkText = it }
    }
}

@Composable
private fun UuidChecker(text: String, onChange: (String) -> Unit) {
    Label("Check a UUID")
    Field(text, onChange, "Paste a UUID to see its version", KeyboardType.Text)
    if (text.isNotBlank()) BasicText(
        UuidGen.inspect(text),
        modifier = Modifier.padding(top = 8.dp),
        style = TextStyle(color = MP.TextPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    )
}

@Composable
private fun Label(t: String) {
    BasicText(t, modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp),
        style = TextStyle(color = MP.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold))
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, type: KeyboardType) {
    Box(
        Modifier.fillMaxWidth().background(MP.SurfaceElevated, RoundedCornerShape(12.dp))
            .border(1.dp, MP.SurfaceOutline, RoundedCornerShape(12.dp)).padding(12.dp)
    ) {
        val style = TextStyle(color = MP.TextPrimary, fontSize = 16.sp)
        if (value.isEmpty()) BasicText(hint, style = style.copy(color = MP.TextSecondary))
        BasicTextField(
            value = value, onValueChange = onChange, textStyle = style, singleLine = true,
            cursorBrush = SolidColor(MP.Accent),
            keyboardOptions = KeyboardOptions(keyboardType = type),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
