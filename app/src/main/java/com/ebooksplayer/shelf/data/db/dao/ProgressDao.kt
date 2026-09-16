package com.ebooksplayer.shelf.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ebooksplayer.shelf.data.db.entity.AudiobookProgress
import com.ebooksplayer.shelf.data.db.entity.EbookProgress
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {
    @Query("SELECT * FROM ebook_progress WHERE bookId = :bookId")
    fun observeEbookProgress(bookId: Long): Flow<EbookProgress?>

    @Query("SELECT * FROM ebook_progress")
    fun observeAllEbookProgress(): Flow<List<EbookProgress>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEbookProgress(progress: EbookProgress)

    @Query("SELECT * FROM audiobook_progress WHERE bookId = :bookId")
    fun observeAudiobookProgress(bookId: Long): Flow<AudiobookProgress?>

    @Query("SELECT * FROM audiobook_progress")
    fun observeAllAudiobookProgress(): Flow<List<AudiobookProgress>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAudiobookProgress(progress: AudiobookProgress)
}
