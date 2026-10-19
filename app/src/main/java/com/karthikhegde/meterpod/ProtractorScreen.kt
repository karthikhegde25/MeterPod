package com.karthikhegde.meterpod

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.karthikhegde.meterpod.compose.KeyGeometries
import com.karthikhegde.meterpod.compose.KeyPalettes
import com.karthikhegde.meterpod.compose.MP
import com.karthikhegde.meterpod.compose.RealButton
import com.karthikhegde.meterpod.compose.ResumePauseEffect
import com.karthikhegde.meterpod.compose.key3DBackground
import com.karthikhegde.meterpod.ui.ProtractorView

/**
 * A manually-operated protractor. The needle can be set two ways - drag
 * it directly on the protractor with a finger, or use the +/- steppers
 * for fine adjustment. The Lock button freezes the angle entirely - once
 * locked, neither touch-dragging nor the +/- buttons can change it,
 * until unlocked again.
 *
 * Interaction for +/-: a single tap moves the needle by a small fixed
 * step; press and hold either button to sweep continuously at a faster
 * rate, similar to how a stepper/spinner control usually works.
 */
@Composable
fun ProtractorScreen() {
    val holder = remember { arrayOfNulls<ProtractorView>(1) }

    var angleReadout by remember { mutableStateOf(String.format("Angle: %.2f°", 90.0)) }
    var lockedUi by remember { mutableStateOf(false) }

    val state = remember {
        ProtractorState { angle ->
            angleReadout = String.format("Angle: %.2f°", angle)
            holder[0]?.setAngle(angle.toFloat())
        }
    }

    ResumePauseEffect(onResume = {}, onPause = { state.stopHolding() })
    DisposableEffect(Unit) { onDispose { state.stopHolding() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MP.Background)
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AndroidView(
                factory = { ctx ->
                    ProtractorView(ctx).also { pv ->
                        holder[0] = pv
                        state.updateDisplay()
                        pv.onAngleDragged = { angle ->
                            state.currentAngle = angle.toDouble()
                            angleReadout = String.format("Angle: %.2f°", state.currentAngle)
                            // protractorView already has the live angle from the drag itself -
                            // no need to call setAngle() again here, that would just be redundant.
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            RealButton(
                text = if (lockedUi) "Unlock" else "Lock",
                onClick = {
                    state.isLocked = !state.isLocked
                    holder[0]?.isLocked = state.isLocked
                    lockedUi = state.isLocked
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 40.dp, end = 12.dp)
                    .rotate(90f),
                primary = true,
                textColor = MP.TextPrimary,
                compact = true
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HoldStepButton(
                label = "−",
                onPressDown = {
                    state.applyStep(state.singleStepDegrees * -1)
                    state.holdDirection = -1
                    state.holdHandler.postDelayed(state.holdRunnable, state.holdInitialDelayMs)
                },
                onRelease = { state.stopHolding() },
                modifier = Modifier.rotate(90f)
            )
            Spacer(Modifier.weight(1f).height(1.dp))
            HoldStepButton(
                label = "+",
                onPressDown = {
                    state.applyStep(state.singleStepDegrees * 1)
                    state.holdDirection = 1
                    state.holdHandler.postDelayed(state.holdRunnable, state.holdInitialDelayMs)
                },
                onRelease = { state.stopHolding() },
                modifier = Modifier
            )
        }

        Text(
            text = angleReadout,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            color = MP.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

/** 52dp round blue stepper (bg_round_button) that reports raw press / release for hold-to-repeat. */
@Composable
private fun HoldStepButton(
    label: String,
    onPressDown: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(52.dp)
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
        Text(
            text = label,
            color = Color(0xFFFFFFFF),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** The fragment's fields and hold-to-repeat logic (moved verbatim). */
private class ProtractorState(private val displayAngle: (Double) -> Unit) {
    var currentAngle = 90.0
    var isLocked = false

    val singleStepDegrees = 0.01
    val holdStepDegrees = 0.10
    val holdIntervalMs = 50L
    val holdInitialDelayMs = 350L

    val holdHandler = Handler(Looper.getMainLooper())
    var holdDirection = 0 // +1, -1, or 0 when nothing is held
    val holdRunnable = object : Runnable {
        override fun run() {
            if (holdDirection == 0) return
            applyStep(holdStepDegrees * holdDirection)
            holdHandler.postDelayed(this, holdIntervalMs)
        }
    }

    fun stopHolding() {
        holdDirection = 0
        holdHandler.removeCallbacks(holdRunnable)
    }

    fun applyStep(deltaDegrees: Double) {
        if (isLocked) return
        currentAngle = (currentAngle + deltaDegrees).coerceIn(0.0, 180.0)
        updateDisplay()
    }

    fun updateDisplay() {
        displayAngle(currentAngle)
    }
}
