package com.karthikhegde.meterpod

import android.app.Activity
import android.app.AlertDialog
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

private const val FV_HOST = "appassets.androidplatform.net"

/** Extracts the file Uris from an "Open with" (VIEW) or Share (SEND / SEND_MULTIPLE) intent. */
@Suppress("DEPRECATION")
fun urisFromIntent(i: Intent?): List<Uri> {
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

/**
 * State + behaviour of the File Viewer (was the body of FileViewerFragment): owns the WebView,
 * the queue of files handed in from outside, and the chooser / save plumbing.
 */
private class FileViewerController(private val ctx: Context) {
    var web: WebView? = null
    var chooserCallback: ValueCallback<Array<Uri>>? = null
    var pendingSave: ByteArray? = null
    val shared = CopyOnWriteArrayList<Uri>()
    val queue = mutableListOf<Int>()
    var pageReady = false

    var launchChooser: ((Intent) -> Unit)? = null
    var launchSaver: ((Intent) -> Unit)? = null

    fun toast(m: String) {
        Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()
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
            (ctx as? Activity)?.runOnUiThread {
                pendingSave = bytes
                launchSaver?.invoke(
                    Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType(mime.ifEmpty { "application/octet-stream" })
                        .putExtra(Intent.EXTRA_TITLE, name)
                )
            }
        }
    }

    /** Streams a shared file to the page without loading it into memory twice. */
    fun sharedResponse(u: Uri): WebResourceResponse? {
        val i = u.lastPathSegment?.toIntOrNull() ?: return null
        val uri = shared.getOrNull(i) ?: return null
        val resolver = ctx.contentResolver
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

    fun flush() {
        val webView = web ?: return
        if (!pageReady) return
        val resolver = ctx.contentResolver
        while (queue.isNotEmpty()) {
            val idx = queue.removeAt(0)
            val uri = shared[idx]
            val name = displayName(uri)
            val mime = resolver.getType(uri) ?: ""
            webView.evaluateJavascript(
                "window.addFromAndroid('https://$FV_HOST/shared/$idx',${JSONObject.quote(name)},${JSONObject.quote(mime)})",
                null
            )
        }
    }

    private fun displayName(u: Uri): String {
        try {
            ctx.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getString(0)
                    if (!n.isNullOrEmpty()) return n
                }
            }
        } catch (_: Exception) {
        }
        return u.lastPathSegment?.substringAfterLast('/') ?: "file"
    }

    fun destroy() {
        web?.destroy()
        web = null
        pageReady = false
    }
}

/**
 * Multi File Viewer: a bundled web app (assets/fileviewer/index.html, ~60 formats decoded by
 * small JS parsers) hosted in a WebView. The page is served from
 * https://appassets.androidplatform.net (a secure context, needed for hashing and certificate
 * features) and never touches the network - the app has no INTERNET permission.
 *
 * Files can be opened from the page's own picker, or handed in from outside through
 * [incoming] ("Open with" and Share); entries are streamed to the page and removed from the list.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FileViewerScreen(incoming: SnapshotStateList<Uri>) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val controller = remember { FileViewerController(ctx) }

    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val picked = WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data)
        controller.chooserCallback?.onReceiveValue(picked)
        controller.chooserCallback = null
    }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uri = r.data?.data
        val bytes = controller.pendingSave
        controller.pendingSave = null
        if (r.resultCode == Activity.RESULT_OK && uri != null && bytes != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                controller.toast("Saved")
            } catch (e: Exception) {
                controller.toast("Could not save: ${e.message}")
            }
        }
    }
    controller.launchChooser = { chooser.launch(it) }
    controller.launchSaver = { saver.launch(it) }

    // Drain files handed in from outside (initial launch or onNewIntent).
    LaunchedEffect(incoming.size) {
        if (incoming.isNotEmpty()) {
            val batch = incoming.toList()
            incoming.clear()
            controller.addUris(batch)
        }
    }

    DisposableEffect(Unit) {
        onDispose { controller.destroy() }
    }

    // Creating a WebView is heavy; let the screen and its title draw first, then build it on a later frame.
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        androidx.compose.runtime.withFrameNanos { }
        androidx.compose.runtime.withFrameNanos { }
        ready = true
    }
    if (!ready) {
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize().background(com.karthikhegde.meterpod.compose.MP.Background),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            androidx.compose.foundation.text.BasicText(
                "Loading viewer...",
                style = androidx.compose.ui.text.TextStyle(color = com.karthikhegde.meterpod.compose.MP.TextSecondary)
            )
        }
        return
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

            val webView = WebView(context)
            controller.web = webView
            webView.setBackgroundColor(if (night) 0xFF12161C.toInt() else 0xFFF4F6F9.toInt())
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                displayZoomControls = false
            }

            val loader = WebViewAssetLoader.Builder()
                .setDomain(FV_HOST)
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .build()

            webView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val u = request.url
                    if (u.host == "cdnjs.cloudflare.com") localLib(context, u)?.let { return it }
                    if (u.host == FV_HOST && u.path?.startsWith("/shared/") == true) return controller.sharedResponse(u)
                    return loader.shouldInterceptRequest(u)
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val u = request.url
                    if (u.host == FV_HOST) return false
                    if (u.scheme == "http" || u.scheme == "https" || u.scheme == "mailto") {
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (_: Exception) {}
                    }
                    return true
                }

                override fun onPageFinished(view: WebView, url: String) {
                    controller.pageReady = true
                    controller.flush()
                }
            }

            webView.webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView,
                    cb: ValueCallback<Array<Uri>>,
                    params: FileChooserParams
                ): Boolean {
                    controller.chooserCallback?.onReceiveValue(null)
                    controller.chooserCallback = cb
                    return try {
                        controller.launchChooser?.invoke(params.createIntent())
                        true
                    } catch (e: Exception) {
                        controller.chooserCallback = null
                        false
                    }
                }

                override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                    AlertDialog.Builder(context)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                        .setOnCancelListener { result.cancel() }
                        .show()
                    return true
                }

                override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                    AlertDialog.Builder(context)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                        .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                        .setOnCancelListener { result.cancel() }
                        .show()
                    return true
                }
            }

            webView.addJavascriptInterface(controller.Bridge(), "AndroidBridge")
            webView.isVerticalScrollBarEnabled = true
            webView.overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
            webView.loadUrl("https://$FV_HOST/assets/fileviewer/index.html")
            webView
        }
    )
}

/** Serves the bundled copies of pdf.js and Mermaid (assets/fileviewer/libs) instead of the CDN, so they work offline. */
private fun localLib(context: android.content.Context, u: Uri): WebResourceResponse? {
    val path = u.path ?: return null
    val name = when {
        path.endsWith("/pdf.min.js") -> "pdf.min.js"
        path.endsWith("/pdf.worker.min.js") -> "pdf.worker.min.js"
        path.endsWith("/mermaid.min.js") -> "mermaid.min.js"
        else -> return null
    }
    return try {
        WebResourceResponse(
            "text/javascript", "utf-8", 200, "OK",
            mapOf("Access-Control-Allow-Origin" to "*"),
            context.assets.open("fileviewer/libs/$name")
        )
    } catch (e: Exception) {
        null
    }
}
