package com.ebooksplayer.shelf.data.repository

import com.ebooksplayer.shelf.data.db.dao.HighlightDao
import com.ebooksplayer.shelf.data.db.entity.Highlight
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class HighlightRepository @Inject constructor(
    private val highlightDao: HighlightDao,
) {
    fun observeForBook(bookId: Long): Flow<List<Highlight>> = highlightDao.observeForBook(bookId)

    suspend fun addHighlight(bookId: Long, locatorJson: String, colorArgb: Int, note: String?): Long =
        highlightDao.insert(
            Highlight(
                bookId = bookId,
                locatorJson = locatorJson,
                colorArgb = colorArgb,
                note = note,
                createdAt = System.currentTimeMillis(),
            ),
        )

    suspend fun deleteHighlight(highlight: Highlight) = highlightDao.delete(highlight)
}
