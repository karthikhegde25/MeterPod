package com.karthikhegde.meterpod

import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karthikhegde.meterpod.compose.MP

/** Lists every sensor SensorManager reports for this device (Sensor.TYPE_ALL). */
@Composable
fun SensorTesterListScreen(onOpenSensor: (type: Int, name: String) -> Unit) {
    val context = LocalContext.current
    val sensors = remember {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        sensorManager.getSensorList(Sensor.TYPE_ALL).sortedBy { it.name }
    }

    Column(Modifier.fillMaxSize().background(MP.Background)) {
        BasicText(
            text = "Tap a sensor to see its live readings.",
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            style = TextStyle(color = MP.TextSecondary, fontSize = 13.sp)
        )
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(sensors, key = { it.type.toString() + it.name + it.isWakeUpSensor + it.vendor + it.version }) { sensor ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSensor(sensor.type, sensor.name + "|" + (if (sensor.isWakeUpSensor) "1" else "0")) }
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
                ) {
                    BasicText(
                        text = sensor.name,
                        style = TextStyle(
                            color = MP.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    BasicText(
                        // stringType is API 20+; minSdk here is 24, so it's always available.
                        text = sensor.stringType + if (sensor.isWakeUpSensor) "  (wake-up)" else "",
                        modifier = Modifier.padding(top = 2.dp),
                        style = TextStyle(color = MP.TextSecondary, fontSize = 12.sp)
                    )
                }
            }
        }
    }
}
