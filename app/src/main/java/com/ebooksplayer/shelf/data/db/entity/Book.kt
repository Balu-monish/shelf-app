package com.ebooksplayer.shelf.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class BookFormat { EPUB, M4B }

@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String?,
    val coverUri: String?,
    val format: BookFormat,
    /** Null until [isDownloaded] is true - a Drive stub has nothing to point at yet. */
    val fileUri: String?,
    val dateAdded: Long,
    val durationMs: Long? = null,
    val pageCount: Int? = null,
    /** Google Drive file id, set only for books imported via Drive sync. */
    val driveFileId: String? = null,
    /** Drive-reported file size, shown on the Download button before fetching it. */
    val driveSizeBytes: Long? = null,
    /**
     * True for local SAF imports (always fully present) and for Drive books
     * once downloaded. False only for a Drive stub row that's been listed
     * but not yet downloaded - has a title (filename) but no file, cover,
     * author, or duration yet.
     */
    val isDownloaded: Boolean = true,
    /** From EPUB Calibre metadata only; never set for M4B (no standard tag exists). */
    val series: String? = null,
    val seriesIndex: Float? = null,
    /**
     * A plain HTTPS source for a not-yet-downloaded stub that isn't from
     * Drive (e.g. a preloaded sample) - downloaded with a bare GET, no
     * OAuth token. Mutually exclusive with [driveFileId] in practice.
     */
    val downloadUrl: String? = null,
)
