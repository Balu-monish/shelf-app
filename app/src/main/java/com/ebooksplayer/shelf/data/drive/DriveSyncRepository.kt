package com.ebooksplayer.shelf.data.drive

import android.content.Context
import android.net.Uri
import android.util.Log
import com.ebooksplayer.shelf.data.db.entity.BookFormat
import com.ebooksplayer.shelf.data.download.ContentDownloader
import com.ebooksplayer.shelf.data.importer.CoverStorage
import com.ebooksplayer.shelf.data.importer.EpubMetadataExtractor
import com.ebooksplayer.shelf.data.importer.M4bMetadataExtractor
import com.ebooksplayer.shelf.data.repository.BookRepository
import com.ebooksplayer.shelf.data.settings.AppSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "DriveSyncRepository"

data class DriveSyncSummary(
    val imported: Int,
    val skipped: Int,
    val failed: Int,
)

sealed interface DriveSyncOutcome {
    data class Completed(val summary: DriveSyncSummary) : DriveSyncOutcome
    data object AuthorizationFailed : DriveSyncOutcome
    data class Failure(val reason: String) : DriveSyncOutcome
}

sealed interface DriveDownloadOutcome {
    data object Success : DriveDownloadOutcome
    data object AuthorizationFailed : DriveDownloadOutcome
    data class Failure(val reason: String) : DriveDownloadOutcome
}

/**
 * Folder names come from AppSettingsRepository (user-editable in Settings,
 * defaulting to the three sync-to-drive.ps1 folders). sync() only *lists*
 * what's there and records lightweight stub rows (title = filename, nothing
 * else) - it deliberately never downloads file content just to build the
 * library listing, to avoid pulling potentially many GB of audiobooks onto
 * the device unasked. Fetching a given book's actual content is a separate,
 * explicit per-book action: downloadBook() - which also handles
 * non-Drive stubs (Book.downloadUrl) such as preloaded samples.
 */
