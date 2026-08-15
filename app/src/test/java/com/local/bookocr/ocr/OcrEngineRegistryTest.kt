package com.local.bookocr.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OcrEngineRegistryTest {

    @Test
    fun `registry exposes options and resolves configured default`() {
        val first = FakeOcrEngine(engineId = "first", displayName = "First")
        val second = FakeOcrEngine(engineId = "second", displayName = "Second")

        val registry = OcrEngineRegistry(listOf(first, second), defaultEngineId = second.engineId)

        assertEquals(second.engineId, registry.defaultEngineId)
        assertEquals(listOf("first", "second"), registry.options.map { it.id })
        assertEquals(second, registry.requireEngine(second.engineId))
    }

    @Test
    fun `registry rejects duplicate ids`() {
        assertThrows(IllegalArgumentException::class.java) {
            OcrEngineRegistry(
                listOf(
                    FakeOcrEngine(engineId = "same"),
                    FakeOcrEngine(engineId = "same"),
                ),
            )
        }
    }

    @Test
    fun `registry rejects an unknown engine`() {
        val registry = OcrEngineRegistry(listOf(FakeOcrEngine()))

        assertThrows(OcrException::class.java) { registry.requireEngine("unknown") }
    }
}
