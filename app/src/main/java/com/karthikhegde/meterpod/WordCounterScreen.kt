package com.karthikhegde.meterpod

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karthikhegde.meterpod.compose.HairlineDivider
import com.karthikhegde.meterpod.compose.MP
import com.karthikhegde.meterpod.compose.MeterTextField
import com.karthikhegde.meterpod.compose.RealButton
import com.karthikhegde.meterpod.compose.TileCard

private val wordPattern = Regex("[A-Za-z]+")

/**
 * Word & character counter. Distinguishes several character-related
 * figures on purpose, since they answer different questions:
 *  - Words: counted as maximal runs of English letters (regex [A-Za-z]+).
 *    A token like "hello," still counts (the comma is just a non-letter
 *    boundary), but "123" or "!!!" don't count as words at all, and
 *    something like "test123" counts as one word ("test") since the
 *    digits aren't part of any letter run.
 *  - Characters: every character in the text, spaces/punctuation/digits included.
 *  - Characters (no spaces): the same, minus whitespace.
 *  - English letters: only a-z/A-Z - i.e. actual alphabetic letters, with
 *    digits, spaces, and symbols like "?", "!", "," all excluded.
 */
@Composable
fun WordCounterScreen() {
    var text by remember { mutableStateOf("") }

    val wordCount = wordPattern.findAll(text).count()
    val charCount = text.length
    val charNoSpaceCount = text.count { !it.isWhitespace() }
    val englishLetterCount = text.count { it in 'a'..'z' || it in 'A'..'Z' }

    val wordsCountText = String.format("%,d", wordCount)
    val charsCountText = String.format("%,d", charCount)
    val charsNoSpacesCountText = String.format("%,d", charNoSpaceCount)
    val englishLettersCountText = String.format("%,d", englishLetterCount)

    Column(
        Modifier
            .fillMaxSize()
            .background(MP.Background)
            .padding(16.dp)
    ) {
        MeterTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().weight(1f),
            hint = "Paste or type text here…",
            textSize = 15f,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )

        TileCard(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth()) {
                    CountCell(wordsCountText, "Words", Modifier.weight(1f))
                    CountCell(englishLettersCountText, "English letters", Modifier.weight(1f))
                }
                HairlineDivider(Modifier.padding(top = 14.dp, bottom = 14.dp))
                Row(Modifier.fillMaxWidth()) {
                    CountCell(charsCountText, "Characters", Modifier.weight(1f))
                    CountCell(charsNoSpacesCountText, "Characters (no spaces)", Modifier.weight(1f))
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            RealButton(
                text = "Clear",
                onClick = { text = "" },
                textColor = MP.Needle
            )
        }
    }
}

@Composable
private fun CountCell(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(
            text = value,
            style = TextStyle(color = MP.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        )
        BasicText(
            text = label,
            modifier = Modifier.padding(top = 2.dp),
            style = TextStyle(color = MP.TextSecondary, fontSize = 12.sp)
        )
    }
}
