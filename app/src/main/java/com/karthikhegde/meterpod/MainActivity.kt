package com.karthikhegde.meterpod

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateListOf
import com.karthikhegde.meterpod.compose.AppRoot
import com.karthikhegde.meterpod.compose.MeterPodTheme
import com.karthikhegde.meterpod.compose.Navigator

class MainActivity : ComponentActivity() {

    private lateinit var navigator: Navigator

    /** Files handed in via Open with / Share; the File Viewer screen drains this list. */
    private val incomingFiles = mutableStateListOf<Uri>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val saved = savedInstanceState?.getStringArrayList(STATE_STACK)
        navigator = Navigator(if (saved.isNullOrEmpty()) listOf(Navigator.HOME) else saved)

        setContent {
            MeterPodTheme {
                AppRoot(navigator, incomingFiles)
            }
        }

        if (savedInstanceState == null) {
            // Launched via "Open with" / Share: go straight to the File Viewer.
            handleExternalIntent(intent)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(STATE_STACK, navigator.snapshot())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternalIntent(intent)
    }

    /** Files shared to the app (Open with / Share) are shown in the File Viewer tool. */
    private fun handleExternalIntent(i: Intent?) {
        val uris = urisFromIntent(i)
        if (uris.isEmpty()) return
        incomingFiles.addAll(uris)
        if (navigator.current != Navigator.FILE_VIEWER) navigator.push(Navigator.FILE_VIEWER)
    }

    private companion object {
        const val STATE_STACK = "nav_stack"
    }
}
