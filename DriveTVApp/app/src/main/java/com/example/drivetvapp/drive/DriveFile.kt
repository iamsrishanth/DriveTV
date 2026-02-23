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

data class DriveFileListResponse(
    val files: List<DriveFileJson>,
    val nextPageToken: String? = null
)

data class DriveFileJson(
    val id: String?,
    val name: String?,
    val mimeType: String?,
    val size: String?,
    val thumbnailLink: String?
) {
    fun toDriveFile(): DriveFile = DriveFile(
        id = id ?: "",
        name = name ?: "Unknown",
        mimeType = mimeType ?: "",
        size = size?.toLongOrNull() ?: 0,
        thumbnailLink = thumbnailLink,
        isFolder = mimeType == "application/vnd.google-apps.folder"
    )
}
