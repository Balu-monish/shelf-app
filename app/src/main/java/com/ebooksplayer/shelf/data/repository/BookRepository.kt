package com.ebooksplayer.shelf.data.repository

import android.net.Uri
import com.ebooksplayer.shelf.data.db.dao.BookDao
import com.ebooksplayer.shelf.data.db.entity.Book
import com.ebooksplayer.shelf.data.db.entity.BookFormat
import com.ebooksplayer.shelf.data.importer.BookImporter
import com.ebooksplayer.shelf.data.importer.ImportResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class BookRepository @Inject constructor(
    private val bookDao: BookDao,
    private val bookImporter: BookImporter,
) {
    fun observeBooks(): Flow<List<Book>> = bookDao.observeAll()

    fun observeBook(bookId: Long): Flow<Book?> = bookDao.observeById(bookId)

    suspend fun getBook(bookId: Long): Book? = bookDao.getById(bookId)

    suspend fun importBook(uri: Uri, driveFileId: String? = null): ImportResult =
        bookImporter.import(uri, driveFileId)

    suspend fun hasBookWithDriveFileId(driveFileId: String): Boolean =
        bookDao.existsWithDriveFileId(driveFileId)

    suspend fun insertDriveStub(
        title: String,
        format: BookFormat,
        driveFileId: String,
        driveSizeBytes: Long?,
        coverUri: String? = null,
    ): Long = bookDao.insert(
        Book(
            title = title,
            author = null,
            coverUri = coverUri,
            format = format,
            fileUri = null,
            dateAdded = System.currentTimeMillis(),
            driveFileId = driveFileId,
            driveSizeBytes = driveSizeBytes,
            isDownloaded = false,
        ),
    )

    suspend fun updateBook(book: Book) = bookDao.update(book)

    /** A stub sourced from a plain HTTPS URL rather than Drive (e.g. a preloaded sample). */
    suspend fun insertDownloadableStub(
        title: String,
        author: String?,
        format: BookFormat,
        downloadUrl: String,
        coverUri: String? = null,
    ): Long = bookDao.insert(
        Book(
            title = title,
            author = author,
            coverUri = coverUri,
            format = format,
            fileUri = null,
            dateAdded = System.currentTimeMillis(),
            downloadUrl = downloadUrl,
            isDownloaded = false,
        ),
    )
}
