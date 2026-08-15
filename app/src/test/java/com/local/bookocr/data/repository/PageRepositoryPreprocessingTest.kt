package com.local.bookocr.data.repository

import com.local.bookocr.imageprocessor.ImageProcessor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the non-destructive image-processing contract on [PageRepository]: the original image
 * survives every operation, the processed image is a distinct file, resets and deletes clean up
 * correctly, and OCR resolves to the processed image only when it actually exists.
 */
class PageRepositoryPreprocessingTest {

    private lateinit var pageDao: FakePageDao
    private lateinit var imageStorage: FakeImageStorage
    private lateinit var repository: PageRepository

    @Before
    fun setUp() {
        pageDao = FakePageDao()
        imageStorage = FakeImageStorage()
        repository = PageRepository(pageDao, imageStorage)
    }

    @Test
    fun `importPageWithPaths persists both original and processed paths`() = runTest {
        val id = repository.importPageWithPaths(
            bookId = 1L,
            storedImagePath = "original.jpg",
            processedImagePath = "processed.jpg",
            processingSettingsJson = "{}",
            processingVersion = ImageProcessor.PROCESSING_VERSION,
            pageNumber = 2,
        ).getOrThrow()

        val page = pageDao.getByIdOnce(id)!!
        assertEquals("original.jpg", page.storedImagePath)
        assertEquals("processed.jpg", page.processedImagePath)
        assertEquals(ImageProcessor.PROCESSING_VERSION, page.processingVersion)
        assertEquals(2, page.pageNumber)
        assertNotEquals(page.storedImagePath, page.processedImagePath)
    }

    @Test
    fun `original image path is preserved after setProcessedImage`() = runTest {
        val id = repository.importPageWithPaths(1L, "original.jpg", null, null, 0, null).getOrThrow()

        repository.setProcessedImage(id, "processed.jpg", "{}", ImageProcessor.PROCESSING_VERSION)

        val page = pageDao.getByIdOnce(id)!!
        assertEquals("original.jpg", page.storedImagePath)
        assertEquals("processed.jpg", page.processedImagePath)
    }

    @Test
    fun `clearProcessedImage resets to original and deletes the processed file`() = runTest {
        imageStorage.processedPaths += "processed.jpg"
        val id = repository.importPageWithPaths(
            1L, "original.jpg", "processed.jpg", "{}", ImageProcessor.PROCESSING_VERSION, null,
        ).getOrThrow()

        repository.clearProcessedImage(id)

        val page = pageDao.getByIdOnce(id)!!
        assertEquals("original.jpg", page.storedImagePath)
        assertNull(page.processedImagePath)
        assertNull(page.processingSettingsJson)
        assertEquals(0, page.processingVersion)
        assertTrue(imageStorage.deletedProcessedPaths.contains("processed.jpg"))
    }

    @Test
    fun `resolveImageFile returns the processed file when it exists`() = runTest {
        imageStorage.processedPaths += "processed.jpg"
        val id = repository.importPageWithPaths(1L, "original.jpg", "processed.jpg", "{}", 1, null).getOrThrow()
        val page = pageDao.getByIdOnce(id)!!

        val resolved = repository.resolveImageFile(page)

        assertEquals(imageStorage.resolveProcessedFile("processed.jpg"), resolved)
    }

    @Test
    fun `resolveImageFile falls back to the original when no processed image exists`() = runTest {
        val id = repository.importPageWithPaths(1L, "original.jpg", null, null, 0, null).getOrThrow()
        val page = pageDao.getByIdOnce(id)!!

        val resolved = repository.resolveImageFile(page)

        assertEquals(imageStorage.resolveFile("original.jpg"), resolved)
    }

    @Test
    fun `resolveImageFile falls back to the original when processed path is set but file is missing`() = runTest {
        // processedImagePath recorded but the file is not on disk (processedExists == false).
        val id = repository.importPageWithPaths(1L, "original.jpg", "ghost.jpg", "{}", 1, null).getOrThrow()
        val page = pageDao.getByIdOnce(id)!!

        val resolved = repository.resolveImageFile(page)

        assertEquals(imageStorage.resolveFile("original.jpg"), resolved)
    }

    @Test
    fun `deletePage removes both the original and the processed files`() = runTest {
        imageStorage.processedPaths += "processed.jpg"
        val id = repository.importPageWithPaths(1L, "original.jpg", "processed.jpg", "{}", 1, null).getOrThrow()
        val page = pageDao.getByIdOnce(id)!!

        repository.deletePage(page)

        assertNull(pageDao.getByIdOnce(id))
        assertTrue(imageStorage.deletedPaths.contains("original.jpg"))
        assertTrue(imageStorage.deletedProcessedPaths.contains("processed.jpg"))
    }

    @Test
    fun `resolveOriginalFile always returns the original even when a processed image exists`() = runTest {
        imageStorage.processedPaths += "processed.jpg"
        val id = repository.importPageWithPaths(1L, "original.jpg", "processed.jpg", "{}", 1, null).getOrThrow()
        val page = pageDao.getByIdOnce(id)!!

        assertEquals(imageStorage.resolveFile("original.jpg"), repository.resolveOriginalFile(page))
    }

    @Test
    fun `importPageWithPaths with identity processing stores no processed image`() = runTest {
        val id = repository.importPageWithPaths(1L, "original.jpg", null, null, 0, null).getOrThrow()
        val page = pageDao.getByIdOnce(id)!!

        assertNull(page.processedImagePath)
        assertEquals(0, page.processingVersion)
        assertFalse(page.processedImagePath == page.storedImagePath)
    }
}
