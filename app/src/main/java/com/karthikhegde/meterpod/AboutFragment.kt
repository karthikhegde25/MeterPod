package com.karthikhegde.meterpod

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.karthikhegde.meterpod.tools.ToolCatalog

/** Full "About" screen (opened from the ⋮ menu): app info, developer, license, source and credits. */
class AboutFragment : Fragment() {

    private val repoUrl = "https://github.com/karthikhegde25/MeterPod"
    private val licenseUrl = "https://www.gnu.org/licenses/gpl-3.0.html#license-text"
    private val imageToolboxUrl = "https://github.com/T8RIN/ImageToolbox"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_about, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val version = try {
            val ctx = requireContext()
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
        view.findViewById<TextView>(R.id.versionText).text = "Version $version"
        view.findViewById<TextView>(R.id.toolCountText).text = "${ToolCatalog.all().size} tools in one app"

        view.findViewById<View>(R.id.licenseButton).setOnClickListener { open(licenseUrl) }
        view.findViewById<View>(R.id.repoButton).setOnClickListener { open(repoUrl) }
        view.findViewById<View>(R.id.issuesButton).setOnClickListener { open("$repoUrl/issues") }
        view.findViewById<View>(R.id.imageToolboxButton).setOnClickListener { open(imageToolboxUrl) }
    }

    private fun open(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "No app found to open this link", Toast.LENGTH_SHORT).show()
        }
    }
}
