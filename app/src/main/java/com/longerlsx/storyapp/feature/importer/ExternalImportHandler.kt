package com.longerlsx.storyapp.feature.importer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException
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
        val mimeType = intent.type ?: context.resolveMimeType(uri)
        if (!isSupportedTxt(fileName = fileName, mimeType = mimeType)) {
            return null
        }

        val bytes = context.readUriBytesOrNull(uri) ?: return null

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
    try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    return cursor.getString(nameIndex)
                }
            }
        }
    } catch (_: IllegalArgumentException) {
        // Malformed or unsupported external URIs should not crash import handling.
    } catch (_: SecurityException) {
        // Missing URI grants are handled as a non-importable external payload.
    }

    return uri.lastPathSegment ?: "imported.txt"
}

private fun Context.resolveMimeType(uri: Uri): String? {
    return try {
        contentResolver.getType(uri)
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: SecurityException) {
        null
    }
}

private fun Context.readUriBytesOrNull(uri: Uri): ByteArray? {
    return try {
        contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes()
        }
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: SecurityException) {
        null
    }
}
