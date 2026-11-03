package com.example.core.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

data class SavedApkResult(
    val uri: Uri,
    val displayPath: String,
    val sizeBytes: Long
)

class ApkExportHelper(private val context: Context) {

    /**
     * Extracts the currently running compiled APK from applicationInfo.sourceDir
     * and saves it into the user's Downloads folder as VideoX_AI_v1.0.0.apk using MediaStore.
     */
    fun saveInstalledApkToDownloads(): Result<SavedApkResult> {
        return runCatching {
            val sourceApkPath = context.applicationInfo.sourceDir
            val sourceApkFile = File(sourceApkPath)
            if (!sourceApkFile.exists() || sourceApkFile.length() <= 0L) {
                error("Compiled APK file not found at $sourceApkPath")
            }

            val fileName = "VideoX_AI_v1.0.0.apk"
            val resolver = context.contentResolver

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val insertedUri = resolver.insert(collection, values)
                if (insertedUri != null) {
                    resolver.openOutputStream(insertedUri)?.use { output ->
                        sourceApkFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    } ?: error("Could not open Downloads output stream.")

                    val completeValues = ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    }
                    resolver.update(insertedUri, completeValues, null, null)

                    return@runCatching SavedApkResult(
                        uri = insertedUri,
                        displayPath = "Downloads/$fileName",
                        sizeBytes = sourceApkFile.length()
                    )
                }
            }

            // Fallback to app external files directory if MediaStore Downloads is unavailable
            val fallbackDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val destFile = File(fallbackDir, fileName)
            sourceApkFile.copyTo(destFile, overwrite = true)

            SavedApkResult(
                uri = Uri.fromFile(destFile),
                displayPath = destFile.absolutePath,
                sizeBytes = destFile.length()
            )
        }
    }
}
