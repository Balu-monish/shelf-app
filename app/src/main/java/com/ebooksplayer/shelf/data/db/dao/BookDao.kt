package com.ebooksplayer.shelf.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ebooksplayer.shelf.data.db.entity.Book
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY dateAdded DESC")
    fun observeAll(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :bookId")
    suspend fun getById(bookId: Long): Book?

    @Query("SELECT * FROM books WHERE id = :bookId")
    fun observeById(bookId: Long): Flow<Book?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: Book): Long

    @Update
    suspend fun update(book: Book)

    @Query("UPDATE books SET coverUri = :coverUri WHERE id = :bookId")
    suspend fun updateCoverUri(bookId: Long, coverUri: String)

    @Query("SELECT EXISTS(SELECT 1 FROM books WHERE driveFileId = :driveFileId)")
    suspend fun existsWithDriveFileId(driveFileId: String): Boolean

    @Delete
    suspend fun delete(book: Book)
}
