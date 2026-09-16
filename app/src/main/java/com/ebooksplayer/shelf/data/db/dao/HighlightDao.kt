package com.ebooksplayer.shelf.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.ebooksplayer.shelf.data.db.entity.Highlight
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {
    @Query("SELECT * FROM highlights WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observeForBook(bookId: Long): Flow<List<Highlight>>

    @Insert
    suspend fun insert(highlight: Highlight): Long

    @Delete
    suspend fun delete(highlight: Highlight)
}
