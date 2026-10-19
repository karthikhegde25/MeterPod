package com.karthikhegde.meterpod.compose

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karthikhegde.meterpod.AboutScreen
import com.karthikhegde.meterpod.FileViewerScreen
import com.karthikhegde.meterpod.R
import com.karthikhegde.meterpod.SensorDetailScreen
import com.karthikhegde.meterpod.SensorTesterListScreen
import com.karthikhegde.meterpod.UnitConverterCategoryScreen
import com.karthikhegde.meterpod.UnitConverterHomeScreen
import com.karthikhegde.meterpod.tools.ToolCatalog

/**
 * Replaces the FragmentManager back stack. Routes are plain strings so the stack can be saved in
 * onSaveInstanceState:
 *   "home" | "about" | "tool:<toolId>" | "converter:<categoryId>" | "sensor:<type>|<name>"
 */
class Navigator(initial: List<String>) {
    val stack: SnapshotStateList<String> = mutableStateListOf<String>().also { it.addAll(initial) }

    val current: String get() = stack.last()

    fun push(route: String) {
        stack.add(route)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun snapshot(): ArrayList<String> = ArrayList(stack)

    companion object {
        const val HOME = "home"
        const val ABOUT = "about"
        const val FILE_VIEWER = "tool:file_viewer"
    }
}

/** Header bar (activity_main.xml) + the current screen. */
@Composable
fun AppRoot(navigator: Navigator, incomingFiles: SnapshotStateList<Uri>) {
    BackHandler(enabled = navigator.stack.size > 1) { navigator.pop() }

    androidx.compose.runtime.CompositionLocalProvider(LocalOnBack provides { navigator.pop() }) {
    Column(Modifier.fillMaxSize().background(MP.Background)) {
        val route0 = navigator.current
        val appBarTitle: String? = when {
            route0 == Navigator.HOME -> null
            route0 == Navigator.ABOUT -> "About"
            route0.startsWith("tool:") -> ToolCatalog.all().firstOrNull { it.id == route0.removePrefix("tool:") }?.title
            route0.startsWith("converter:") -> "Unit Converter"
            route0.startsWith("sensor:") -> "Sensor Tester"
            else -> null
        }
        AppBar(title = appBarTitle, onAbout = {
            // MainActivity.openAbout(): ignore if About is already showing
            if (navigator.current != Navigator.ABOUT) navigator.push(Navigator.ABOUT)
        })
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val route = navigator.current
            when {
                route == Navigator.HOME -> HomeScreen(onToolClick = { navigator.push("tool:${it.id}") })
                route == Navigator.ABOUT -> AboutScreen()
                route.startsWith("tool:") -> {
                    val id = route.removePrefix("tool:")
                    when (id) {
                        "unit_converter" -> UnitConverterHomeScreen(
                            onOpenCategory = { navigator.push("converter:${it.id}") }
                        )
                        "sensor_tester" -> SensorTesterListScreen(
                            onOpenSensor = { type, name -> navigator.push("sensor:$type|$name") }
                        )
                        "file_viewer" -> FileViewerScreen(incomingFiles)
                        else -> {
                            val tool = remember(id) { ToolCatalog.all().firstOrNull { it.id == id } }
                            if (tool != null) tool.content()
                        }
                    }
                }
                route.startsWith("converter:") ->
                    UnitConverterCategoryScreen(route.removePrefix("converter:"))
                route.startsWith("sensor:") -> {
                    val rest = route.removePrefix("sensor:")
                    val type = rest.substringBefore('|').toIntOrNull()
                    val name = rest.substringAfter('|', "")
                    if (type != null) SensorDetailScreen(type, name)
                }
            }
        }
    }
    }
}

@Composable
private fun AppBar(title: String?, onAbout: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val dispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    Row(
        Modifier
            .fillMaxWidth()
            .background(MP.Background)
            .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (title != null) {
            // back arrow; goes through the system dispatcher so tools can ask "leave?" first
            Box(
                Modifier.size(44.dp).clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { dispatcher?.onBackPressed() },
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
                    val sw = 2.4.dp.toPx()
                    val c = MP.TextPrimary
                    drawLine(c, Offset(size.width * 0.9f, size.height / 2), Offset(size.width * 0.1f, size.height / 2), sw, androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(c, Offset(size.width * 0.1f, size.height / 2), Offset(size.width * 0.48f, size.height * 0.12f), sw, androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(c, Offset(size.width * 0.1f, size.height / 2), Offset(size.width * 0.48f, size.height * 0.88f), sw, androidx.compose.ui.graphics.StrokeCap.Round)
                }
            }
        } else {
            Box(Modifier.size(16.dp))
        }
        BasicText(
            text = title ?: "MeterPod",
            modifier = Modifier.weight(1f).padding(start = if (title != null) 4.dp else 0.dp),
            maxLines = 1,
            style = TextStyle(
                color = MP.TextPrimary,
                fontSize = if (title != null) 19.sp else 22.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold
            )
        )
        Box {
            Box(
                Modifier
                    .size(46.dp)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { menuOpen = true },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_more_vert),
                    contentDescription = "More options",
                    colorFilter = ColorFilter.tint(MP.TextPrimary),
                    modifier = Modifier.size(24.dp)
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier.background(MP.SurfaceElevated)
            ) {
                DropdownMenuItem(
                    text = { Text("About", color = MP.TextPrimary) },
                    onClick = { menuOpen = false; onAbout() }
                )
            }
        }
    }
}