@Singleton
class DriveSyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: DriveAuthManager,
    private val driveApi: DriveApi,
    private val bookRepository: BookRepository,
    private val settingsRepository: AppSettingsRepository,
    private val contentDownloader: ContentDownloader,
    private val epubExtractor: EpubMetadataExtractor,
    private val m4bExtractor: M4bMetadataExtractor,
    private val coverStorage: CoverStorage,
) {
    suspend fun sync(): DriveSyncOutcome = try {
        val accessToken = authManager.ensureAccessToken()
            ?: return DriveSyncOutcome.AuthorizationFailed

        var imported = 0
        var skipped = 0
        var failed = 0

        for (mapping in settingsRepository.driveFolders.value) {
            val folderId = runCatching { driveApi.findFolderIdByName(accessToken, mapping.folderName) }
                .onFailure { Log.w(TAG, "Failed to find folder '${mapping.folderName}'", it) }
                .getOrNull() ?: continue

            val files = runCatching { collectFiles(accessToken, folderId, mapping.format) }
                .onFailure { Log.w(TAG, "Failed to list files in '${mapping.folderName}'", it) }
                .getOrDefault(emptyList())

            for (file in files) {
                val result = runCatching { registerStub(file, mapping.format) }
                    .onFailure { Log.w(TAG, "Failed to register '${file.name}'", it) }
                    .getOrNull()

                when (result) {
                    StubResult.REGISTERED -> imported++
                    StubResult.SKIPPED -> skipped++
                    null -> failed++
                }
            }
        }

        DriveSyncOutcome.Completed(DriveSyncSummary(imported, skipped, failed))
    } catch (e: Exception) {
        Log.e(TAG, "Drive sync failed", e)
        DriveSyncOutcome.Failure(e.message ?: "Unknown error")
    }

    /**
     * Downloads one previously-listed book's actual content (from Drive if
     * [com.ebooksplayer.shelf.data.db.entity.Book.driveFileId] is set, or a
     * plain HTTPS GET if [com.ebooksplayer.shelf.data.db.entity.Book.downloadUrl]
     * is set instead), extracts its real metadata (same extractors local
     * imports use), and hydrates the stub row in place. No-op if it's
     * already downloaded.
     */
    suspend fun downloadBook(bookId: Long): DriveDownloadOutcome = try {
        val book = bookRepository.getBook(bookId)
            ?: return DriveDownloadOutcome.Failure("Book not found")
        if (book.isDownloaded) return DriveDownloadOutcome.Success

        val extension = if (book.format == BookFormat.EPUB) ".epub" else ".m4b"
        val destination: File

        when {
            book.driveFileId != null -> {
                val accessToken = authManager.ensureAccessToken()
                    ?: return DriveDownloadOutcome.AuthorizationFailed
                destination = File(downloadsDir(), "${book.driveFileId}$extension")
                if (!driveApi.downloadFile(accessToken, book.driveFileId, destination)) {
                    destination.delete()
                    return DriveDownloadOutcome.Failure("Download failed")
                }
            }
            book.downloadUrl != null -> {
                destination = File(downloadsDir(), "book_${book.id}$extension")
                if (!contentDownloader.download(book.downloadUrl, destination)) {
                    destination.delete()
                    return DriveDownloadOutcome.Failure("Download failed")
                }
            }
            else -> return DriveDownloadOutcome.Failure("Nothing to download")
        }

        val hydrated = withContext(Dispatchers.IO) {
            val fileUri = Uri.fromFile(destination)
            val metadata = when (book.format) {
                BookFormat.EPUB -> epubExtractor.extract(fileUri, book.title)
                BookFormat.M4B -> m4bExtractor.extract(fileUri, book.title)
            }
            val coverUri = metadata.coverBytes?.let { bytes -> coverStorage.save(book.id, bytes) }

            book.copy(
                title = metadata.title,
                author = metadata.author ?: book.author,
                coverUri = coverUri ?: book.coverUri,
                fileUri = fileUri.toString(),
                durationMs = metadata.durationMs,
                isDownloaded = true,
                series = metadata.series,
                seriesIndex = metadata.seriesIndex,
            )
        }

        bookRepository.updateBook(hydrated)
        DriveDownloadOutcome.Success
    } catch (e: Exception) {
        // A stale partial download (if any) is harmless: it's named by
        // book/Drive file id and gets overwritten on the next retry.
        Log.e(TAG, "Download failed for book $bookId", e)
        DriveDownloadOutcome.Failure(e.message ?: "Unknown error")
    }

    private enum class StubResult { REGISTERED, SKIPPED }

    private suspend fun registerStub(file: DriveEntry, format: BookFormat): StubResult {
        if (bookRepository.hasBookWithDriveFileId(file.id)) {
            return StubResult.SKIPPED
        }
        bookRepository.insertDriveStub(
            title = file.name.substringBeforeLast('.').ifBlank { file.name },
            format = format,
            driveFileId = file.id,
            driveSizeBytes = file.sizeBytes,
            coverUri = file.thumbnailLink,
        )
        return StubResult.REGISTERED
    }

    private suspend fun collectFiles(
        accessToken: String,
        folderId: String,
        format: BookFormat,
    ): List<DriveEntry> {
        val extension = if (format == BookFormat.EPUB) ".epub" else ".m4b"
        val result = mutableListOf<DriveEntry>()
        val toVisit = ArrayDeque<String>()
        toVisit.add(folderId)

        while (toVisit.isNotEmpty()) {
            val currentFolder = toVisit.removeFirst()
            for (child in driveApi.listChildren(accessToken, currentFolder)) {
                if (child.isFolder) {
                    toVisit.add(child.id)
                } else if (child.name.endsWith(extension, ignoreCase = true)) {
                    result += child
                }
            }
        }
        return result
    }

    private fun downloadsDir(): File =
        File(context.filesDir, "drive_books").apply { mkdirs() }
}
