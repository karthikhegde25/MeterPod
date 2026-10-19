package com.karthikhegde.meterpod

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.karthikhegde.meterpod.compose.KeyGeometries
import com.karthikhegde.meterpod.compose.KeyPalettes
import com.karthikhegde.meterpod.compose.MP
import com.karthikhegde.meterpod.compose.ResumePauseEffect
import com.karthikhegde.meterpod.compose.key3DBackground
import com.karthikhegde.meterpod.ui.RulerView

/**
 * Screen ruler, in the app's normal portrait orientation. The ruler strip
 * runs the full height of the display along the right edge (now the
 * phone's longer physical dimension, since we're not forcing landscape),
 * so it measures a longer real-world length than it would sideways.
 *
 * RulerView draws the tick marks, the draggable red line, the value
 * readout, and the lock icon (which now rides along the line itself), and
 * handles its own touch input for dragging/locking. This screen adds
 * the +/- buttons for fine nudging, using the same tap-vs-hold pattern as
 * the Protractor's steppers.
 */
@Composable
fun RulerScreen() {
    val state = remember { RulerHoldState() }

    ResumePauseEffect(onResume = {}, onPause = { state.stopHolding() })
    DisposableEffect(Unit) { onDispose { state.stopHolding() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MP.Background)
    ) {
        AndroidView(
            factory = { ctx -> RulerView(ctx).also { state.rulerView = it } },
            modifier = Modifier.fillMaxSize()
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp)
        ) {
            RulerStepButton(
                label = "−",
                onPressDown = {
                    state.rulerView?.nudgeCm(state.singleStepCm * -1)
                    state.holdDirection = -1
                    state.holdHandler.postDelayed(state.holdRunnable, state.holdInitialDelayMs)
                },
                onRelease = { state.stopHolding() }
            )
            Spacer(Modifier.width(10.dp))
            RulerStepButton(
                label = "+",
                onPressDown = {
                    state.rulerView?.nudgeCm(state.singleStepCm * 1)
                    state.holdDirection = 1
                    state.holdHandler.postDelayed(state.holdRunnable, state.holdInitialDelayMs)
                },
                onRelease = { state.stopHolding() }
            )
        }
    }
}

/** 48dp round blue stepper (bg_round_button) that reports raw press / release for hold-to-repeat. */
@Composable
private fun RulerStepButton(
    label: String,
    onPressDown: () -> Unit,
    onRelease: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(48.dp)
            .key3DBackground(KeyGeometries.Round, KeyPalettes.Primary, pressed = pressed)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        onPressDown()
                        tryAwaitRelease()
                        onRelease()
                        pressed = false
                    }
                )
            }
            .padding(bottom = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = Color(0xFFFFFFFF),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

/** The fragment's fields and hold-to-repeat logic (moved verbatim). */
private class RulerHoldState {
    var rulerView: RulerView? = null

    val singleStepCm = 0.01f
    val holdStepCm = 0.05f
    val holdIntervalMs = 50L
    val holdInitialDelayMs = 350L

    val holdHandler = Handler(Looper.getMainLooper())
    var holdDirection = 0 // +1, -1, or 0 when nothing is held
    val holdRunnable = object : Runnable {
        override fun run() {
            if (holdDirection == 0) return
            rulerView?.nudgeCm(holdStepCm * holdDirection)
            holdHandler.postDelayed(this, holdIntervalMs)
        }
    }

    fun stopHolding() {
        holdDirection = 0
        holdHandler.removeCallbacks(holdRunnable)
    }
}
