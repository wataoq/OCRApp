package com.local.bookocr.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.local.bookocr.ui.book.BookDetailScreen
import com.local.bookocr.ui.editor.EditorScreen
import com.local.bookocr.ui.library.LibraryScreen

@Composable
fun BookOcrNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = BookOcrDestinations.LIBRARY_ROUTE) {
        composable(BookOcrDestinations.LIBRARY_ROUTE) {
            LibraryScreen(
                onOpenBook = { bookId ->
                    navController.navigate(BookOcrDestinations.bookDetailRoute(bookId))
                },
            )
        }

        composable(
            route = BookOcrDestinations.BOOK_DETAIL_ROUTE,
            arguments = listOf(navArgument(BookOcrDestinations.BOOK_ID_ARG) { type = NavType.LongType }),
        ) {
            BookDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenPage = { pageId ->
                    navController.navigate(BookOcrDestinations.editorRoute(pageId))
                },
            )
        }

        composable(
            route = BookOcrDestinations.EDITOR_ROUTE,
            arguments = listOf(navArgument(BookOcrDestinations.PAGE_ID_ARG) { type = NavType.LongType }),
        ) {
            EditorScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
