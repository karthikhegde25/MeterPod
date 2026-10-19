package com.karthikhegde.meterpod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karthikhegde.meterpod.calculator.ExpressionEvaluator
import com.karthikhegde.meterpod.compose.CalcKey
import com.karthikhegde.meterpod.compose.CalcKind
import com.karthikhegde.meterpod.compose.MP

/**
 * Scientific calculator built on [ExpressionEvaluator]: every button tap
 * appends a token to a plain-text expression string, which is re-evaluated
 * after every tap for a live preview (silently ignoring errors from
 * incomplete expressions like "3+", which are normal mid-typing states,
 * not real errors). Tapping "=" commits the result and starts the next
 * expression from it, like a normal calculator's chained-calculation flow.
 */
@Composable
fun CalculatorScreen() {
    var expression by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("0") }
    var angleInDegrees by remember { mutableStateOf(true) }

    fun tryEvaluate(text: String, degrees: Boolean = angleInDegrees): Double? {
        return try {
            ExpressionEvaluator(degrees).evaluate(text)
        } catch (e: Exception) {
            null
        }
    }

    fun formatNumber(value: Double): String {
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

    fun updatePreview(expr: String = expression, degrees: Boolean = angleInDegrees) {
        if (expr.isEmpty()) {
            resultText = "0"
            return
        }
        val value = tryEvaluate(expr, degrees)
        if (value != null) {
            resultText = formatNumber(value)
        }
        // If evaluation fails, leave the previous preview showing rather than
        // flashing "Error" on every normal incomplete-expression keystroke.
    }

    fun setExpression(newExpr: String) {
        expression = newExpr
        updatePreview(newExpr)
    }

    fun append(token: String) {
        setExpression(expression + token)
    }

    fun backspace() {
        setExpression(if (expression.isNotEmpty()) expression.substring(0, expression.length - 1) else expression)
    }

    fun clearAll() {
        expression = ""
        resultText = "0"
    }

    fun wrapInNegation() {
        setExpression("-($expression)")
    }

    fun commitResult() {
        val value = tryEvaluate(expression) ?: return
        setExpression(formatNumber(value))
    }

    @Composable
    fun RowScope.Key(
        text: String,
        kind: CalcKind,
        size: Float,
        weight: Float = 1f,
        onClick: () -> Unit
    ) {
        CalcKey(
            text = text,
            onClick = onClick,
            modifier = Modifier.weight(weight).fillMaxHeight().padding(4.dp),
            kind = kind,
            fontSize = size
        )
    }

    Column(Modifier.fillMaxSize().background(MP.Background)) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(MP.Surface)
                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 18.dp)
        ) {
            BasicText(
                text = expression,
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 2,
                style = TextStyle(
                    color = MP.TextSecondary,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End
                )
            )
            BasicText(
                text = resultText,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                style = TextStyle(
                    color = MP.TextPrimary,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End
                )
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)
        ) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                Key(if (angleInDegrees) "DEG" else "RAD", CalcKind.FUNCTION, 12f) {
                    angleInDegrees = !angleInDegrees
                    updatePreview(expression, angleInDegrees)
                }
                Key("(", CalcKind.FUNCTION, 16f) { append("(") }
                Key(")", CalcKind.FUNCTION, 16f) { append(")") }
                Key("⌫", CalcKind.FUNCTION, 16f) { backspace() }
                Key("AC", CalcKind.FUNCTION, 13f) { clearAll() }
            }
            Row(Modifier.fillMaxWidth().weight(1f)) {
                Key("sin", CalcKind.FUNCTION, 13f) { append("sin(") }
                Key("cos", CalcKind.FUNCTION, 13f) { append("cos(") }
                Key("tan", CalcKind.FUNCTION, 13f) { append("tan(") }
                Key("x²", CalcKind.FUNCTION, 15f) { append("^2") }
                Key("√", CalcKind.FUNCTION, 17f) { append("sqrt(") }
            }
            Row(Modifier.fillMaxWidth().weight(1f)) {
                Key("asin", CalcKind.FUNCTION, 12f) { append("asin(") }
                Key("acos", CalcKind.FUNCTION, 12f) { append("acos(") }
                Key("atan", CalcKind.FUNCTION, 12f) { append("atan(") }
                Key("xʸ", CalcKind.FUNCTION, 15f) { append("^") }
                Key("1/x", CalcKind.FUNCTION, 13f) { append("^(-1)") }
            }
            Row(Modifier.fillMaxWidth().weight(1f)) {
                Key("log", CalcKind.FUNCTION, 13f) { append("log(") }
                Key("ln", CalcKind.FUNCTION, 13f) { append("ln(") }
                Key("!", CalcKind.FUNCTION, 17f) { append("!") }
                Key("π", CalcKind.FUNCTION, 17f) { append("pi") }
                Key("e", CalcKind.FUNCTION, 17f) { append("e") }
            }
            Row(Modifier.fillMaxWidth().weight(1f)) {
                Key("7", CalcKind.NUMBER, 20f) { append("7") }
                Key("8", CalcKind.NUMBER, 20f) { append("8") }
                Key("9", CalcKind.NUMBER, 20f) { append("9") }
                Key("÷", CalcKind.OPERATOR, 20f) { append("/") }
                Key("%", CalcKind.OPERATOR, 17f) { append("%") }
            }
            Row(Modifier.fillMaxWidth().weight(1f)) {
                Key("4", CalcKind.NUMBER, 20f) { append("4") }
                Key("5", CalcKind.NUMBER, 20f) { append("5") }
                Key("6", CalcKind.NUMBER, 20f) { append("6") }
                Key("×", CalcKind.OPERATOR, 20f) { append("*") }
                Key("±", CalcKind.OPERATOR, 17f) { wrapInNegation() }
            }
            Row(Modifier.fillMaxWidth().weight(1f)) {
                Key("1", CalcKind.NUMBER, 20f) { append("1") }
                Key("2", CalcKind.NUMBER, 20f) { append("2") }
                Key("3", CalcKind.NUMBER, 20f) { append("3") }
                Key("−", CalcKind.OPERATOR, 20f) { append("-") }
                Key("=", CalcKind.EQUALS, 20f) { commitResult() }
            }
            Row(Modifier.fillMaxWidth().weight(1f)) {
                Key("0", CalcKind.NUMBER, 20f, weight = 2f) { append("0") }
                Key(".", CalcKind.NUMBER, 20f) { append(".") }
                Key("+", CalcKind.OPERATOR, 20f, weight = 2f) { append("+") }
            }
        }
    }
}
