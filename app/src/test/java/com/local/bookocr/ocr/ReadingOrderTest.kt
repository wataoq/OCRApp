package com.local.bookocr.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingOrderTest {

    @Test
    fun `horizontal blocks are ordered top-to-bottom then left-to-right`() {
        val blocks = listOf(
            block(index = 0, left = 500, top = 300, width = 300, height = 100),
            block(index = 1, left = 100, top = 100, width = 300, height = 100),
            block(index = 2, left = 500, top = 100, width = 300, height = 100),
        )

        assertEquals(listOf(1, 2, 0), readingOrder(blocks))
    }

    @Test
    fun `vertical columns are ordered right-to-left and then top-to-bottom`() {
        val blocks = listOf(
            block(index = 0, left = 100, top = 20, width = 80, height = 500),
            block(index = 1, left = 500, top = 200, width = 80, height = 300),
            block(index = 2, left = 500, top = 20, width = 80, height = 150),
        )

        assertEquals(listOf(2, 1, 0), readingOrder(blocks))
    }

    @Test
    fun `missing bounding boxes retain stable order at the end`() {
        val blocks = listOf(
            ReadingOrderBlock(0, null, null, null, null),
            block(index = 1, left = 0, top = 0, width = 200, height = 50),
            ReadingOrderBlock(2, null, null, null, null),
        )

        assertEquals(listOf(1, 0, 2), readingOrder(blocks))
    }

    private fun block(index: Int, left: Int, top: Int, width: Int, height: Int) =
        ReadingOrderBlock(index, left, top, left + width, top + height)
}
