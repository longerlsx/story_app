package com.longerlsx.storyapp.feature.importer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.util.Locale

data class ExternalImportPayload(
    val fileName: String,
    val bytes: ByteArray,
)

object ExternalImportHandler {
    suspend fun extractPayload(
        context: Context,
        intent: Intent?,
    ): ExternalImportPayload? {
        if (intent == null) {
            return null
        }

        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        } ?: return null

        val fileName = context.resolveDisplayName(uri)
        val mimeType = intent.type ?: context.contentResolver.getType(uri)
        if (!isSupportedTxt(fileName = fileName, mimeType = mimeType)) {
            return null
        }

        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes()
        } ?: return null

        return ExternalImportPayload(
            fileName = fileName,
            bytes = bytes,
        )
    }

    private fun isSupportedTxt(
        fileName: String,
        mimeType: String?,
    ): Boolean {
        val normalizedName = fileName.lowercase(Locale.ROOT)
        if (normalizedName.endsWith(".txt")) {
            return true
        }

        return when (mimeType?.lowercase(Locale.ROOT)) {
            "text/plain",
            "text/*",
            "application/octet-stream",
            -> true

            else -> false
        }
    }
}

private fun Context.resolveDisplayName(uri: Uri): String {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0) {
                return cursor.getString(nameIndex)
            }
        }
    }

    return uri.lastPathSegment ?: "imported.txt"
}
