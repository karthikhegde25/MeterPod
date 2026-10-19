package com.karthikhegde.meterpod

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.karthikhegde.meterpod.compose.MP
import com.karthikhegde.meterpod.compose.RealButton
import com.karthikhegde.meterpod.compose.SectionLabel
import com.karthikhegde.meterpod.compose.aboutCardBackground
import com.karthikhegde.meterpod.tools.ToolCatalog

private const val repoUrl = "https://github.com/karthikhegde25/MeterPod"
private const val licenseUrl = "https://www.gnu.org/licenses/gpl-3.0.html#license-text"
private const val imageToolboxUrl = "https://github.com/T8RIN/ImageToolbox"

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        Toast.makeText(context, "No app found to open this link", Toast.LENGTH_SHORT).show()
    }
}

private class Highlight(val emoji: String, val title: String, val body: String)

private val highlights = listOf(
    Highlight("📏", "Measure", "Ruler, protractor, angle, level, compass, speed, odometer, pedometer and a magnifier."),
    Highlight("📡", "Sense", "Sound level, light and magnetic field with live graphs, metal detector and a full sensor tester."),
    Highlight("⏱", "Time & Record", "Clock, timer and stopwatch, plus a long-recording audio recorder with a live waveform."),
    Highlight("🗂", "Files & Data", "File viewer, file creator and converter for text, data and images, QR generator and word counter."),
    Highlight("🎮", "Games", "2048, Tic-Tac-Toe, Minesweeper, Snake, Number Puzzle, Maze Munch, Flappy Bird and Space Invaders.")
)

@Composable
private fun Chip(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Color(0x1FFFFFFF), RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color(0xFFC4D4F5), fontSize = 11.sp)
    }
}

/** Full "About" screen (opened from the ⋮ menu): app info, what's inside, developer, license, source and credits. */
@Composable
fun AboutScreen() {
    val context = LocalContext.current

    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }
    val toolCount = remember { ToolCatalog.all().size }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MP.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Hero
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF1B3F94), Color(0xFF2F6FE0), Color(0xFF4C8DFF))))
                .padding(start = 18.dp, end = 18.dp, top = 26.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(100.dp).background(Color(0x33FFFFFF), CircleShape).border(1.dp, Color(0x66FFFFFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // adaptive launcher icon: painterResource can't load it, so host a plain ImageView
                AndroidView(
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            setImageResource(R.mipmap.ic_launcher)
                            contentDescription = "MeterPod logo"
                        }
                    },
                    modifier = Modifier.size(80.dp).clip(CircleShape)
                )
            }
            Text("MeterPod", modifier = Modifier.padding(top = 14.dp), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                "Version $version",
                modifier = Modifier.padding(top = 6.dp).background(Color(0x33FFFFFF), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 3.dp),
                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold
            )
            Text(
                "A pocket toolbox of measuring tools, sensors, converters, utilities and games, all in one small offline app.",
                modifier = Modifier.padding(top = 12.dp),
                color = Color(0xFFE3ECFF), fontSize = 14.sp, textAlign = TextAlign.Center
            )
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("tools in one app", toolCount.toString(), Modifier.weight(1f))
            }
        }

        // What's inside
        SectionLabel("What's inside")
        Column(Modifier.fillMaxWidth().aboutCardBackground().padding(vertical = 6.dp, horizontal = 14.dp)) {
            highlights.forEachIndexed { i, h ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.size(40.dp).background(MP.AccentSoft, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) { Text(h.emoji, fontSize = 20.sp) }
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(h.title, color = MP.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(h.body, modifier = Modifier.padding(top = 2.dp), color = MP.TextSecondary, fontSize = 12.5.sp)
                    }
                }
                if (i < highlights.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF2A3A62)))
            }
        }

        // Developer
        SectionLabel("Developer")
        Row(
            modifier = Modifier.fillMaxWidth().aboutCardBackground().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(46.dp).background(MP.Accent, CircleShape), contentAlignment = Alignment.Center) {
                Text("K", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                text = "Karthik Hegde",
                modifier = Modifier.padding(start = 14.dp),
                color = MP.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // License
        SectionLabel("License")
        Column(Modifier.fillMaxWidth().aboutCardBackground().padding(16.dp)) {
            Text("GNU General Public License v3.0", color = MP.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "You are free to use, study, share and modify this app under the terms of the GPL 3.",
                modifier = Modifier.padding(top = 4.dp), color = MP.TextSecondary, fontSize = 13.sp
            )
            RealButton(
                text = "Read the license",
                onClick = { openUrl(context, licenseUrl) },
                modifier = Modifier.padding(top = 12.dp),
                primary = true,
                compact = true
            )
        }

        // Source code
        SectionLabel("Source code")
        Column(Modifier.fillMaxWidth().aboutCardBackground().padding(16.dp)) {
            Text("github.com/karthikhegde25/MeterPod", color = MP.Accent, fontSize = 14.sp)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                RealButton(
                    text = "Open repository",
                    onClick = { openUrl(context, repoUrl) },
                    modifier = Modifier.padding(end = 10.dp),
                    primary = true,
                    compact = true
                )
                RealButton(
                    text = "Report an issue",
                    onClick = { openUrl(context, "$repoUrl/issues") },
                    primary = false,
                    compact = true
                )
            }
        }

        // Credits
        SectionLabel("Credits")
        Column(Modifier.fillMaxWidth().aboutCardBackground().padding(16.dp)) {
            Text("QR Generator", color = MP.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                "Content types and code formats are adapted from Image Toolbox's QR code feature. Thanks to the Image Toolbox project and its contributors. Encoding by ZXing.",
                modifier = Modifier.padding(top = 2.dp), color = MP.TextSecondary, fontSize = 13.sp
            )
            RealButton(
                text = "Image Toolbox",
                onClick = { openUrl(context, imageToolboxUrl) },
                modifier = Modifier.padding(top = 10.dp),
                primary = false,
                compact = true
            )
            Text("Games", modifier = Modifier.padding(top = 16.dp), color = MP.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                "2048, Tic-Tac-Toe, Minesweeper, Snake and the sliding puzzle follow their classic rules. Maze Munch is an original game inspired by classic maze-chase arcade games.",
                modifier = Modifier.padding(top = 2.dp), color = MP.TextSecondary, fontSize = 13.sp
            )
        }

        Text(
            text = "Works fully offline.",
            modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 8.dp),
            color = MP.TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}
