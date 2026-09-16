package com.ebooksplayer.shelf.data.importer

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoverStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun save(bookId: Long, bytes: ByteArray): String {
        val dir = File(context.filesDir, "covers").apply { mkdirs() }
        val file = File(dir, "$bookId.jpg")
        file.writeBytes(bytes)
        return file.toUri().toString()
    }

    private fun File.toUri() = android.net.Uri.fromFile(this)
}
