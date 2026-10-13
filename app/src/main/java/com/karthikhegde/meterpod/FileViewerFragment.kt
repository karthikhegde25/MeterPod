package com.karthikhegde.meterpod

import android.app.Activity
import android.content.res.Configuration
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Multi File Viewer: a bundled web app (assets/fileviewer/index.html, ~60 formats decoded by
 * small JS parsers) hosted in a WebView. The page is served from
 * https://appassets.androidplatform.net (a secure context, needed for hashing and certificate
 * features) and never touches the network - the app has no INTERNET permission.
 *
 * Files can be opened from the page's own picker, or handed in from outside through
 * [newInstance] / [addUris] ("Open with" and Share), which streams them to the page.
 */
class FileViewerFragment : Fragment() {

    private var web: WebView? = null
    private var chooserCallback: ValueCallback<Array<Uri>>? = null
    private var pendingSave: ByteArray? = null
    private val shared = CopyOnWriteArrayList<Uri>()
    private val queue = mutableListOf<Int>()
    private var pageReady = false

    private val chooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val picked = WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data)
        chooserCallback?.onReceiveValue(picked)
        chooserCallback = null
    }

    private val saver = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uri = r.data?.data
        val bytes = pendingSave
        pendingSave = null
        if (r.resultCode == Activity.RESULT_OK && uri != null && bytes != null) {
            try {
                requireContext().contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                toast("Saved")
            } catch (e: Exception) {
                toast("Could not save: ${e.message}")
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        val webView = WebView(ctx)
        web = webView
        webView.setBackgroundColor(if (night) 0xFF12161C.toInt() else 0xFFF4F6F9.toInt())
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            displayZoomControls = false
        }

        val loader = WebViewAssetLoader.Builder()
            .setDomain(HOST)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
            .build()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val u = request.url
                if (u.host == HOST && u.path?.startsWith("/shared/") == true) return sharedResponse(u)
                return loader.shouldInterceptRequest(u)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                if (u.host == HOST) return false
                if (u.scheme == "http" || u.scheme == "https" || u.scheme == "mailto") {
                    try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (_: Exception) {}
                }
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                pageReady = true
                flush()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                cb: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                chooserCallback?.onReceiveValue(null)
                chooserCallback = cb
                return try {
                    chooser.launch(params.createIntent())
                    true
                } catch (e: Exception) {
                    chooserCallback = null
                    false
                }
            }

            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                AlertDialog.Builder(requireContext())
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                    .setOnCancelListener { result.cancel() }
                    .show()
                return true
            }

            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                AlertDialog.Builder(requireContext())
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }
                    .show()
                return true
            }
        }

        webView.addJavascriptInterface(Bridge(), "AndroidBridge")
        webView.loadUrl("https://$HOST/assets/fileviewer/index.html")

        @Suppress("DEPRECATION")
        val initial: List<Uri> = arguments?.getParcelableArrayList<Uri>(ARG_URIS) ?: emptyList()
        addUris(initial)
        return webView
    }

    override fun onDestroyView() {
        web?.destroy()
        web = null
        pageReady = false
        super.onDestroyView()
    }

    /** Queues files handed over from outside (Open with / Share); shown as soon as the page is ready. */
    fun addUris(uris: List<Uri>) {
        for (u in uris) {
            shared.add(u)
            queue.add(shared.size - 1)
        }
        flush()
    }

    /** Called from the page (window.AndroidBridge.save) to let the user pick where to store a file. */
    inner class Bridge {
        @JavascriptInterface
        fun save(name: String, mime: String, base64: String) {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            activity?.runOnUiThread {
                pendingSave = bytes
                saver.launch(
                    Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType(mime.ifEmpty { "application/octet-stream" })
                        .putExtra(Intent.EXTRA_TITLE, name)
                )
            }
        }
    }

    /** Streams a shared file to the page without loading it into memory twice. */
    private fun sharedResponse(u: Uri): WebResourceResponse? {
        val i = u.lastPathSegment?.toIntOrNull() ?: return null
        val uri = shared.getOrNull(i) ?: return null
        val resolver = context?.contentResolver ?: return null
        return try {
            WebResourceResponse(
                resolver.getType(uri) ?: "application/octet-stream", null, 200, "OK",
                mapOf("Access-Control-Allow-Origin" to "*"),
                resolver.openInputStream(uri)
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun flush() {
        val webView = web ?: return
        if (!pageReady) return
        val resolver = context?.contentResolver ?: return
        while (queue.isNotEmpty()) {
            val idx = queue.removeAt(0)
            val uri = shared[idx]
            val name = displayName(uri)
            val mime = resolver.getType(uri) ?: ""
            webView.evaluateJavascript(
                "window.addFromAndroid('https://$HOST/shared/$idx',${JSONObject.quote(name)},${JSONObject.quote(mime)})",
                null
            )
        }
    }

    private fun displayName(u: Uri): String {
        try {
            context?.contentResolver?.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getString(0)
                    if (!n.isNullOrEmpty()) return n
                }
            }
        } catch (_: Exception) {
        }
        return u.lastPathSegment?.substringAfterLast('/') ?: "file"
    }

    private fun toast(m: String) {
        context?.let { Toast.makeText(it, m, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        private const val HOST = "appassets.androidplatform.net"
        private const val ARG_URIS = "uris"

        fun newInstance(uris: List<Uri> = emptyList()): FileViewerFragment =
            FileViewerFragment().apply {
                arguments = Bundle().apply { putParcelableArrayList(ARG_URIS, ArrayList(uris)) }
            }

        /** Extracts the file Uris from an "Open with" (VIEW) or Share (SEND / SEND_MULTIPLE) intent. */
        @Suppress("DEPRECATION")
        fun urisFrom(i: Intent?): List<Uri> {
            if (i == null) return emptyList()
            return when (i.action) {
                Intent.ACTION_VIEW -> listOfNotNull(i.data)
                Intent.ACTION_SEND -> listOfNotNull(
                    if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    else i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                )
                Intent.ACTION_SEND_MULTIPLE ->
                    (if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    else i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)) ?: emptyList<Uri>()
                else -> emptyList()
            }
        }
    }
}
