package com.karthikhegde.meterpod

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.karthikhegde.meterpod.calculator.ExpressionEvaluator

/**
 * Scientific calculator built on [ExpressionEvaluator]: every button tap
 * appends a token to a plain-text expression string, which is re-evaluated
 * after every tap for a live preview (silently ignoring errors from
 * incomplete expressions like "3+", which are normal mid-typing states,
 * not real errors). Tapping "=" commits the result and starts the next
 * expression from it, like a normal calculator's chained-calculation flow.
 */
class CalculatorFragment : Fragment() {

    private lateinit var expressionText: TextView
    private lateinit var resultText: TextView

    private var expression = StringBuilder()
    private var angleInDegrees = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_calculator, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        expressionText = view.findViewById(R.id.expressionText)
        resultText = view.findViewById(R.id.resultText)

        // Digits and simple literal tokens.
        mapOf(
            R.id.digit0Button to "0", R.id.digit1Button to "1", R.id.digit2Button to "2",
            R.id.digit3Button to "3", R.id.digit4Button to "4", R.id.digit5Button to "5",
            R.id.digit6Button to "6", R.id.digit7Button to "7", R.id.digit8Button to "8",
            R.id.digit9Button to "9", R.id.decimalButton to ".",
            R.id.addButton to "+", R.id.subtractButton to "-",
            R.id.multiplyButton to "*", R.id.divideButton to "/",
            R.id.openParenButton to "(", R.id.closeParenButton to ")",
            R.id.piButton to "pi", R.id.eButton to "e",
            R.id.percentButton to "%", R.id.factorialButton to "!",
            R.id.powerButton to "^"
        ).forEach { (id, token) ->
            view.findViewById<Button>(id).setOnClickListener { append(token) }
        }

        // Function tokens that open a parenthesis, ready for the argument.
        mapOf(
            R.id.sinButton to "sin(", R.id.cosButton to "cos(", R.id.tanButton to "tan(",
            R.id.asinButton to "asin(", R.id.acosButton to "acos(", R.id.atanButton to "atan(",
            R.id.logButton to "log(", R.id.lnButton to "ln(", R.id.sqrtButton to "sqrt("
        ).forEach { (id, token) ->
            view.findViewById<Button>(id).setOnClickListener { append(token) }
        }

        // Postfix tokens for the currently-typed value.
        view.findViewById<Button>(R.id.squareButton).setOnClickListener { append("^2") }
        view.findViewById<Button>(R.id.inverseButton).setOnClickListener { append("^(-1)") }
        view.findViewById<Button>(R.id.negateButton).setOnClickListener { wrapInNegation() }

        view.findViewById<Button>(R.id.backspaceButton).setOnClickListener { backspace() }
        view.findViewById<Button>(R.id.clearButton).setOnClickListener { clearAll() }
        view.findViewById<Button>(R.id.equalsButton).setOnClickListener { commitResult() }

        val degToggleButton = view.findViewById<Button>(R.id.degToggleButton)
        degToggleButton.setOnClickListener {
            angleInDegrees = !angleInDegrees
            degToggleButton.text = if (angleInDegrees) "DEG" else "RAD"
            updatePreview()
        }

        updateDisplays()
    }

    private fun append(token: String) {
        expression.append(token)
        updateDisplays()
    }

    private fun backspace() {
        if (expression.isNotEmpty()) expression.deleteCharAt(expression.length - 1)
        updateDisplays()
    }

    private fun clearAll() {
        expression.clear()
        resultText.text = "0"
        expressionText.text = ""
    }

    private fun wrapInNegation() {
        expression.insert(0, "-(").append(")")
        updateDisplays()
    }

    private fun commitResult() {
        val value = tryEvaluate(expression.toString()) ?: return
        expression = StringBuilder(formatNumber(value))
        updateDisplays()
    }

    private fun updateDisplays() {
        expressionText.text = expression.toString()
        updatePreview()
    }

    private fun updatePreview() {
        if (expression.isEmpty()) {
            resultText.text = "0"
            return
        }
        val value = tryEvaluate(expression.toString())
        if (value != null) {
            resultText.text = formatNumber(value)
        }
        // If evaluation fails, leave the previous preview showing rather than
        // flashing "Error" on every normal incomplete-expression keystroke.
    }

    private fun tryEvaluate(text: String): Double? {
        return try {
            ExpressionEvaluator(angleInDegrees).evaluate(text)
        } catch (e: Exception) {
            null
        }
    }

    private fun formatNumber(value: Double): String {
        if (value.isNaN()) return "Error"
        if (value.isInfinite()) return if (value > 0) "∞" else "-∞"

        val absValue = kotlin.math.abs(value)
        return if (absValue != 0.0 && (absValue < 0.0001 || absValue >= 1.0e12)) {
            String.format("%.6e", value)
        } else {
            var text = String.format("%.8f", value)
            if (text.contains(".")) text = text.trimEnd('0').trimEnd('.')
            text
        }
    }
}
