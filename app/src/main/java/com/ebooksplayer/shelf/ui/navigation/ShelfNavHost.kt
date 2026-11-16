package com.ebooksplayer.shelf.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ebooksplayer.shelf.ui.detail.BookDetailScreen
import com.ebooksplayer.shelf.ui.library.LibraryScreen
import com.ebooksplayer.shelf.ui.player.PlayerScreen
import com.ebooksplayer.shelf.ui.reader.ReaderScreen
import com.ebooksplayer.shelf.ui.settings.SettingsScreen

@Composable
fun ShelfNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Destination.Library.route) {
        composable(Destination.Library.route) {
            LibraryScreen(
                onOpenBook = { bookId ->
                    navController.navigate(Destination.BookDetail.createRoute(bookId))
                },
                onOpenSettings = { navController.navigate(Destination.Settings.route) },
            )
        }
        composable(Destination.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Destination.BookDetail.route,
            arguments = listOf(navArgument(ARG_BOOK_ID) { type = NavType.LongType }),
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getLong(ARG_BOOK_ID) ?: return@composable
            BookDetailScreen(
                bookId = bookId,
                onRead = { navController.navigate(Destination.Reader.createRoute(bookId)) },
                onListen = { navController.navigate(Destination.Player.createRoute(bookId)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Destination.Reader.route,
            arguments = listOf(navArgument(ARG_BOOK_ID) { type = NavType.LongType }),
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getLong(ARG_BOOK_ID) ?: return@composable
            ReaderScreen(bookId = bookId, onBack = { navController.popBackStack() })
        }
        composable(
            route = Destination.Player.route,
            arguments = listOf(navArgument(ARG_BOOK_ID) { type = NavType.LongType }),
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getLong(ARG_BOOK_ID) ?: return@composable
            PlayerScreen(bookId = bookId, onBack = { navController.popBackStack() })
        }
    }
}
