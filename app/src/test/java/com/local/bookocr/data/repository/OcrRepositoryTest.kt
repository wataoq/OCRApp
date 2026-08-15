package com.local.bookocr.data.repository

import com.local.bookocr.ocr.FakeOcrEngine
import com.local.bookocr.ocr.OcrEngineRegistry
import java.io.File
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OcrRepositoryTest {

    private lateinit var ocrResultDao: FakeOcrResultDao
    private lateinit var ocrEngine: FakeOcrEngine
    private lateinit var repository: OcrRepository
    private val imageFile = File("dummy.jpg")

    @Before
    fun setUp() {
        ocrResultDao = FakeOcrResultDao()
        ocrEngine = FakeOcrEngine()
        repository = OcrRepository(ocrResultDao, ocrEngine, workDispatcher = UnconfinedTestDispatcher())
    }

    @Test
    fun `initial OCR seeds editedText equal to rawText`() = runTest {
        ocrEngine.setNextResult("初回のOCR結果")

        val result = repository.runInitialOcr(pageId = 1L, imageFile = imageFile).getOrThrow()

        assertEquals("初回のOCR結果", result.rawText)
        assertEquals("初回のOCR結果", result.editedText)
    }

    @Test
    fun `saving edited text never changes rawText`() = runTest {
        ocrEngine.setNextResult("元のOCR結果")
        repository.runInitialOcr(pageId = 1L, imageFile = imageFile)

        repository.saveEditedText(pageId = 1L, editedText = "ユーザーが修正した文章")

        val stored = ocrResultDao.getByPageIdOnce(1L)!!
        assertEquals("元のOCR結果", stored.rawText)
        assertEquals("ユーザーが修正した文章", stored.editedText)
    }

    @Test
    fun `resetToRaw restores editedText from rawText without touching rawText`() = runTest {
        ocrEngine.setNextResult("元のOCR結果")
        repository.runInitialOcr(pageId = 1L, imageFile = imageFile)
        repository.saveEditedText(pageId = 1L, editedText = "編集済み")

        repository.resetToRaw(pageId = 1L)

        val stored = ocrResultDao.getByPageIdOnce(1L)!!
        assertEquals("元のOCR結果", stored.rawText)
        assertEquals("元のOCR結果", stored.editedText)
    }

    @Test
    fun `rerun preserves user edits when editedText differs from rawText`() = runTest {
        ocrEngine.setNextResult("1回目の結果")
        repository.runInitialOcr(pageId = 1L, imageFile = imageFile)
        repository.saveEditedText(pageId = 1L, editedText = "ユーザーの修正")

        ocrEngine.setNextResult("2回目の結果")
        val outcome = repository.rerunOcr(pageId = 1L, imageFile = imageFile).getOrThrow()

        assertEquals(OcrRerunOutcome.EditsPreserved, outcome)
        val stored = ocrResultDao.getByPageIdOnce(1L)!!
        assertEquals("2回目の結果", stored.rawText)
        assertEquals("ユーザーの修正", stored.editedText)
    }

    @Test
    fun `rerun replaces editedText when the user never edited it`() = runTest {
        ocrEngine.setNextResult("1回目の結果")
        repository.runInitialOcr(pageId = 1L, imageFile = imageFile)

        ocrEngine.setNextResult("2回目の結果")
        val outcome = repository.rerunOcr(pageId = 1L, imageFile = imageFile).getOrThrow()

        assertEquals(OcrRerunOutcome.EditsReplaced, outcome)
        val stored = ocrResultDao.getByPageIdOnce(1L)!!
        assertEquals("2回目の結果", stored.rawText)
        assertEquals("2回目の結果", stored.editedText)
    }

    @Test
    fun `OCR engine failure does not create a result row`() = runTest {
        val failingEngine = FakeOcrEngine(shouldFail = true)
        val failingRepository = OcrRepository(ocrResultDao, failingEngine, workDispatcher = UnconfinedTestDispatcher())

        val result = failingRepository.runInitialOcr(pageId = 1L, imageFile = imageFile)

        assertTrue(result.isFailure)
        assertNull(ocrResultDao.getByPageIdOnce(1L))
    }

    @Test
    fun `explicit engine selection persists selected engine and preserves edits`() = runTest {
        val first = FakeOcrEngine(nextText = "first output", engineId = "first")
        val second = FakeOcrEngine(nextText = "second output", engineId = "second")
        repository = OcrRepository(
            ocrResultDao,
            OcrEngineRegistry(listOf(first, second), defaultEngineId = first.engineId),
            workDispatcher = UnconfinedTestDispatcher(),
        )
        repository.runInitialOcr(pageId = 1L, imageFile = imageFile)
        repository.saveEditedText(pageId = 1L, editedText = "user correction")

        val outcome = repository.rerunOcr(
            pageId = 1L,
            imageFile = imageFile,
            engineId = second.engineId,
        ).getOrThrow()

        assertEquals(OcrRerunOutcome.EditsPreserved, outcome)
        val stored = ocrResultDao.getByPageIdOnce(1L)!!
        assertEquals("second output", stored.rawText)
        assertEquals("user correction", stored.editedText)
        assertEquals(second.engineId, stored.engineId)
        assertEquals(1, first.recognizeCallCount)
        assertEquals(1, second.recognizeCallCount)
    }
}
