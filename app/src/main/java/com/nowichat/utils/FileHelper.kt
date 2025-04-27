package com.nowichat.utils

import android.content.Context
import android.net.Uri
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.nowichat.models.FileType
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

object FileHelper {
    fun getFileType(context: Context, uri: Uri): FileType {
        val mimeType = context.contentResolver.getType(uri) ?: return FileType.FILE
        return when {
            mimeType.startsWith("image/") -> FileType.IMAGE
            mimeType.startsWith("video/") -> FileType.VIDEO
            else -> FileType.FILE
        }
    }

    fun copyFileToInternalStorage(context: Context, uri: Uri): String {
        val fileName = getFileName(context, uri)
        val file = File(context.filesDir, "${System.currentTimeMillis()}_$fileName")
        
        // Check available space
        val fileSize = (context.contentResolver.openInputStream(uri)?.available() ?: 0).toLong()
        val availableSpace = file.parentFile?.usableSpace ?: 0
        if (fileSize > availableSpace) {
            throw IOException("Not enough storage space. Need ${formatSize(fileSize)}, " +
                            "but only ${formatSize(availableSpace)} available")
        }
        
        // Check file size limit (e.g., 50MB)
        if (fileSize > 50 * 1024 * 1024) {
            throw IOException("File too large. Maximum size is 50MB")
        }

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IOException("Could not open input stream")
        } catch (e: Exception) {
            file.delete() // Clean up on failure
            throw e
        }
        
        return file.absolutePath
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }

    fun loadMediaPreview(context: Context, uri: String, imageView: ImageView) {
        Glide.with(context)
            .load(uri)
            .centerCrop()
            .into(imageView)
    }

    fun getFileSize(context: Context, uri: String): String {
        val file = File(uri)
        val size = file.length()
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> "${size / (1024 * 1024)} MB"
        }
    }

    fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val columnIndex = it.getColumnIndex("_display_name")
                    if (columnIndex != -1) {
                        result = it.getString(columnIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result ?: "unknown_file"
    }
}
