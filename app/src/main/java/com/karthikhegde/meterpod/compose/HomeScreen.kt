package com.karthikhegde.meterpod.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.karthikhegde.meterpod.tools.ToolCatalog
import com.karthikhegde.meterpod.tools.ToolItem

private val categories = listOf("All", "Measure", "Sensors", "Utilities", "Files", "Games")

private fun categoryOf(id: String): String = when (id) {
    "angle", "level", "compass", "protractor", "ruler", "speed", "odometer", "pedometer", "magnifier" -> "Measure"
    "metal_detector", "sound_level", "light_detector", "magnetic_field", "accelerometer", "sensor_tester", "screen_info", "network_info" -> "Sensors"
    "file_creator", "file_converter", "file_viewer" -> "Files"
    "games" -> "Games"
    else -> "Utilities"
}

private fun accentOf(cat: String): Color = when (cat) {
    "Measure" -> Color(0xFF4C8DFF)
    "Sensors" -> Color(0xFF2DD4BF)
    "Utilities" -> Color(0xFFFFB020)
    "Files" -> Color(0xFFA78BFA)
    else -> Color(0xFFFF6B9A)
}


/** Kept for the life of the process so coming back to Home resumes at the same scroll position. */
private val homeListState = androidx.compose.foundation.lazy.LazyListState()

/** Home screen: just the tool cards (colour-coded by category), with no text above them. */
@Composable
fun HomeScreen(onToolClick: (ToolItem) -> Unit) {
    val tools = remember { ToolCatalog.all() }

    LazyColumn(
        state = homeListState,
        modifier = Modifier.fillMaxSize().background(MP.Background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 24.dp)
    ) {
        items(tools.chunked(2), key = { row -> "r_" + row.first().id }) { row ->
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t -> ToolCard(t, accentOf(categoryOf(t.id)), Modifier.weight(1f)) { onToolClick(t) } }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}



@Composable
private fun ToolCard(tool: ToolItem, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = if (pressed) 0.26f else 0.16f), MP.Surface)))
            .border(1.dp, accent.copy(alpha = if (pressed) 0.8f else 0.28f), RoundedCornerShape(20.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(46.dp).background(accent.copy(alpha = 0.18f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(factory = { ctx -> tool.createIconView(ctx) }, modifier = Modifier.size(34.dp))
        }
        BasicText(
            tool.title,
            modifier = Modifier.padding(start = 10.dp).weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = MP.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        )
    }
}



