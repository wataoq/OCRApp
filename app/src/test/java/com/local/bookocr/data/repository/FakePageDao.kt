package com.local.bookocr.data.repository

import com.local.bookocr.data.local.dao.PageDao
import com.local.bookocr.data.local.entity.PageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakePageDao : PageDao {
    private var nextId = 1L
    val pages = MutableStateFlow<List<PageEntity>>(emptyList())
    var deleteFailure: Throwable? = null

    fun observeAll(): Flow<List<PageEntity>> = pages

    override suspend fun insert(page: PageEntity): Long {
        val id = nextId++
        pages.update { it + page.copy(id = id) }
        return id
    }

    override suspend fun insertAll(pages: List<PageEntity>): List<Long> = pages.map { insert(it) }

    override suspend fun delete(page: PageEntity) {
        deleteFailure?.let { throw it }
        pages.update { list -> list.filterNot { it.id == page.id } }
    }

    override fun observeForBook(bookId: Long): Flow<List<PageEntity>> =
        pages.map { list -> list.filter { it.bookId == bookId } }

    override suspend fun getForBookOnce(bookId: Long): List<PageEntity> =
        pages.value.filter { it.bookId == bookId }

    override fun observeById(pageId: Long): Flow<PageEntity?> =
        pages.map { list -> list.find { it.id == pageId } }

    override suspend fun getByIdOnce(pageId: Long): PageEntity? = pages.value.find { it.id == pageId }

    override suspend fun updatePageNumber(pageId: Long, pageNumber: Int?, updatedAt: Long) {
        pages.update { list ->
            list.map { if (it.id == pageId) it.copy(pageNumber = pageNumber, updatedAt = updatedAt) else it }
        }
    }

    override suspend fun updateProcessedImage(
        pageId: Long,
        processedPath: String?,
        settingsJson: String?,
        version: Int,
        updatedAt: Long,
    ) {
        pages.update { list ->
            list.map {
                if (it.id == pageId) {
                    it.copy(
                        processedImagePath = processedPath,
                        processingSettingsJson = settingsJson,
                        processingVersion = version,
                        updatedAt = updatedAt,
                    )
                } else {
                    it
                }
            }
        }
    }

    override suspend fun updateDewarpImage(
        pageId: Long,
        dewarpPath: String?,
        meshJson: String?,
        updatedAt: Long,
    ) {
        pages.update { list ->
            list.map {
                if (it.id == pageId) {
                    it.copy(dewarpImagePath = dewarpPath, dewarpMeshJson = meshJson, updatedAt = updatedAt)
                } else {
                    it
                }
            }
        }
    }

    override suspend fun updateActiveVariant(pageId: Long, variant: String, updatedAt: Long) {
        pages.update { list ->
            list.map { if (it.id == pageId) it.copy(activeVariant = variant, updatedAt = updatedAt) else it }
        }
    }
}
