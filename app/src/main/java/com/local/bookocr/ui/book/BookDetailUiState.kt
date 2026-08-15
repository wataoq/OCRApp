package com.local.bookocr.ui.book

import com.local.bookocr.data.local.entity.BookEntity
import com.local.bookocr.data.local.entity.PageEntity

data class BookDetailUiState(
    val book: BookEntity? = null,
    val pages: List<PageEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    val pagePendingDeletion: PageEntity? = null,
    val pagePendingNumberEdit: PageEntity? = null,
    val errorMessage: String? = null,
    val bookDeletedExternally: Boolean = false,
)
