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
import androidx.fragment.app.Fragment
import com.karthikhegde.meterpod.ui.RulerView

/**
 * Screen ruler, in the app's normal portrait orientation. The ruler strip
 * runs the full height of the display along the right edge (now the
 * phone's longer physical dimension, since we're not forcing landscape),
 * so it measures a longer real-world length than it would sideways.
 *
 * RulerView draws the tick marks, the draggable red line, the value
 * readout, and the lock icon (which now rides along the line itself), and
 * handles its own touch input for dragging/locking. This fragment adds
 * the +/- buttons for fine nudging, using the same tap-vs-hold pattern as
 * the Protractor's steppers.
 */
class RulerFragment : Fragment() {

    private lateinit var rulerView: RulerView

    private val singleStepCm = 0.01f
    private val holdStepCm = 0.05f
    private val holdIntervalMs = 50L
    private val holdInitialDelayMs = 350L

    private val holdHandler = Handler(Looper.getMainLooper())
    private var holdDirection = 0 // +1, -1, or 0 when nothing is held
    private val holdRunnable = object : Runnable {
        override fun run() {
            if (holdDirection == 0) return
            rulerView.nudgeCm(holdStepCm * holdDirection)
            holdHandler.postDelayed(this, holdIntervalMs)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_ruler, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rulerView = view.findViewById(R.id.rulerView)

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
                    rulerView.nudgeCm(singleStepCm * direction)
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
}
