package com.local.bookocr.imageprocessor

import org.junit.Assert.assertEquals
import org.junit.Test

class GutterDetectorTest {

    @Test
    fun `detects a dark gutter near the center`() {
        val columns = DoubleArray(200) { 220.0 }
        for (x in 92..100) columns[x] = 35.0

        val split = GutterDetector.detectFromColumnLuminance(columns)

        assertEquals(0.48f, split, 0.03f)
    }

    @Test
    fun `falls back to center when no gutter has useful contrast`() {
        val columns = DoubleArray(200) { 210.0 }

        assertEquals(0.5f, GutterDetector.detectFromColumnLuminance(columns), 0.001f)
    }
}
