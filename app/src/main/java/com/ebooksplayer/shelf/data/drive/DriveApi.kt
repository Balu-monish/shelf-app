package com.ebooksplayer.shelf.data.drive

import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
private const val FILES_ENDPOINT = "https://www.googleapis.com/drive/v3/files"

/**
 * Thin wrapper around the Google Drive v3 REST API - just enough to find a
 * folder by name, list its immediate children, and download a file. Direct
 * REST calls (rather than the generated google-api-services-drive client)
 * per Google's current guidance for Android.
 */
@Singleton
class DriveApi @Inject constructor(
    private val httpClient: OkHttpClient,
) {
    suspend fun findFolderIdByName(accessToken: String, name: String): String? =
        withContext(Dispatchers.IO) {
            val query = "mimeType = '$FOLDER_MIME_TYPE' and name = '${escapeForQuery(name)}' " +
                "and 'root' in parents and trashed = false"
            val url = FILES_ENDPOINT.toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("fields", "files(id,name)")
                .addQueryParameter("spaces", "drive")
                .build()

            val response = execute(accessToken, url.toString()) ?: return@withContext null
            val files = JSONObject(response).optJSONArray("files") ?: return@withContext null
            if (files.length() == 0) null else files.getJSONObject(0).getString("id")
        }

    suspend fun listChildren(accessToken: String, folderId: String): List<DriveEntry> =
        withContext(Dispatchers.IO) {
            val entries = mutableListOf<DriveEntry>()
            var pageToken: String? = null

            do {
                val urlBuilder = FILES_ENDPOINT.toHttpUrl().newBuilder()
                    .addQueryParameter("q", "'$folderId' in parents and trashed = false")
                    .addQueryParameter("fields", "nextPageToken,files(id,name,mimeType,size,thumbnailLink)")
                    .addQueryParameter("pageSize", "1000")
                    .addQueryParameter("spaces", "drive")
                pageToken?.let { urlBuilder.addQueryParameter("pageToken", it) }

                val response = execute(accessToken, urlBuilder.build().toString()) ?: break
                val json = JSONObject(response)
                val files = json.optJSONArray("files")
                if (files != null) {
                    for (i in 0 until files.length()) {
                        val file = files.getJSONObject(i)
                        entries += DriveEntry(
                            id = file.getString("id"),
                            name = file.getString("name"),
                            isFolder = file.optString("mimeType") == FOLDER_MIME_TYPE,
                            // "size" is omitted by Drive for folders and some file types.
                            sizeBytes = file.optString("size").toLongOrNull(),
                            thumbnailLink = file.optString("thumbnailLink").takeIf { it.isNotBlank() },
                        )
                    }
                }
                pageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
            } while (pageToken != null)

            entries
        }

    suspend fun downloadFile(accessToken: String, fileId: String, destination: File): Boolean =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$FILES_ENDPOINT/$fileId?alt=media")
                .header("Authorization", "Bearer $accessToken")
                .build()

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

    private fun execute(accessToken: String, url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    private fun escapeForQuery(value: String): String =
        value.replace("\\", "\\\\").replace("'", "\\'")
}
