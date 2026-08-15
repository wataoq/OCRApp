package com.local.bookocr.ui.navigation

import android.net.Uri

object BookOcrDestinations {
    const val LIBRARY_ROUTE = "library"
    const val BOOK_DETAIL_ROUTE = "book/{bookId}"
    const val EDITOR_ROUTE = "editor/{pageId}"
    const val PREPROCESSING_ROUTE = "preprocessing/{bookId}?sourceUri={sourceUri}"

    const val BOOK_ID_ARG = "bookId"
    const val PAGE_ID_ARG = "pageId"
    const val PREPROCESSING_SOURCE_URI_ARG = "sourceUri"

    fun bookDetailRoute(bookId: Long) = "book/$bookId"
    fun editorRoute(pageId: Long) = "editor/$pageId"

    /** [sourceUri] is the Photo Picker content Uri; it is URL-encoded into the route argument. */
    fun preprocessingRoute(bookId: Long, sourceUri: Uri): String =
        "preprocessing/$bookId?sourceUri=${Uri.encode(sourceUri.toString())}"
}
