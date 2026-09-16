package com.ebooksplayer.shelf.data.download

import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads a file from a plain, publicly-accessible HTTPS URL - no auth
 * header, unlike DriveApi's downloads. Used for content sourced outside
 * Drive (e.g. a preloaded sample pointing at a direct archive.org link).
 */
@Singleton
class ContentDownloader @Inject constructor(
    private val httpClient: OkHttpClient,
) {
    suspend fun download(url: String, destination: File): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext false
            val body = response.body ?: return@withContext false
            destination.parentFile?.mkdirs()
            body.byteStream().use { input ->
                FileOutputStream(destination).use { output -> input.copyTo(output) }
            }
            true
        }
    }
}
