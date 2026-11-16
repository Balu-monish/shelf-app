package com.ebooksplayer.shelf.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "audiobook_progress",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AudiobookProgress(
    @PrimaryKey val bookId: Long,
    val chapterIndex: Int,
    val positionMs: Long,
    val playbackSpeed: Float = 1.0f,
    val lastPlayedAt: Long,
)
