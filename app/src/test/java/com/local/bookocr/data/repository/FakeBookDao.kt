package com.local.bookocr.data.repository

import com.local.bookocr.data.local.dao.BookDao
import com.local.bookocr.data.local.dao.BookWithPageCount
import com.local.bookocr.data.local.entity.BookEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeBookDao(private val pageDao: FakePageDao) : BookDao {
    private var nextId = 1L
    val books = MutableStateFlow<List<BookEntity>>(emptyList())

    override suspend fun insert(book: BookEntity): Long {
        val id = nextId++
        books.update { it + book.copy(id = id) }
        return id
    }

    override suspend fun delete(book: BookEntity) {
        books.update { list -> list.filterNot { it.id == book.id } }
    }

    override fun observeAllWithPageCount(): Flow<List<BookWithPageCount>> =
        combine(books, pageDao.observeAll()) { bookList, pages ->
            bookList.map { b ->
                BookWithPageCount(
                    id = b.id,
                    title = b.title,
                    author = b.author,
                    createdAt = b.createdAt,
                    updatedAt = b.updatedAt,
                    pageCount = pages.count { it.bookId == b.id },
                )
            }.sortedByDescending { it.updatedAt }
        }

    override fun observeById(bookId: Long): Flow<BookEntity?> = books.map { list -> list.find { it.id == bookId } }

    override suspend fun getByIdOnce(bookId: Long): BookEntity? = books.value.find { it.id == bookId }
}
