package com.local.bookocr.data.repository

import com.local.bookocr.data.local.entity.PageEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class PageOrderingTest {

    private fun page(id: Long, pageNumber: Int?, createdAt: Long) = PageEntity(
        id = id,
        bookId = 1L,
        pageNumber = pageNumber,
        storedImagePath = "p$id.jpg",
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    @Test
    fun `numbered pages sort numerically and before unnumbered pages`() {
        val pages = listOf(
            page(id = 1, pageNumber = 5, createdAt = 100),
            page(id = 2, pageNumber = null, createdAt = 50),
            page(id = 3, pageNumber = 1, createdAt = 200),
        )

        val sortedIds = pages.sortedForDisplay().map { it.id }

        assertEquals(listOf(3L, 1L, 2L), sortedIds)
    }

    @Test
    fun `unnumbered pages fall back to import order deterministically`() {
        val pages = listOf(
            page(id = 1, pageNumber = null, createdAt = 300),
            page(id = 2, pageNumber = null, createdAt = 100),
            page(id = 3, pageNumber = null, createdAt = 200),
        )

        val sortedIds = pages.sortedForDisplay().map { it.id }

        assertEquals(listOf(2L, 3L, 1L), sortedIds)
    }
}
