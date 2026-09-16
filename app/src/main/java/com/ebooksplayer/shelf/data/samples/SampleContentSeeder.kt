package com.ebooksplayer.shelf.data.samples

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import com.ebooksplayer.shelf.data.db.entity.BookFormat
import com.ebooksplayer.shelf.data.repository.BookRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "SampleContentSeeder"
private const val SAMPLE_AUDIOBOOK_URL =
    "https://archive.org/download/mashi_1110_librivox/MashiAndOtherStories_librivox.m4b"

/**
 * Seeds the library with the bundled sample content once, on first launch.
 * "1984" is a tiny real asset so it's imported (and fully readable)
 * immediately; the Mashi audiobook is 106 MB so only its metadata is
 * preloaded as a downloadUrl stub, same as a Drive stub, per the user's
 * explicit call to keep the app bundle small rather than ship the audio.
 */
@Singleton
class SampleContentSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("shelf_sample_seed", Context.MODE_PRIVATE)

    suspend fun seedIfNeeded() {
        if (prefs.getBoolean(KEY_SEEDED, false)) return

        withContext(Dispatchers.IO) {
            runCatching { seedEpub() }
                .onFailure { Log.w(TAG, "Failed to seed sample EPUB", it) }
            runCatching { seedAudiobookStub() }
                .onFailure { Log.w(TAG, "Failed to seed sample audiobook stub", it) }
        }

        prefs.edit { putBoolean(KEY_SEEDED, true) }
    }

    private suspend fun seedEpub() {
        val destination = File(context.filesDir, "sample/1984.epub")
        destination.parentFile?.mkdirs()
        context.assets.open("sample/1984.epub").use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        bookRepository.importBook(Uri.fromFile(destination))
    }

    private suspend fun seedAudiobookStub() {
        bookRepository.insertDownloadableStub(
            title = "Mashi and Other Stories",
            author = "Hyakuzo Kurata",
            format = BookFormat.M4B,
            downloadUrl = SAMPLE_AUDIOBOOK_URL,
        )
    }

    companion object {
        private const val KEY_SEEDED = "seeded"
    }
}
