package com.ebooksplayer.shelf.data.repository

import com.ebooksplayer.shelf.data.db.dao.BookmarkDao
import com.ebooksplayer.shelf.data.db.entity.Bookmark
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class BookmarkRepository @Inject constructor(
    private val bookmarkDao: BookmarkDao,
) {
    fun observeForBook(bookId: Long): Flow<List<Bookmark>> = bookmarkDao.observeForBook(bookId)

    suspend fun addBookmark(bookId: Long, position: String, note: String? = null): Long =
        bookmarkDao.insert(
            Bookmark(bookId = bookId, position = position, note = note, createdAt = System.currentTimeMillis()),
        )

    suspend fun deleteBookmark(bookmark: Bookmark) = bookmarkDao.delete(bookmark)
}
