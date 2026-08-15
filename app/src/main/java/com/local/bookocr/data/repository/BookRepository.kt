package com.local.bookocr.data.repository

import com.local.bookocr.data.local.dao.BookDao
import com.local.bookocr.data.local.dao.BookWithPageCount
import com.local.bookocr.data.local.dao.PageDao
import com.local.bookocr.data.local.entity.BookEntity
import com.local.bookocr.storage.ImageStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class BookRepository(
    private val bookDao: BookDao,
    private val pageDao: PageDao,
    private val imageStorage: ImageStorage,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    fun observeBooks(): Flow<List<BookWithPageCount>> = bookDao.observeAllWithPageCount()

    fun observeBook(bookId: Long): Flow<BookEntity?> = bookDao.observeById(bookId)

    suspend fun createBook(title: String, author: String?): Result<Long> {
        if (BookValidation.validateTitle(title) is TitleValidationResult.Blank) {
            return Result.failure(IllegalArgumentException("タイトルを入力してください"))
        }
        val now = System.currentTimeMillis()
        val book = BookEntity(
            title = title.trim(),
            author = author?.trim()?.ifBlank { null },
            createdAt = now,
            updatedAt = now,
        )
        return runCatching { bookDao.insert(book) }
    }

    /**
     * Deletes the book (Room cascades its pages and their OCR results), then deletes the
     * orphaned page image files. The file cleanup runs after the DB delete succeeds so a
     * failed DB delete never leaves the DB and filesystem inconsistent with each other.
     */
    suspend fun deleteBook(book: BookEntity): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val pages = pageDao.getForBookOnce(book.id)
            bookDao.delete(book)
            pages.forEach { page ->
                page.processedImagePath?.let { imageStorage.deleteProcessed(it) }
                page.dewarpImagePath?.let { imageStorage.deleteDewarp(it) }
                imageStorage.delete(page.storedImagePath)
            }
        }
    }
}
