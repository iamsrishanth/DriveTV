package com.example.drivetvapp.drive

data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long = 0,
    val thumbnailLink: String? = null,
    val isFolder: Boolean = false,
    val isSubtitle: Boolean = false
) {
    val nameWithoutExtension: String
        get() = name.substringBeforeLast('.')
}
