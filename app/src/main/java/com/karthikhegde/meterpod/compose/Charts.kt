package com.karthikhegde.meterpod.compose

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Fixed-size rolling window of recent samples. Call [add] from any callback on the main thread. */
class SeriesBuffer(val capacity: Int) {
    private val data = FloatArray(capacity)
    private var head = 0
    var count = 0
        private set

    /** Bumped on every change; the chart reads it while drawing so it redraws itself. */
    var version by mutableIntStateOf(0)
        private set

    fun add(v: Float) {
        data[head] = v
        head = (head + 1) % capacity
        if (count < capacity) count++
        version++
    }

    fun clear() {
        head = 0; count = 0; version++
    }

    /** i = 0 is the oldest retained sample. */
    operator fun get(i: Int): Float = data[(head - count + i + capacity * 2) % capacity]

    fun last(): Float? = if (count == 0) null else get(count - 1)
}

class ChartLine(val buffer: SeriesBuffer, val color: Color, val label: String = "")

/** "Nice" tick step (1, 2, 5 x 10^n) giving roughly [target] intervals over [span]. */
private fun niceStep(span: Float, target: Int): Float {
    val raw = span / target
    val mag = 10.0.pow(floor(log10(raw.toDouble()))).toFloat()
    val f = raw / mag
    val nice = when {
        f < 1.5f -> 1f
        f < 3.5f -> 2f
        f < 7.5f -> 5f
        else -> 10f
    }
    return nice * mag
}

/**
 * Live line chart over a rolling window.
 *
 * [yMin]/[yMax] fix the vertical range; leave them null to auto-fit the visible data (never narrower than
 * [minSpan], so sensor noise isn't blown up). [labelFor] formats the y-axis tick values; [fixedStep] forces
 * the tick spacing (used for the logarithmic light chart where ticks are whole powers of ten).
 */
@Composable
fun LineChart(
    lines: List<ChartLine>,
    modifier: Modifier = Modifier,
    yMin: Float? = null,
    yMax: Float? = null,
    minSpan: Float = 1f,
    fixedStep: Float? = null,
    labelFor: (Float) -> String = { v -> if (abs(v) >= 100f || v == floor(v)) String.format("%.0f", v) else String.format("%.1f", v) },
    zeroLine: Boolean = false
) {
    val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MP.TextSecondary.toArgb()
        typeface = Typeface.DEFAULT
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .background(MP.Surface, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
    ) {
        // Reading `version` here makes this draw block re-run whenever any series changes.
        lines.forEach { it.buffer.version }

        val textPx = 10.sp.toPx()
        axisPaint.textSize = textPx
        val padL = 40.dp.toPx(); val padR = 10.dp.toPx(); val padT = 10.dp.toPx(); val padB = 10.dp.toPx()
        val w = size.width - padL - padR
        val h = size.height - padT - padB
        if (w <= 0f || h <= 0f) return@Canvas

        // ----- vertical range
        var lo = yMin ?: Float.MAX_VALUE
        var hi = yMax ?: -Float.MAX_VALUE
        if (yMin == null || yMax == null) {
            var dlo = Float.MAX_VALUE; var dhi = -Float.MAX_VALUE
            for (ln in lines) for (i in 0 until ln.buffer.count) { val v = ln.buffer[i]; if (v < dlo) dlo = v; if (v > dhi) dhi = v }
            if (dlo > dhi) { dlo = 0f; dhi = minSpan }
            val span = max(dhi - dlo, minSpan)
            val mid = (dhi + dlo) / 2f
            if (yMin == null) lo = mid - span * 0.6f
            if (yMax == null) hi = mid + span * 0.6f
        }
        if (hi - lo < 1e-6f) hi = lo + 1f
        val step = fixedStep ?: niceStep(hi - lo, 4)
        if (yMin == null) lo = floor(lo / step) * step
        if (yMax == null) hi = ceil(hi / step) * step
        if (hi <= lo) hi = lo + step

        fun yOf(v: Float) = padT + h - (v - lo) / (hi - lo) * h

        // ----- grid + labels
        val gridColor = MP.SurfaceOutline
        var t = ceil(lo / step) * step
        var guard = 0
        while (t <= hi + step * 0.001f && guard++ < 40) {
            val y = yOf(t)
            drawLine(gridColor, Offset(padL, y), Offset(padL + w, y), strokeWidth = 1.dp.toPx())
            drawIntoCanvas { c ->
                axisPaint.textAlign = Paint.Align.RIGHT
                c.nativeCanvas.drawText(labelFor(t), padL - 5.dp.toPx(), y + textPx * 0.35f, axisPaint)
            }
            t += step
        }
        if (zeroLine && lo < 0f && hi > 0f) {
            drawLine(MP.TextSecondary.copy(alpha = 0.6f), Offset(padL, yOf(0f)), Offset(padL + w, yOf(0f)), strokeWidth = 1.5.dp.toPx())
        }

        // ----- lines
        for (ln in lines) {
            val n = ln.buffer.count
            if (n < 2) continue
            val cap = ln.buffer.capacity
            val path = Path()
            for (i in 0 until n) {
                val x = padL + w * (cap - n + i) / (cap - 1).toFloat()
                val y = yOf(ln.buffer[i].coerceIn(lo, hi))
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, ln.color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Small coloured legend / title row used above charts. */
@Composable
fun ChartTitle(text: String, color: Color, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, androidx.compose.foundation.shape.CircleShape))
        BasicText(
            text = text,
            modifier = Modifier.padding(start = 8.dp).weight(1f),
            style = TextStyle(color = MP.TextPrimary, fontSize = 13.sp)
        )
        BasicText(text = value, style = TextStyle(color = color, fontSize = 13.sp))
    }
}
