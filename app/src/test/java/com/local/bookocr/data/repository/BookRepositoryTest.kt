package com.local.bookocr.data.repository

import com.local.bookocr.data.local.entity.PageEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BookRepositoryTest {

    private lateinit var pageDao: FakePageDao
    private lateinit var bookDao: FakeBookDao
    private lateinit var imageStorage: FakeImageStorage
    private lateinit var repository: BookRepository

    @Before
    fun setUp() {
        pageDao = FakePageDao()
        bookDao = FakeBookDao(pageDao)
        imageStorage = FakeImageStorage()
        repository = BookRepository(bookDao, pageDao, imageStorage)
    }

    @Test
    fun `createBook rejects a blank title`() = runTest {
        val result = repository.createBook("   ", null)

        assertTrue(result.isFailure)
        assertTrue(bookDao.books.value.isEmpty())
    }

    @Test
    fun `createBook trims title and normalizes a blank author to null`() = runTest {
        val id = repository.createBook("  こころ  ", "  ").getOrThrow()

        val book = bookDao.getByIdOnce(id)
        assertEquals("こころ", book?.title)
        assertNull(book?.author)
    }

    @Test
    fun `deleteBook removes the db row and every page image file`() = runTest {
        val bookId = repository.createBook("こころ", null).getOrThrow()
        pageDao.insert(PageEntity(bookId = bookId, pageNumber = 1, storedImagePath = "a.jpg", createdAt = 1, updatedAt = 1))
        pageDao.insert(PageEntity(bookId = bookId, pageNumber = 2, storedImagePath = "b.jpg", createdAt = 2, updatedAt = 2))
        val book = bookDao.getByIdOnce(bookId)!!

        val result = repository.deleteBook(book)

        assertTrue(result.isSuccess)
        assertNull(bookDao.getByIdOnce(bookId))
        assertEquals(setOf("a.jpg", "b.jpg"), imageStorage.deletedPaths.toSet())
    }
}
