package com.ebooksplayer.shelf.data.importer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pulls title/author/artwork/duration out of an M4B using the platform's
 * MediaMetadataRetriever, which already understands MP4/M4A/M4B 'ilst'
 * tags. Chapter-list extraction is added in Phase 2 alongside the player.
 */
@Singleton
class M4bMetadataExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun extract(uri: Uri, fallbackTitle: String): ExtractedMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.trim()?.takeIf { it.isNotEmpty() } ?: fallbackTitle
            val author = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
            val coverBytes = retriever.embeddedPicture

            ExtractedMetadata(title = title, author = author, coverBytes = coverBytes, durationMs = durationMs)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read M4B metadata for $uri", e)
            ExtractedMetadata(title = fallbackTitle, author = null, coverBytes = null)
        } finally {
            retriever.release()
        }
    }

    companion object {
        private const val TAG = "M4bMetadataExtractor"
    }
}
