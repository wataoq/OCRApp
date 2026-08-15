package com.local.bookocr.ui.library

import com.local.bookocr.data.local.dao.BookWithPageCount

data class LibraryUiState(
    val books: List<BookWithPageCount> = emptyList(),
    val isLoading: Boolean = true,
    val showCreateDialog: Boolean = false,
    val bookPendingDeletion: BookWithPageCount? = null,
    val errorMessage: String? = null,
)
