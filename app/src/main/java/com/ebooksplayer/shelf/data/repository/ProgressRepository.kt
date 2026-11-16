package com.ebooksplayer.shelf.data.repository

import com.ebooksplayer.shelf.data.db.dao.ProgressDao
import com.ebooksplayer.shelf.data.db.entity.AudiobookProgress
import com.ebooksplayer.shelf.data.db.entity.EbookProgress
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Singleton
class ProgressRepository @Inject constructor(
    private val progressDao: ProgressDao,
) {
    suspend fun getAudiobookProgress(bookId: Long): AudiobookProgress? =
        progressDao.observeAudiobookProgress(bookId).first()

    fun observeAllAudiobookProgress(): Flow<List<AudiobookProgress>> =
        progressDao.observeAllAudiobookProgress()

    suspend fun saveAudiobookProgress(progress: AudiobookProgress) =
        progressDao.upsertAudiobookProgress(progress)

    suspend fun getEbookProgress(bookId: Long): EbookProgress? =
        progressDao.observeEbookProgress(bookId).first()

    fun observeAllEbookProgress(): Flow<List<EbookProgress>> =
        progressDao.observeAllEbookProgress()

    suspend fun saveEbookProgress(progress: EbookProgress) =
        progressDao.upsertEbookProgress(progress)
}
