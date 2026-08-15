package com.local.bookocr.data.repository

import com.local.bookocr.imageprocessor.model.ProcessingVariant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the Original / Perspective / Dewarped variant model: which file OCR resolves to per active
 * variant, which variants are reported available, switching the active variant, and that dewarp
 * files are cleaned up - all without ever touching the untouched original.
 */
class PageRepositoryVariantTest {

    private lateinit var pageDao: FakePageDao
    private lateinit var imageStorage: FakeImageStorage
    private lateinit var repository: PageRepository

    @Before
    fun setUp() {
        pageDao = FakePageDao()
        imageStorage = FakeImageStorage()
        repository = PageRepository(pageDao, imageStorage)
    }

    private suspend fun insertAllVariants(): Long {
        imageStorage.processedPaths += "persp.jpg"
        imageStorage.dewarpPaths += "dewarp.jpg"
        return repository.importPageWithPaths(
            bookId = 1L,
            storedImagePath = "original.jpg",
            processedImagePath = "persp.jpg",
            processingSettingsJson = "{}",
            processingVersion = 1,
            pageNumber = null,
            dewarpImagePath = "dewarp.jpg",
            dewarpMeshJson = "{}",
            activeVariant = ProcessingVariant.DEWARPED,
        ).getOrThrow()
    }

    @Test
    fun `resolveImageFile returns the dewarp file when DEWARPED is active`() = runTest {
        val page = pageDao.getByIdOnce(insertAllVariants())!!
        assertEquals(imageStorage.resolveDewarpFile("dewarp.jpg"), repository.resolveImageFile(page))
    }

    @Test
    fun `resolveImageFile returns the perspective file when PERSPECTIVE is active`() = runTest {
        val id = insertAllVariants()
        repository.setActiveVariant(id, ProcessingVariant.PERSPECTIVE)
        val page = pageDao.getByIdOnce(id)!!
        assertEquals(imageStorage.resolveProcessedFile("persp.jpg"), repository.resolveImageFile(page))
    }

    @Test
    fun `resolveImageFile returns the original when ORIGINAL is active`() = runTest {
        val id = insertAllVariants()
        repository.setActiveVariant(id, ProcessingVariant.ORIGINAL)
        val page = pageDao.getByIdOnce(id)!!
        assertEquals(imageStorage.resolveFile("original.jpg"), repository.resolveImageFile(page))
    }

    @Test
    fun `resolveImageFile falls back to original when active dewarp file is missing`() = runTest {
        // Dewarp path recorded active but file not on disk.
        val id = repository.importPageWithPaths(
            1L, "original.jpg", null, null, 0, null,
            dewarpImagePath = "ghost.jpg", dewarpMeshJson = "{}", activeVariant = ProcessingVariant.DEWARPED,
        ).getOrThrow()
        val page = pageDao.getByIdOnce(id)!!
        assertEquals(imageStorage.resolveFile("original.jpg"), repository.resolveImageFile(page))
    }

    @Test
    fun `availableVariants reports only variants whose files exist`() = runTest {
        val page = pageDao.getByIdOnce(insertAllVariants())!!
        assertEquals(
            listOf(ProcessingVariant.ORIGINAL, ProcessingVariant.PERSPECTIVE, ProcessingVariant.DEWARPED),
            repository.availableVariants(page),
        )
    }

    @Test
    fun `availableVariants is original-only for an unprocessed page`() = runTest {
        val id = repository.importPageWithPaths(1L, "original.jpg", null, null, 0, null).getOrThrow()
        val page = pageDao.getByIdOnce(id)!!
        assertEquals(listOf(ProcessingVariant.ORIGINAL), repository.availableVariants(page))
    }

    @Test
    fun `setActiveVariant persists the selection`() = runTest {
        val id = insertAllVariants()
        repository.setActiveVariant(id, ProcessingVariant.ORIGINAL)
        assertEquals(ProcessingVariant.ORIGINAL.name, pageDao.getByIdOnce(id)!!.activeVariant)
    }

    @Test
    fun `clearDewarpImage deletes the file, clears columns, and leaves the active variant valid`() = runTest {
        val id = insertAllVariants()

        repository.clearDewarpImage(id)

        val page = pageDao.getByIdOnce(id)!!
        assertNull(page.dewarpImagePath)
        assertNull(page.dewarpMeshJson)
        assertEquals(ProcessingVariant.PERSPECTIVE.name, page.activeVariant)
        assertTrue(imageStorage.deletedDewarpPaths.contains("dewarp.jpg"))
        // The original is untouched.
        assertEquals("original.jpg", page.storedImagePath)
    }

    @Test
    fun `deletePage removes original, perspective and dewarp files`() = runTest {
        val id = insertAllVariants()
        val page = pageDao.getByIdOnce(id)!!

        repository.deletePage(page)

        assertNull(pageDao.getByIdOnce(id))
        assertTrue(imageStorage.deletedPaths.contains("original.jpg"))
        assertTrue(imageStorage.deletedProcessedPaths.contains("persp.jpg"))
        assertTrue(imageStorage.deletedDewarpPaths.contains("dewarp.jpg"))
    }

    @Test
    fun `importPageWithPaths defaults keep the original-only backward-compatible behavior`() = runTest {
        val id = repository.importPageWithPaths(1L, "original.jpg", null, null, 0, null).getOrThrow()
        val page = pageDao.getByIdOnce(id)!!
        assertNull(page.dewarpImagePath)
        assertEquals(ProcessingVariant.PERSPECTIVE.name, page.activeVariant)
        // No processed/dewarp images exist, so resolve falls back to the original.
        assertEquals(imageStorage.resolveFile("original.jpg"), repository.resolveImageFile(page))
        assertFalse(repository.availableVariants(page).contains(ProcessingVariant.DEWARPED))
    }
}
