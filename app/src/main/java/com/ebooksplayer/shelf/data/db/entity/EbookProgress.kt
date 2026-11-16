package com.ebooksplayer.shelf.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "ebook_progress",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class EbookProgress(
    @PrimaryKey val bookId: Long,
    val locatorJson: String,
    val percentage: Float,
    val lastReadAt: Long,
)
