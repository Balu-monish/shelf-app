package com.ebooksplayer.shelf.player

data class PlayerChapter(
    val index: Int,
    val title: String,
    val startMs: Long,
    val endMs: Long,
)
