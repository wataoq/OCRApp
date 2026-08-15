package com.local.bookocr.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.local.bookocr.data.local.entity.BookEntity
import kotlinx.coroutines.flow.Flow

data class BookWithPageCount(
    val id: Long,
    val title: String,
    val author: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val pageCount: Int,
)

@Dao
interface BookDao {

    @Insert
    suspend fun insert(book: BookEntity): Long

    @Delete
    suspend fun delete(book: BookEntity)

    @Query(
        """
        SELECT books.id, books.title, books.author, books.createdAt, books.updatedAt,
               COUNT(pages.id) AS pageCount
        FROM books
        LEFT JOIN pages ON pages.bookId = books.id
        GROUP BY books.id
        ORDER BY books.updatedAt DESC
        """,
    )
    fun observeAllWithPageCount(): Flow<List<BookWithPageCount>>

    @Query("SELECT * FROM books WHERE id = :bookId")
    fun observeById(bookId: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :bookId")
    suspend fun getByIdOnce(bookId: Long): BookEntity?
}
