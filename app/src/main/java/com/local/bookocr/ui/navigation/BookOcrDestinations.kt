package com.local.bookocr.ui.navigation

object BookOcrDestinations {
    const val LIBRARY_ROUTE = "library"
    const val BOOK_DETAIL_ROUTE = "book/{bookId}"
    const val EDITOR_ROUTE = "editor/{pageId}"

    const val BOOK_ID_ARG = "bookId"
    const val PAGE_ID_ARG = "pageId"

    fun bookDetailRoute(bookId: Long) = "book/$bookId"
    fun editorRoute(pageId: Long) = "editor/$pageId"
}
