package com.ebooksplayer.shelf.data.importer

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.ebooksplayer.shelf.data.db.dao.BookDao
import com.ebooksplayer.shelf.data.db.entity.Book
import com.ebooksplayer.shelf.data.db.entity.BookFormat
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ImportResult {
    data class Success(val bookId: Long) : ImportResult
    data class UnsupportedFormat(val fileName: String) : ImportResult
    data class Failure(val reason: String) : ImportResult
}

@Singleton
class BookImporter @Inject constructor(
    private val contentResolver: ContentResolver,
    private val bookDao: BookDao,
    private val epubExtractor: EpubMetadataExtractor,
    private val m4bExtractor: M4bMetadataExtractor,
    private val coverStorage: CoverStorage,
) {
    suspend fun import(uri: Uri, driveFileId: String? = null): ImportResult {
        if (uri.scheme == "content") {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            runCatching { contentResolver.takePersistableUriPermission(uri, takeFlags) }
        }

        val displayName = queryDisplayName(uri) ?: uri.lastPathSegment ?: "Untitled"
        val format = formatFor(displayName) ?: return ImportResult.UnsupportedFormat(displayName)
        val fallbackTitle = displayName.substringBeforeLast('.')

        return try {
            val metadata = when (format) {
                BookFormat.EPUB -> epubExtractor.extract(uri, fallbackTitle)
                BookFormat.M4B -> m4bExtractor.extract(uri, fallbackTitle)
            }

            val bookId = bookDao.insert(
                Book(
                    title = metadata.title,
                    author = metadata.author,
                    coverUri = null,
                    format = format,
                    fileUri = uri.toString(),
                    dateAdded = System.currentTimeMillis(),
                    durationMs = metadata.durationMs,
                    driveFileId = driveFileId,
                    series = metadata.series,
                    seriesIndex = metadata.seriesIndex,
                ),
            )

            metadata.coverBytes?.let { bytes ->
                val coverUri = coverStorage.save(bookId, bytes)
                bookDao.updateCoverUri(bookId, coverUri)
            }

            ImportResult.Success(bookId)
        } catch (e: Exception) {
            ImportResult.Failure(e.message ?: "Unknown error")
        }
    }

    private fun formatFor(fileName: String): BookFormat? = when {
        fileName.endsWith(".epub", ignoreCase = true) -> BookFormat.EPUB
        fileName.endsWith(".m4b", ignoreCase = true) -> BookFormat.M4B
        else -> null
    }

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme != "content") return null
        return runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) cursor.getString(index) else null
                    } else {
                        null
                    }
                }
        }.getOrNull()
    }
}
