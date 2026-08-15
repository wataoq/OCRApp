package com.local.bookocr.ui.book

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.local.bookocr.data.local.entity.PageEntity
import com.local.bookocr.data.repository.BookRepository
import com.local.bookocr.data.repository.PageRepository
import com.local.bookocr.ui.navigation.BookOcrDestinations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BookDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val pageRepository: PageRepository,
) : ViewModel() {

    private val bookId: Long = checkNotNull(savedStateHandle[BookOcrDestinations.BOOK_ID_ARG])

    private val _uiState = MutableStateFlow(BookDetailUiState())
    val uiState: StateFlow<BookDetailUiState> = _uiState.asStateFlow()

    private var hasLoadedBookOnce = false

    init {
        viewModelScope.launch {
            combine(bookRepository.observeBook(bookId), pageRepository.observePages(bookId)) { book, pages ->
                book to pages
            }.collect { (book, pages) ->
                if (book == null) {
                    if (hasLoadedBookOnce) {
                        _uiState.update { it.copy(book = null, isLoading = false, bookDeletedExternally = true) }
                    } else {
                        _uiState.update { it.copy(isLoading = false) }
                    }
                } else {
                    hasLoadedBookOnce = true
                    _uiState.update { it.copy(book = book, pages = pages, isLoading = false) }
                }
            }
        }
    }

    fun onDeleteRequest(page: PageEntity) {
        _uiState.update { it.copy(pagePendingDeletion = page) }
    }

    fun onDismissDeleteRequest() {
        _uiState.update { it.copy(pagePendingDeletion = null) }
    }

    fun onConfirmDeletePage() {
        val page = _uiState.value.pagePendingDeletion ?: return
        viewModelScope.launch {
            pageRepository.deletePage(page)
                .onFailure { _uiState.update { it.copy(errorMessage = "ページの削除に失敗しました") } }
            _uiState.update { it.copy(pagePendingDeletion = null) }
        }
    }

    fun onEditPageNumberRequest(page: PageEntity) {
        _uiState.update { it.copy(pagePendingNumberEdit = page) }
    }

    fun onDismissPageNumberEdit() {
        _uiState.update { it.copy(pagePendingNumberEdit = null) }
    }

    fun onConfirmPageNumber(pageNumber: Int?) {
        val page = _uiState.value.pagePendingNumberEdit ?: return
        viewModelScope.launch {
            pageRepository.updatePageNumber(page.id, pageNumber)
            _uiState.update { it.copy(pagePendingNumberEdit = null) }
        }
    }

    fun onErrorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    companion object {
        fun factory(bookRepository: BookRepository, pageRepository: PageRepository) = viewModelFactory {
            initializer {
                BookDetailViewModel(createSavedStateHandle(), bookRepository, pageRepository)
            }
        }
    }
}
