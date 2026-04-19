package com.batchrenamer

import android.net.Uri
import java.io.File

data class ImageItem(
    val id: Long,
    val uri: Uri,
    val path: String,
    val name: String,
    val size: Long,
    var detectedNumber: Int? = null,
    var isSelected: Boolean = true
) {
    fun getFile(): File = File(path)
    
    fun getExtension(): String = name.substringAfterLast('.', "")
    
    fun getNameWithoutExtension(): String = name.substringBeforeLast('.', "")
    
    fun getSizeString(): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> String.format("%.1f MB", size / (1024.0 * 1024.0))
        }
    }
}
