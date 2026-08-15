package com.local.bookocr.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class BookValidationTest {

    @Test
    fun `blank title is invalid`() {
        assertEquals(TitleValidationResult.Blank, BookValidation.validateTitle("   "))
    }

    @Test
    fun `empty title is invalid`() {
        assertEquals(TitleValidationResult.Blank, BookValidation.validateTitle(""))
    }

    @Test
    fun `non-blank title is valid`() {
        assertEquals(TitleValidationResult.Valid, BookValidation.validateTitle("こころ"))
    }
}
