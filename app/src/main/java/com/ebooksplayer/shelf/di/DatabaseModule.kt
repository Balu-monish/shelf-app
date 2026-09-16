package com.ebooksplayer.shelf.di

import android.content.Context
import androidx.room.Room
import com.ebooksplayer.shelf.data.db.AppDatabase
import com.ebooksplayer.shelf.data.db.dao.BookDao
import com.ebooksplayer.shelf.data.db.dao.BookmarkDao
import com.ebooksplayer.shelf.data.db.dao.CollectionDao
import com.ebooksplayer.shelf.data.db.dao.HighlightDao
import com.ebooksplayer.shelf.data.db.dao.ProgressDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideBookDao(database: AppDatabase): BookDao = database.bookDao()

    @Provides
    fun provideProgressDao(database: AppDatabase): ProgressDao = database.progressDao()

    @Provides
    fun provideBookmarkDao(database: AppDatabase): BookmarkDao = database.bookmarkDao()

    @Provides
    fun provideCollectionDao(database: AppDatabase): CollectionDao = database.collectionDao()

    @Provides
    fun provideHighlightDao(database: AppDatabase): HighlightDao = database.highlightDao()
}
