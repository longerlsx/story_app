package com.longerlsx.storyapp.feature.importer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.IntentCompat
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ExternalImportPayload(
    val fileName: String,
    val bytes: ByteArray,
)

object ExternalImportHandler {
    suspend fun extractPayload(
        context: Context,
        intent: Intent?,
    ): ExternalImportPayload? = prepareImport(context, intent)?.getOrNull()

    suspend fun prepareImport(
        context: Context,
        intent: Intent?,
    ): Result<ExternalImportPayload>? {
        if (intent == null) {
            return null
        }

        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        } ?: return null

        return prepareImport(context, uri, intent.type)
    }

    suspend fun prepareImport(
        context: Context,
        uri: Uri,
        mimeType: String? = null,
    ): Result<ExternalImportPayload> = withContext(Dispatchers.IO) {
        try {
            val fileName = context.resolveDisplayName(uri)
            if (!isSupportedTxt(fileName, mimeType ?: context.resolveMimeType(uri))) {
                return@withContext Result.failure(IllegalArgumentException("请选择 TXT 文本文件。"))
            }
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IOException("文件无法打开")
            if (bytes.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("文件没有可阅读的内容。"))
            }
            Result.success(ExternalImportPayload(fileName, bytes))
        } catch (failure: IOException) {
            Result.failure(unreadableFile(failure))
        } catch (failure: IllegalArgumentException) {
            Result.failure(unreadableFile(failure))
        } catch (failure: SecurityException) {
            Result.failure(unreadableFile(failure))
        }
    }

    private fun unreadableFile(cause: Exception): IOException =
        IOException("无法读取文件，请重新选择文件并允许访问。", cause)

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
                    cursor.getString(nameIndex)?.takeIf(String::isNotBlank)?.let { return it }
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
