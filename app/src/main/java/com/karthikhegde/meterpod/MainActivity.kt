package com.karthikhegde.meterpod

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import com.karthikhegde.meterpod.tools.ToolItem

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, HomeFragment())
                .commit()
            // Launched via "Open with" / Share: go straight to the File Viewer.
            handleExternalIntent(intent)
        }

        findViewById<ImageButton>(R.id.overflowButton).setOnClickListener { anchor ->
            showOverflowMenu(anchor)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternalIntent(intent)
    }

    /** Called by HomeFragment when a tile is tapped. */
    fun navigateToTool(tool: ToolItem) {
        pushFragment(tool.createFragment(), tool.id)
    }

    /** General-purpose navigation for any fragment, including nested screens within a tool. */
    fun pushFragment(fragment: androidx.fragment.app.Fragment, tag: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(tag)
            .commit()
    }

    /** Files shared to the app (Open with / Share) are shown in the File Viewer tool. */
    private fun handleExternalIntent(i: Intent?) {
        val uris = FileViewerFragment.urisFrom(i)
        if (uris.isEmpty()) return
        val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        if (current is FileViewerFragment) {
            current.addUris(uris)
        } else {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, FileViewerFragment.newInstance(uris))
                .addToBackStack("file_viewer")
                .commitAllowingStateLoss()
        }
    }

    private fun showOverflowMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.main_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_about) {
                openAbout()
                true
            } else {
                false
            }
        }
        popup.show()
    }

    private fun openAbout() {
        val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        if (current is AboutFragment) return
        pushFragment(AboutFragment(), "about")
    }
}
