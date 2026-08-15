package com.local.bookocr.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.local.bookocr.data.local.entity.PageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PageDao {

    @Insert
    suspend fun insert(page: PageEntity): Long

    @Delete
    suspend fun delete(page: PageEntity)

    @Query("SELECT * FROM pages WHERE bookId = :bookId")
    fun observeForBook(bookId: Long): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE bookId = :bookId")
    suspend fun getForBookOnce(bookId: Long): List<PageEntity>

    @Query("SELECT * FROM pages WHERE id = :pageId")
    fun observeById(pageId: Long): Flow<PageEntity?>

    @Query("SELECT * FROM pages WHERE id = :pageId")
    suspend fun getByIdOnce(pageId: Long): PageEntity?

    @Query("UPDATE pages SET pageNumber = :pageNumber, updatedAt = :updatedAt WHERE id = :pageId")
    suspend fun updatePageNumber(pageId: Long, pageNumber: Int?, updatedAt: Long)
}
