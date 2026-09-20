package com.karthikhegde.meterpod.qr

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/** Saves the generated QR bitmap to the gallery, or hands it to a share sheet. */
object QrFileSaver {

    /** Writes [bitmap] as a PNG into the device's Pictures/MeterPod folder. Returns true on success. */
    fun saveToGallery(context: Context, bitmap: Bitmap, displayName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MeterPod")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
                resolver.openOutputStream(uri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "MeterPod")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "$displayName.png")
                FileOutputStream(file).use { out: OutputStream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
                android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /** Writes [bitmap] to the app's cache and returns a content:// [Uri] suitable for sharing. */
    fun cacheForSharing(context: Context, bitmap: Bitmap, displayName: String): Uri {
        val cacheDir = File(context.cacheDir, "qr_share").apply { mkdirs() }
        val file = File(cacheDir, "$displayName.png")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun shareIntent(context: Context, bitmap: Bitmap, displayName: String): Intent {
        val uri = cacheForSharing(context, bitmap, displayName)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
