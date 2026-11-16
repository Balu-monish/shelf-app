package com.ebooksplayer.shelf.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.ebooksplayer.shelf.data.db.dao.BookDao
import com.ebooksplayer.shelf.data.db.dao.BookmarkDao
import com.ebooksplayer.shelf.data.db.dao.CollectionDao
import com.ebooksplayer.shelf.data.db.dao.HighlightDao
import com.ebooksplayer.shelf.data.db.dao.ProgressDao
import com.ebooksplayer.shelf.data.db.entity.AudiobookProgress
import com.ebooksplayer.shelf.data.db.entity.Book
import com.ebooksplayer.shelf.data.db.entity.BookCollectionCrossRef
import com.ebooksplayer.shelf.data.db.entity.BookFormat
import com.ebooksplayer.shelf.data.db.entity.Bookmark
import com.ebooksplayer.shelf.data.db.entity.Collection
import com.ebooksplayer.shelf.data.db.entity.EbookProgress
import com.ebooksplayer.shelf.data.db.entity.Highlight

class Converters {
    @TypeConverter
    fun fromBookFormat(format: BookFormat): String = format.name

    @TypeConverter
    fun toBookFormat(value: String): BookFormat = BookFormat.valueOf(value)
}

@Database(
    entities = [
        Book::class,
        EbookProgress::class,
        AudiobookProgress::class,
        Bookmark::class,
        Collection::class,
        BookCollectionCrossRef::class,
        Highlight::class,
    ],
    version = 6,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun progressDao(): ProgressDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun collectionDao(): CollectionDao
    abstract fun highlightDao(): HighlightDao

    companion object {
        const val DATABASE_NAME = "shelf.db"
    }
}
