package com.local.bookocr.data.repository

import android.net.Uri
import com.local.bookocr.data.local.entity.PageEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PageRepositoryTest {

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
    fun `importPage copies the image then inserts a page row referencing it`() = runTest {
        imageStorage.nextImportResult = Result.success("uuid123.jpg")

        val id = repository.importPage(bookId = 1L, sourceUri = Uri.EMPTY, pageNumber = 3).getOrThrow()

        val page = pageDao.getByIdOnce(id)
        assertEquals("uuid123.jpg", page?.storedImagePath)
        assertEquals(3, page?.pageNumber)
    }

    @Test
    fun `importPage failure does not insert an orphaned page row`() = runTest {
        imageStorage.nextImportResult = Result.failure(RuntimeException("画像を開けませんでした"))

        val result = repository.importPage(bookId = 1L, sourceUri = Uri.EMPTY, pageNumber = null)

        assertTrue(result.isFailure)
        assertTrue(pageDao.getForBookOnce(1L).isEmpty())
    }

    @Test
    fun `deletePage removes the db row and the image file`() = runTest {
        val id = pageDao.insert(
            PageEntity(bookId = 1L, pageNumber = 1, storedImagePath = "x.jpg", createdAt = 1, updatedAt = 1),
        )
        val page = pageDao.getByIdOnce(id)!!

        repository.deletePage(page)

        assertNull(pageDao.getByIdOnce(id))
        assertTrue(imageStorage.deletedPaths.contains("x.jpg"))
    }

    @Test
    fun `pages are exposed in display order`() = runTest {
        pageDao.insert(PageEntity(bookId = 1L, pageNumber = 2, storedImagePath = "b.jpg", createdAt = 2, updatedAt = 2))
        pageDao.insert(PageEntity(bookId = 1L, pageNumber = 1, storedImagePath = "a.jpg", createdAt = 1, updatedAt = 1))

        val ordered = pageDao.getForBookOnce(1L).sortedForDisplay().map { it.pageNumber }

        assertEquals(listOf(1, 2), ordered)
    }
}
