package com.ebooksplayer.shelf.data.repository

import com.ebooksplayer.shelf.data.db.dao.CollectionDao
import com.ebooksplayer.shelf.data.db.entity.BookCollectionCrossRef
import com.ebooksplayer.shelf.data.db.entity.Collection
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class CollectionRepository @Inject constructor(
    private val collectionDao: CollectionDao,
) {
    fun observeAll(): Flow<List<Collection>> = collectionDao.observeAll()

    suspend fun createCollection(name: String): Long =
        collectionDao.insert(Collection(name = name, createdAt = System.currentTimeMillis()))

    fun observeCollectionIdsForBook(bookId: Long): Flow<List<Long>> =
        collectionDao.observeCollectionIdsForBook(bookId)

    fun observeBookIdsInCollection(collectionId: Long): Flow<List<Long>> =
        collectionDao.observeBookIdsInCollection(collectionId)

    suspend fun addBookToCollection(bookId: Long, collectionId: Long) =
        collectionDao.addBookToCollection(BookCollectionCrossRef(bookId, collectionId))

    suspend fun removeBookFromCollection(bookId: Long, collectionId: Long) =
        collectionDao.removeBookFromCollection(bookId, collectionId)
}
