package com.local.bookocr.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.local.bookocr.data.local.dao.BookWithPageCount
import com.local.bookocr.data.local.entity.BookEntity
import com.local.bookocr.data.repository.BookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LibraryViewModel(private val bookRepository: BookRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            bookRepository.observeBooks().collect { books ->
                _uiState.update { it.copy(books = books, isLoading = false) }
            }
        }
    }

    fun onShowCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = true) }
    }

    fun onDismissCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = false) }
    }

    fun onCreateBook(title: String, author: String?) {
        viewModelScope.launch {
            bookRepository.createBook(title, author)
                .onSuccess { _uiState.update { it.copy(showCreateDialog = false) } }
                .onFailure { _uiState.update { it.copy(errorMessage = "タイトルを入力してください") } }
        }
    }

    fun onRequestDelete(book: BookWithPageCount) {
        _uiState.update { it.copy(bookPendingDeletion = book) }
    }

    fun onDismissDeleteRequest() {
        _uiState.update { it.copy(bookPendingDeletion = null) }
    }

    fun onConfirmDelete() {
        val book = _uiState.value.bookPendingDeletion ?: return
        viewModelScope.launch {
            val entity = BookEntity(
                id = book.id,
                title = book.title,
                author = book.author,
                createdAt = book.createdAt,
                updatedAt = book.updatedAt,
            )
            bookRepository.deleteBook(entity)
                .onSuccess { _uiState.update { it.copy(bookPendingDeletion = null) } }
                .onFailure {
                    _uiState.update {
                        it.copy(bookPendingDeletion = null, errorMessage = "本の削除に失敗しました")
                    }
                }
        }
    }

    fun onErrorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    companion object {
        fun factory(bookRepository: BookRepository) = viewModelFactory {
            initializer { LibraryViewModel(bookRepository) }
        }
    }
}
