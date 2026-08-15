package com.local.bookocr.imageprocessor

import com.local.bookocr.imageprocessor.model.DewarpMesh
import com.local.bookocr.imageprocessor.model.ReadingDirection
import com.local.bookocr.imageprocessor.model.SpreadPageSide
import com.local.bookocr.imageprocessor.model.orderedSides
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentScanLayoutTest {

    @Test
    fun `Japanese reading direction imports the right page first`() {
        assertEquals(
            listOf(SpreadPageSide.RIGHT, SpreadPageSide.LEFT),
            ReadingDirection.RIGHT_TO_LEFT.orderedSides(),
        )
    }

    @Test
    fun `left-to-right direction reverses spread import order`() {
        assertEquals(
            listOf(SpreadPageSide.LEFT, SpreadPageSide.RIGHT),
            ReadingDirection.LEFT_TO_RIGHT.orderedSides(),
        )
    }

    @Test
    fun `spread meshes stay inside their own side of the gutter`() {
        val split = 0.46f
        val left = DewarpMesh.forHorizontalRegion(0f, split, pointsPerEdge = 5)
        val right = DewarpMesh.forHorizontalRegion(split, 1f, pointsPerEdge = 5)

        assertTrue(left.topPoints.all { it.x <= split })
        assertTrue(right.topPoints.all { it.x >= split })
        assertEquals(5, left.topPoints.size)
        assertEquals(5, right.bottomPoints.size)
    }
}
