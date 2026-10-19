package com.karthikhegde.meterpod

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karthikhegde.meterpod.compose.MP
import com.karthikhegde.meterpod.compose.MeterTextField
import com.karthikhegde.meterpod.compose.TileCard
import com.karthikhegde.meterpod.converter.UnitCatalog
import com.karthikhegde.meterpod.converter.UnitCategory
import com.karthikhegde.meterpod.converter.UnitDefinition
import kotlin.math.abs

private enum class Side { LEFT, RIGHT }

private fun formatResult(value: Double): String {
    if (value.isNaN()) return ""
    if (value.isInfinite()) return if (value > 0) "∞" else "-∞"
    if (value == 0.0) return "0"

    val absValue = abs(value)
    return if (absValue < 0.0001 || absValue >= 1.0e9) {
        String.format("%.6e", value)
    } else {
        var text = String.format("%.6f", value)
        if (text.contains(".")) text = text.trimEnd('0').trimEnd('.')
        text
    }
}

/**
 * Two-sided unit converter for one category: pick a unit on each side, type
 * a number into either field, and the other side updates live.
 *
 * The two text fields each react to edits, and each would
 * normally re-trigger the other's handler when it programmatically sets
 * text - the isUpdating guard flag prevents that feedback loop.
 */
@Composable
fun UnitConverterCategoryScreen(categoryId: String) {
    // Original returned early (blank screen) when the category was unknown.
    val category = UnitCatalog.byId(categoryId)
    if (category == null) {
        Box(Modifier.fillMaxSize().background(MP.Background))
        return
    }
    ConverterContent(category)
}

@Composable
private fun ConverterContent(category: UnitCategory) {
    var leftUnit by remember { mutableStateOf(category.units[0]) }
    var rightUnit by remember { mutableStateOf(category.units.getOrElse(1) { category.units[0] }) }

    // leftValueInput.setText("1") on creation: left is the edited side, right is computed from it.
    var leftText by remember { mutableStateOf("1") }
    var rightText by remember {
        mutableStateOf(
            formatResult(
                category.units.getOrElse(1) { category.units[0] }
                    .fromBase(category.units[0].toBase(1.0))
            )
        )
    }

    // Plain (non-state) flag, like the original field: only read/written synchronously.
    val isUpdating = remember { booleanArrayOf(false) }
    var lastEditedSide by remember { mutableStateOf(Side.LEFT) }

    var pickerForLeft by remember { mutableStateOf<Boolean?>(null) }

    fun recompute(side: Side = lastEditedSide, left: UnitDefinition = leftUnit, right: UnitDefinition = rightUnit) {
        isUpdating[0] = true
        try {
            if (side == Side.LEFT) {
                val input = leftText.toDoubleOrNull()
                if (input != null) {
                    val base = left.toBase(input)
                    rightText = formatResult(right.fromBase(base))
                } else {
                    rightText = ""
                }
            } else {
                val input = rightText.toDoubleOrNull()
                if (input != null) {
                    val base = right.toBase(input)
                    leftText = formatResult(left.fromBase(base))
                } else {
                    leftText = ""
                }
            }
        } finally {
            isUpdating[0] = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MP.Background)
            .padding(20.dp)
    ) {
        BasicText(
            text = category.name,
            modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally),
            style = TextStyle(color = MP.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        )

        Row(Modifier.fillMaxWidth().padding(top = 28.dp)) {
            // Left side
            TileCard(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp)
            ) {
                Column(Modifier.fillMaxWidth()) {
                    BasicText(
                        text = "${leftUnit.displayName} (${leftUnit.symbol}) ▾",
                        modifier = Modifier
                            .clickable { pickerForLeft = true }
                            .padding(4.dp),
                        style = TextStyle(color = MP.Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    )
                    MeterTextField(
                        value = leftText,
                        onValueChange = { newValue ->
                            leftText = newValue
                            if (!isUpdating[0]) {
                                lastEditedSide = Side.LEFT
                                recompute(Side.LEFT)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        hint = "0",
                        textSize = 24f,
                        singleLine = true,
                        contentPadding = PaddingValues(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            }

            // 12dp gap (layout_marginStart on the right tile; both tiles keep equal widths)
            Spacer(Modifier.width(12.dp))

            // Right side
            TileCard(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp)
            ) {
                Column(Modifier.fillMaxWidth()) {
                    BasicText(
                        text = "${rightUnit.displayName} (${rightUnit.symbol}) ▾",
                        modifier = Modifier
                            .clickable { pickerForLeft = false }
                            .padding(4.dp),
                        style = TextStyle(color = MP.Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    )
                    MeterTextField(
                        value = rightText,
                        onValueChange = { newValue ->
                            rightText = newValue
                            if (!isUpdating[0]) {
                                lastEditedSide = Side.RIGHT
                                recompute(Side.RIGHT)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        hint = "0",
                        textSize = 24f,
                        singleLine = true,
                        contentPadding = PaddingValues(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            }
        }

        BasicText(
            text = "Type a number on either side — the other side converts live. Tap a unit name to change it.",
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            style = TextStyle(color = MP.TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
        )
    }

    val picking = pickerForLeft
    if (picking != null) {
        AlertDialog(
            onDismissRequest = { pickerForLeft = null },
            containerColor = MP.SurfaceElevated,
            title = { Text("Select unit", color = MP.TextPrimary) },
            text = {
                LazyColumn {
                    itemsIndexed(category.units) { _, unit ->
                        Text(
                            text = "${unit.displayName} (${unit.symbol})",
                            color = MP.TextPrimary,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    pickerForLeft = null
                                    if (picking) leftUnit = unit else rightUnit = unit
                                    recompute(
                                        left = if (picking) unit else leftUnit,
                                        right = if (picking) rightUnit else unit
                                    )
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }
}
