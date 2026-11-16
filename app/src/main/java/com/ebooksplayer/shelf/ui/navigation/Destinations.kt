package com.ebooksplayer.shelf.ui.navigation

sealed class Destination(val route: String) {
    data object Library : Destination("library")
    data object BookDetail : Destination("book/{bookId}") {
        fun createRoute(bookId: Long) = "book/$bookId"
    }
    data object Reader : Destination("book/{bookId}/read") {
        fun createRoute(bookId: Long) = "book/$bookId/read"
    }
    data object Player : Destination("book/{bookId}/listen") {
        fun createRoute(bookId: Long) = "book/$bookId/listen"
    }
    data object Settings : Destination("settings")
}

const val ARG_BOOK_ID = "bookId"
