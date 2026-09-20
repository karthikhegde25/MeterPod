package com.karthikhegde.meterpod

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import com.karthikhegde.meterpod.tools.ToolItem

class MainActivity : AppCompatActivity() {

    private val repoUrl = "https://github.com/karthikhegde25/MeterPod"
    private val licenseUrl = "https://www.gnu.org/licenses/gpl-3.0.html#license-text"
    private val imageToolboxUrl = "https://github.com/T8RIN/ImageToolbox"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, HomeFragment())
                .commit()
        }

        findViewById<ImageButton>(R.id.overflowButton).setOnClickListener { anchor ->
            showOverflowMenu(anchor)
        }
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

    private fun showOverflowMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.main_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_about) {
                showAboutDialog()
                true
            } else {
                false
            }
        }
        popup.show()
    }

    private fun showAboutDialog() {
        val message = HtmlCompat.fromHtml(
            "<b>MeterPod</b><br><br>" +
                "Developer: Karthik Hegde<br><br>" +
                "MeterPod is open source and vibe-coded.<br><br>" +
                "Registered under GPL 3 license: <a href=\"$licenseUrl\">$licenseUrl</a><br><br>" +
                "Repo: <a href=\"$repoUrl\">$repoUrl</a><br><br>" +
                "The QR Generator tool's content types and code formats are adapted from " +
                "<a href=\"$imageToolboxUrl\">Image Toolbox</a>'s QR code feature. Thanks to the " +
                "Image Toolbox project and its contributors.",
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )

        val dialog = AlertDialog.Builder(this)
            .setTitle("About")
            .setMessage(message)
            .setPositiveButton("Open repo") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repoUrl)))
            }
            .setNegativeButton("Close", null)
            .create()

        dialog.show()
        dialog.findViewById<TextView>(android.R.id.message)?.movementMethod = LinkMovementMethod.getInstance()
    }
}
