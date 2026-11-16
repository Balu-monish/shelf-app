package com.ebooksplayer.shelf.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ebooksplayer.shelf.data.db.entity.BookCollectionCrossRef
import com.ebooksplayer.shelf.data.db.entity.Collection
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Collection>>

    @Insert
    suspend fun insert(collection: Collection): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addBookToCollection(crossRef: BookCollectionCrossRef)

    @Query("DELETE FROM book_collection_cross_ref WHERE bookId = :bookId AND collectionId = :collectionId")
    suspend fun removeBookFromCollection(bookId: Long, collectionId: Long)

    @Query("SELECT collectionId FROM book_collection_cross_ref WHERE bookId = :bookId")
    fun observeCollectionIdsForBook(bookId: Long): Flow<List<Long>>

    @Query("SELECT bookId FROM book_collection_cross_ref WHERE collectionId = :collectionId")
    fun observeBookIdsInCollection(collectionId: Long): Flow<List<Long>>
}
