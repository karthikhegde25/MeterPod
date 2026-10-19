package com.karthikhegde.meterpod

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karthikhegde.meterpod.compose.MP
import com.karthikhegde.meterpod.compose.badgeSphere
import com.karthikhegde.meterpod.converter.UnitCatalog
import com.karthikhegde.meterpod.converter.UnitCategory

/** Badge sphere colour per list position (cycled), as in the original CategoryAdapter. */
private val badgeColors = listOf(
    "#3A7BD5", "#E53935", "#43A047", "#FB8C00", "#8E24AA", "#00897B"
).map { Color(android.graphics.Color.parseColor(it)) }

/** Unit Converter home: the list of quantity categories (Angle, Length, Mass, ...). */
@Composable
fun UnitConverterHomeScreen(onOpenCategory: (UnitCategory) -> Unit) {
    val categories = UnitCatalog.all
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MP.Background),
        contentPadding = PaddingValues(top = 12.dp)
    ) {
        itemsIndexed(categories) { position, category ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenCategory(category) }
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .badgeSphere(base = badgeColors[position % badgeColors.size]),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = category.badgeText,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    )
                }
                Column(Modifier.padding(start = 16.dp).weight(1f)) {
                    BasicText(
                        text = category.name,
                        style = TextStyle(
                            color = MP.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    BasicText(
                        text = "${category.units.size} units",
                        modifier = Modifier.padding(top = 2.dp),
                        style = TextStyle(color = MP.TextSecondary, fontSize = 12.sp)
                    )
                }
            }
        }
    }
}
