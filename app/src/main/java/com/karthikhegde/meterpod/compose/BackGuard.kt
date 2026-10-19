package com.karthikhegde.meterpod.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Pops the current screen. Provided by AppRoot so a tool can leave after its own "discard?" confirmation. */
val LocalOnBack = staticCompositionLocalOf<() -> Unit> { {} }

/** "Leave and lose the data?" dialog used by tools that hold unsaved work (Time, Recorder). */
@Composable
fun LeaveConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    cancelText: String = "Stay",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MP.SurfaceElevated,
        title = { BasicText(title, style = TextStyle(color = MP.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)) },
        text = { BasicText(message, style = TextStyle(color = MP.TextSecondary, fontSize = 14.sp)) },
        confirmButton = {
            RealButton(confirmText, onClick = onConfirm, compact = true, textColor = MP.Needle)
        },
        dismissButton = {
            RealButton(cancelText, onClick = onDismiss, compact = true, primary = true)
        }
    )
}
