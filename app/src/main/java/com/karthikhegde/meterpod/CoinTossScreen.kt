package com.karthikhegde.meterpod

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.karthikhegde.meterpod.compose.MP
import com.karthikhegde.meterpod.compose.RealButton
import com.karthikhegde.meterpod.ui.CoinView
import kotlin.math.min
import kotlin.random.Random

@Composable
fun CoinTossScreen() {
    val context = LocalContext.current

    var resultText by remember { mutableStateOf(" ") }
    var headsCount by remember { mutableIntStateOf(0) }
    var tailsCount by remember { mutableIntStateOf(0) }

    val sound = remember { CoinLandSound(context) }
    DisposableEffect(Unit) { onDispose { sound.release() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MP.Background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BasicText(
            text = "Tap the coin to toss",
            modifier = Modifier.padding(top = 12.dp),
            style = TextStyle(color = MP.TextSecondary, fontSize = 14.sp)
        )
        // coin: square, as large as fits between the instruction and the result text
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    CoinView(ctx).also { coinView ->
                        coinView.onFlipComplete = { headsResult ->
                            resultText = if (headsResult) "Heads!" else "Tails!"
                            if (headsResult) headsCount++ else tailsCount++
                            sound.playLandSound()
                        }
                        coinView.setOnClickListener {
                            resultText = "Tossing…"
                            coinView.toss(Random.nextBoolean())
                        }
                    }
                },
                modifier = Modifier.squareFit()
            )
        }
        BasicText(
            text = resultText,
            style = TextStyle(color = MP.Accent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        )
        Row(
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(
                text = "Heads: $headsCount   Tails: $tailsCount",
                style = TextStyle(color = MP.TextSecondary, fontSize = 14.sp)
            )
            Spacer(Modifier.width(14.dp))
            RealButton(
                text = "Reset",
                onClick = {
                    headsCount = 0
                    tailsCount = 0
                },
                compact = true,
                textColor = MP.TextPrimary
            )
        }
    }
}

/** The fragment's SoundPool setup / playback / release, moved verbatim. */
private class CoinLandSound(context: Context) {
    private var soundPool: SoundPool? = null
    private var landSoundId = 0
    private var landSoundLoaded = false

    init {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(attributes)
            .build()
        landSoundLoaded = false
        soundPool?.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0 && sampleId == landSoundId) landSoundLoaded = true
        }
        landSoundId = soundPool?.load(context, R.raw.coin_land, 1) ?: 0
    }

    fun playLandSound() {
        if (landSoundLoaded) {
            soundPool?.play(landSoundId, 1f, 1f, 1, 0, 1f)
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
    }
}

/** Largest square that fits the incoming constraints (1:1 ConstraintLayout box with 0dp x 0dp). */
private fun Modifier.squareFit(): Modifier = this.layout { measurable, constraints ->
    val side = min(constraints.maxWidth, constraints.maxHeight)
    val p = measurable.measure(Constraints.fixed(side, side))
    layout(side, side) { p.placeRelative(0, 0) }
}
