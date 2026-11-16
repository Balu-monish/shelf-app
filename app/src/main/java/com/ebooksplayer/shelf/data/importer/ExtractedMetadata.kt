package com.ebooksplayer.shelf.data.importer

data class ExtractedMetadata(
    val title: String,
    val author: String?,
    val coverBytes: ByteArray?,
    val durationMs: Long? = null,
    val series: String? = null,
    val seriesIndex: Float? = null,
)
