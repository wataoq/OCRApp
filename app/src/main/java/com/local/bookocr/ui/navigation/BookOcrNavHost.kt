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
import com.local.bookocr.ui.preprocessing.ImagePreprocessingScreen

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
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments!!.getLong(BookOcrDestinations.BOOK_ID_ARG)
            BookDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenPage = { pageId ->
                    navController.navigate(BookOcrDestinations.editorRoute(pageId))
                },
                onImportPage = { uri ->
                    navController.navigate(BookOcrDestinations.preprocessingRoute(bookId, uri))
                },
            )
        }

        composable(
            route = BookOcrDestinations.PREPROCESSING_ROUTE,
            arguments = listOf(
                navArgument(BookOcrDestinations.BOOK_ID_ARG) { type = NavType.LongType },
                navArgument(BookOcrDestinations.PREPROCESSING_SOURCE_URI_ARG) {
                    type = NavType.StringType
                    nullable = false
                },
            ),
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments!!.getLong(BookOcrDestinations.BOOK_ID_ARG)
            val encodedUri = backStackEntry.arguments!!
                .getString(BookOcrDestinations.PREPROCESSING_SOURCE_URI_ARG)!!
            ImagePreprocessingScreen(
                bookId = bookId,
                encodedSourceUri = encodedUri,
                onPageCreated = { pageId ->
                    // Replace the preprocessing screen with the editor so Back returns to the book.
                    navController.navigate(BookOcrDestinations.editorRoute(pageId)) {
                        popUpTo(BookOcrDestinations.PREPROCESSING_ROUTE) { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() },
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
