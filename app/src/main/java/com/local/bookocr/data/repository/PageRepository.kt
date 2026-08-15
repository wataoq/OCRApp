package com.local.bookocr.data.repository

import android.net.Uri
import com.local.bookocr.data.local.dao.PageDao
import com.local.bookocr.data.local.entity.PageEntity
import com.local.bookocr.imageprocessor.model.ProcessingVariant
import com.local.bookocr.storage.ImageStorage
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class PreparedPagePaths(
    val storedImagePath: String,
    val processedImagePath: String? = null,
    val processingSettingsJson: String? = null,
    val processingVersion: Int = 0,
    val pageNumber: Int? = null,
    val dewarpImagePath: String? = null,
    val dewarpMeshJson: String? = null,
    val activeVariant: ProcessingVariant = ProcessingVariant.ORIGINAL,
)

class PageRepository(
    private val pageDao: PageDao,
    private val imageStorage: ImageStorage,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    fun observePages(bookId: Long): Flow<List<PageEntity>> =
        pageDao.observeForBook(bookId).map { it.sortedForDisplay() }

    fun observePage(pageId: Long): Flow<PageEntity?> = pageDao.observeById(pageId)

    suspend fun getPageOnce(pageId: Long): PageEntity? = pageDao.getByIdOnce(pageId)

    /**
     * The file OCR should read for [page], chosen by its active variant: the dewarped or
     * perspective-corrected image when that variant is selected and its file exists, otherwise the
     * untouched original. This is the single OCR-integration point - EditorViewModel already calls
     * this, so switching variants (and re-running OCR) needs no OCR-layer change.
     */
    fun resolveImageFile(page: PageEntity): File = when (variantOf(page)) {
        ProcessingVariant.DEWARPED ->
            page.dewarpImagePath?.takeIf { imageStorage.dewarpExists(it) }
                ?.let { imageStorage.resolveDewarpFile(it) }
                ?: imageStorage.resolveFile(page.storedImagePath)
        ProcessingVariant.PERSPECTIVE ->
            page.processedImagePath?.takeIf { imageStorage.processedExists(it) }
                ?.let { imageStorage.resolveProcessedFile(it) }
                ?: imageStorage.resolveFile(page.storedImagePath)
        ProcessingVariant.ORIGINAL -> imageStorage.resolveFile(page.storedImagePath)
    }

    /** Always the untouched original, regardless of any correction (for re-editing corrections). */
    fun resolveOriginalFile(page: PageEntity): File = imageStorage.resolveFile(page.storedImagePath)

    fun imageExists(page: PageEntity): Boolean = imageStorage.exists(page.storedImagePath)

    /** Variants whose image actually exists on disk (ORIGINAL always; the others when generated). */
    fun availableVariants(page: PageEntity): List<ProcessingVariant> = buildList {
        add(ProcessingVariant.ORIGINAL)
        page.processedImagePath?.takeIf { imageStorage.processedExists(it) }?.let { add(ProcessingVariant.PERSPECTIVE) }
        page.dewarpImagePath?.takeIf { imageStorage.dewarpExists(it) }?.let { add(ProcessingVariant.DEWARPED) }
    }

    private fun variantOf(page: PageEntity): ProcessingVariant =
        runCatching { ProcessingVariant.valueOf(page.activeVariant) }.getOrDefault(ProcessingVariant.PERSPECTIVE)

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

    /**
     * Inserts a page whose original image was already imported (path known) - used by the image
     * preprocessing flow, which imports the original and generates the processed image itself
     * before persisting, so it can record both paths in one row without a second copy.
     */
    suspend fun importPageWithPaths(
        bookId: Long,
        storedImagePath: String,
        processedImagePath: String?,
        processingSettingsJson: String?,
        processingVersion: Int,
        pageNumber: Int?,
        dewarpImagePath: String? = null,
        dewarpMeshJson: String? = null,
        activeVariant: ProcessingVariant = ProcessingVariant.PERSPECTIVE,
    ): Result<Long> = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        runCatching {
            pageDao.insert(
                PageEntity(
                    bookId = bookId,
                    pageNumber = pageNumber,
                    storedImagePath = storedImagePath,
                    processedImagePath = processedImagePath,
                    processingSettingsJson = processingSettingsJson,
                    processingVersion = processingVersion,
                    dewarpImagePath = dewarpImagePath,
                    dewarpMeshJson = dewarpMeshJson,
                    activeVariant = activeVariant.name,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    /**
     * Persists all pages extracted from one capture atomically and preserves the supplied order.
     * The caller owns file cleanup when this database operation fails.
     */
    suspend fun importPreparedPages(
        bookId: Long,
        pages: List<PreparedPagePaths>,
    ): Result<List<Long>> = withContext(ioDispatcher) {
        require(pages.isNotEmpty())
        val now = System.currentTimeMillis()
        runCatching {
            pageDao.insertAll(
                pages.mapIndexed { index, page ->
                    PageEntity(
                        bookId = bookId,
                        pageNumber = page.pageNumber,
                        storedImagePath = page.storedImagePath,
                        processedImagePath = page.processedImagePath,
                        processingSettingsJson = page.processingSettingsJson,
                        processingVersion = page.processingVersion,
                        dewarpImagePath = page.dewarpImagePath,
                        dewarpMeshJson = page.dewarpMeshJson,
                        activeVariant = page.activeVariant.name,
                        createdAt = now + index,
                        updatedAt = now + index,
                    )
                },
            )
        }
    }

    /** Records a freshly generated dewarp image + its mesh for an existing page (re-dewarp). */
    suspend fun setDewarpImage(
        pageId: Long,
        dewarpPath: String,
        meshJson: String,
    ): Result<Unit> = runCatching {
        pageDao.updateDewarpImage(pageId, dewarpPath, meshJson, System.currentTimeMillis())
    }

    /** Deletes the dewarp file and clears its columns; falls back to PERSPECTIVE if it was active. */
    suspend fun clearDewarpImage(pageId: Long): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val page = pageDao.getByIdOnce(pageId)
            pageDao.updateDewarpImage(pageId, null, null, System.currentTimeMillis())
            if (page?.activeVariant == ProcessingVariant.DEWARPED.name) {
                pageDao.updateActiveVariant(pageId, ProcessingVariant.PERSPECTIVE.name, System.currentTimeMillis())
            }
            page?.dewarpImagePath?.let { imageStorage.deleteDewarp(it) }
            Unit
        }
    }

    /** Selects which corrected image OCR reads (Original / Perspective / Dewarped comparison). */
    suspend fun setActiveVariant(pageId: Long, variant: ProcessingVariant): Result<Unit> = runCatching {
        pageDao.updateActiveVariant(pageId, variant.name, System.currentTimeMillis())
    }

    /** Records a freshly generated processed image for an existing page (re-correction). */
    suspend fun setProcessedImage(
        pageId: Long,
        processedPath: String,
        settingsJson: String,
        version: Int,
    ): Result<Unit> = runCatching {
        pageDao.updateProcessedImage(pageId, processedPath, settingsJson, version, System.currentTimeMillis())
    }

    /**
     * Reverts a page to its original image: deletes the processed file and clears its columns.
     * The original is never touched, so this is a safe "reset to original" for OCR.
     */
    suspend fun clearProcessedImage(pageId: Long): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val page = pageDao.getByIdOnce(pageId)
            pageDao.updateProcessedImage(pageId, null, null, 0, System.currentTimeMillis())
            if (page?.activeVariant == ProcessingVariant.PERSPECTIVE.name) {
                pageDao.updateActiveVariant(pageId, ProcessingVariant.ORIGINAL.name, System.currentTimeMillis())
            }
            page?.processedImagePath?.let { imageStorage.deleteProcessed(it) }
            Unit
        }
    }

    suspend fun deletePage(page: PageEntity): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            pageDao.delete(page)
            page.processedImagePath?.let { imageStorage.deleteProcessed(it) }
            page.dewarpImagePath?.let { imageStorage.deleteDewarp(it) }
            imageStorage.delete(page.storedImagePath)
            Unit
        }
    }

    suspend fun updatePageNumber(pageId: Long, pageNumber: Int?): Result<Unit> = runCatching {
        pageDao.updatePageNumber(pageId, pageNumber, System.currentTimeMillis())
    }
}
