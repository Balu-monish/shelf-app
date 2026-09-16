package com.ebooksplayer.shelf.data.drive

data class DriveEntry(
    val id: String,
    val name: String,
    val isFolder: Boolean,
    val sizeBytes: Long? = null,
    /** Rarely present for EPUB/M4B - Drive only auto-generates these for file types it can preview. */
    val thumbnailLink: String? = null,
)
