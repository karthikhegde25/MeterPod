package com.karthikhegde.meterpod

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karthikhegde.meterpod.compose.MP
import com.karthikhegde.meterpod.compose.RealButton
import com.karthikhegde.meterpod.morse.MorseCode
import com.karthikhegde.meterpod.morse.MorseOutput
import com.karthikhegde.meterpod.morse.MorsePlayer

/** Morse code tool: text -> Morse and Morse -> text, with playback as sound, vibration or flashlight. */
@Composable
fun MorseScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember { MorsePlayer(ctx.applicationContext) }
    DisposableEffect(Unit) { onDispose { player.stop() } }

    var tab by remember { mutableStateOf(0) }
    var textIn by remember { mutableStateOf("") }
    var morseIn by remember { mutableStateOf("") }
    var speed by remember { mutableStateOf(1) } // 0 slow, 1 normal, 2 fast
    var playing by remember { mutableStateOf<MorseOutput?>(null) }

    val morse = if (tab == 0) MorseCode.encode(textIn) else morseIn.trim()
    val decoded = if (tab == 1) MorseCode.decode(morseIn) else ""
    val unitMs = longArrayOf(160L, 90L, 55L)[speed]

    fun play(out: MorseOutput) {
        if (playing == out) { player.stop(); playing = null; return }
        val t = MorseCode.timings(morse, unitMs)
        if (t.isEmpty()) { Toast.makeText(ctx, "Nothing to play yet", Toast.LENGTH_SHORT).show(); return }
        val ok = player.play(scope, out, t) { playing = null }
        if (!ok) Toast.makeText(ctx, when (out) {
            MorseOutput.FLASH -> "No flashlight found on this device"
            MorseOutput.VIBRATION -> "No vibrator found on this device"
            else -> "Could not play"
        }, Toast.LENGTH_SHORT).show() else playing = out
    }

    fun copy(s: String) {
        if (s.isBlank()) return
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("Morse", s))
        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
    }

    Column(
        Modifier.fillMaxSize().background(MP.Background).verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RealButton("Text to Morse", onClick = { tab = 0; player.stop(); playing = null }, primary = tab == 0, compact = true)
            RealButton("Morse to Text", onClick = { tab = 1; player.stop(); playing = null }, primary = tab == 1, compact = true)
        }

        if (tab == 0) {
            Label("Your text")
            Field(textIn, { textIn = it }, "Type something...", mono = false, caps = true)
            Label("Morse code")
            Result(if (morse.isEmpty()) "..." else morse)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RealButton("Copy", onClick = { copy(morse) }, compact = true, enabled = morse.isNotEmpty())
                RealButton("Clear", onClick = { textIn = "" }, compact = true, textColor = MP.Needle, enabled = textIn.isNotEmpty())
            }
        } else {
            Label("Morse code  (. and -, space between letters, / between words)")
            Field(morseIn, { morseIn = it }, "... --- ...", mono = true, caps = false)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RealButton(".", onClick = { morseIn += "." }, compact = true, primary = true)
                RealButton("-", onClick = { morseIn += "-" }, compact = true, primary = true)
                RealButton("space", onClick = { morseIn += " " }, compact = true)
                RealButton("/", onClick = { morseIn += " / " }, compact = true)
                RealButton("Del", onClick = { morseIn = morseIn.dropLast(1) }, compact = true, textColor = MP.Needle)
            }
            Label("Text")
            Result(if (decoded.isEmpty()) "..." else decoded)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RealButton("Copy", onClick = { copy(decoded) }, compact = true, enabled = decoded.isNotEmpty())
                RealButton("Clear", onClick = { morseIn = "" }, compact = true, textColor = MP.Needle, enabled = morseIn.isNotEmpty())
            }
        }

        Label("Play the Morse code as")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RealButton(if (playing == MorseOutput.SOUND) "Stop" else "Sound", onClick = { play(MorseOutput.SOUND) }, primary = true, compact = true)
            RealButton(if (playing == MorseOutput.VIBRATION) "Stop" else "Vibration", onClick = { play(MorseOutput.VIBRATION) }, primary = true, compact = true)
            RealButton(if (playing == MorseOutput.FLASH) "Stop" else "Flashlight", onClick = { play(MorseOutput.FLASH) }, primary = true, compact = true)
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText("Speed", style = TextStyle(color = MP.TextSecondary, fontSize = 13.sp))
            listOf("Slow", "Normal", "Fast").forEachIndexed { i, n ->
                RealButton(n, onClick = { speed = i }, primary = speed == i, compact = true)
            }
        }
        BasicText(
            "Dot = 1 unit, dash = 3 units, 3 units between letters, 7 between words. The flashlight uses the camera torch; keep the phone's volume up for sound.",
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            style = TextStyle(color = MP.TextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
        )
    }
}

@Composable
private fun Label(t: String) {
    BasicText(t, modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp),
        style = TextStyle(color = MP.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold))
}

@Composable
private fun Result(t: String) {
    Box(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)
            .background(MP.Surface, RoundedCornerShape(12.dp))
            .border(1.dp, MP.SurfaceOutline, RoundedCornerShape(12.dp)).padding(12.dp)
    ) {
        BasicText(t, style = TextStyle(color = MP.TextPrimary, fontSize = 18.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, mono: Boolean, caps: Boolean) {
    Box(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 90.dp)
            .background(MP.SurfaceElevated, RoundedCornerShape(12.dp))
            .border(1.dp, MP.SurfaceOutline, RoundedCornerShape(12.dp)).padding(12.dp)
    ) {
        val style = TextStyle(color = MP.TextPrimary, fontSize = 17.sp, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default)
        if (value.isEmpty()) BasicText(hint, style = style.copy(color = MP.TextSecondary))
        BasicTextField(
            value = value, onValueChange = onChange, textStyle = style,
            cursorBrush = SolidColor(MP.Accent),
            keyboardOptions = KeyboardOptions(capitalization = if (caps) KeyboardCapitalization.Sentences else KeyboardCapitalization.None),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
