package com.karthikhegde.meterpod

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.karthikhegde.meterpod.ui.ProtractorView

/**
 * A manually-operated protractor: flat base running down the left edge,
 * dome bulging out to the right, sized to fill the available screen space
 * - all inside the app's normal portrait layout. The screen is never
 * rotated to landscape; only the dial's own drawing has that shape.
 *
 * The needle can be set two ways - drag it directly on the protractor
 * with a finger, or use the +/- steppers (placed at the far left/right
 * edges) for fine adjustment. The Lock button freezes the angle entirely -
 * once locked, neither touch-dragging nor the +/- buttons can change it,
 * until unlocked again.
 *
 * Interaction for +/-: a single tap moves the needle by a small fixed
 * step; press and hold either button to sweep continuously at a faster
 * rate, similar to how a stepper/spinner control usually works.
 */
class ProtractorFragment : Fragment() {

    private lateinit var protractorView: ProtractorView
    private lateinit var angleReadoutText: TextView
    private lateinit var lockButton: Button

    private var currentAngle = 90.0
    private var isLocked = false

    private val singleStepDegrees = 0.01
    private val holdStepDegrees = 0.10
    private val holdIntervalMs = 50L
    private val holdInitialDelayMs = 350L

    private val holdHandler = Handler(Looper.getMainLooper())
    private var holdDirection = 0 // +1, -1, or 0 when nothing is held
    private val holdRunnable = object : Runnable {
        override fun run() {
            if (holdDirection == 0) return
            applyStep(holdStepDegrees * holdDirection)
            holdHandler.postDelayed(this, holdIntervalMs)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_protractor, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        protractorView = view.findViewById(R.id.protractorView)
        angleReadoutText = view.findViewById(R.id.angleReadoutText)
        lockButton = view.findViewById(R.id.lockButton)
        updateDisplay()

        protractorView.onAngleDragged = { angle ->
            currentAngle = angle.toDouble()
            angleReadoutText.text = String.format("Angle: %.2f°", currentAngle)
            // protractorView already has the live angle from the drag itself -
            // no need to call setAngle() again here, that would just be redundant.
        }

        lockButton.setOnClickListener {
            isLocked = !isLocked
            protractorView.isLocked = isLocked
            lockButton.text = if (isLocked) "Unlock" else "Lock"
        }

        setupHoldToRepeat(view.findViewById(R.id.plusButton), direction = 1)
        setupHoldToRepeat(view.findViewById(R.id.minusButton), direction = -1)
    }

    override fun onPause() {
        super.onPause()
        stopHolding()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupHoldToRepeat(button: Button, direction: Int) {
        button.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    applyStep(singleStepDegrees * direction)
                    holdDirection = direction
                    holdHandler.postDelayed(holdRunnable, holdInitialDelayMs)
                    v.isPressed = true
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopHolding()
                    v.isPressed = false
                    v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun stopHolding() {
        holdDirection = 0
        holdHandler.removeCallbacks(holdRunnable)
    }

    private fun applyStep(deltaDegrees: Double) {
        if (isLocked) return
        currentAngle = (currentAngle + deltaDegrees).coerceIn(0.0, 180.0)
        updateDisplay()
    }

    private fun updateDisplay() {
        angleReadoutText.text = String.format("Angle: %.2f°", currentAngle)
        protractorView.setAngle(currentAngle.toFloat())
    }
}
