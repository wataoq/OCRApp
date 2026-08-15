package com.local.bookocr.ui.library

import com.local.bookocr.data.repository.BookRepository
import com.local.bookocr.data.repository.FakeBookDao
import com.local.bookocr.data.repository.FakeImageStorage
import com.local.bookocr.data.repository.FakePageDao
import com.local.bookocr.util.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LibraryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var pageDao: FakePageDao
    private lateinit var bookDao: FakeBookDao
    private lateinit var repository: BookRepository
    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setUp() {
        pageDao = FakePageDao()
        bookDao = FakeBookDao(pageDao)
        repository = BookRepository(bookDao, pageDao, FakeImageStorage(), mainDispatcherRule.dispatcher)
        viewModel = LibraryViewModel(repository)
    }

    @Test
    fun `starts loading then reflects an empty library`() = runTest {
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.books.isEmpty())
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `creating a book updates the list and closes the dialog`() = runTest {
        viewModel.onShowCreateDialog()
        assertEquals(true, viewModel.uiState.value.showCreateDialog)

        viewModel.onCreateBook("こころ", "夏目漱石")
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.showCreateDialog)
        assertEquals(1, viewModel.uiState.value.books.size)
        assertEquals("こころ", viewModel.uiState.value.books.first().title)
    }

    @Test
    fun `blank title shows an error and keeps the dialog open`() = runTest {
        viewModel.onShowCreateDialog()

        viewModel.onCreateBook("   ", null)
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.showCreateDialog)
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.books.isEmpty())
    }

    @Test
    fun `confirming deletion removes the book`() = runTest {
        viewModel.onCreateBook("こころ", null)
        advanceUntilIdle()
        val book = viewModel.uiState.value.books.first()

        viewModel.onRequestDelete(book)
        assertEquals(book, viewModel.uiState.value.bookPendingDeletion)

        viewModel.onConfirmDelete()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.books.isEmpty())
        assertNull(viewModel.uiState.value.bookPendingDeletion)
    }
}
