package com.local.bookocr.data.repository

import android.net.Uri
import com.local.bookocr.data.local.dao.PageDao
import com.local.bookocr.data.local.entity.PageEntity
import com.local.bookocr.storage.ImageStorage
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PageRepository(
    private val pageDao: PageDao,
    private val imageStorage: ImageStorage,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    fun observePages(bookId: Long): Flow<List<PageEntity>> =
        pageDao.observeForBook(bookId).map { it.sortedForDisplay() }

    fun observePage(pageId: Long): Flow<PageEntity?> = pageDao.observeById(pageId)

    fun resolveImageFile(page: PageEntity): File = imageStorage.resolveFile(page.storedImagePath)

    fun imageExists(page: PageEntity): Boolean = imageStorage.exists(page.storedImagePath)

    /**
     * Copies [sourceUri] into app-private storage and inserts the page row. If the DB insert
     * fails after the copy succeeds, the orphaned file is cleaned up rather than left behind.
     */
    suspend fun importPage(
        bookId: Long,
        sourceUri: Uri,
        pageNumber: Int?,
    ): Result<Long> = withContext(ioDispatcher) {
        val storedPath = imageStorage.importImage(sourceUri).getOrElse {
            return@withContext Result.failure(it)
        }
        val now = System.currentTimeMillis()
        runCatching {
            pageDao.insert(
                PageEntity(
                    bookId = bookId,
                    pageNumber = pageNumber,
                    storedImagePath = storedPath,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }.onFailure {
            imageStorage.delete(storedPath)
        }
    }

    suspend fun deletePage(page: PageEntity): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            pageDao.delete(page)
            imageStorage.delete(page.storedImagePath)
        }
    }

    suspend fun updatePageNumber(pageId: Long, pageNumber: Int?): Result<Unit> = runCatching {
        pageDao.updatePageNumber(pageId, pageNumber, System.currentTimeMillis())
    }
}
