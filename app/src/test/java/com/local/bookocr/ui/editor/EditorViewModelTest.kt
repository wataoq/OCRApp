package com.local.bookocr.ui.editor

import androidx.lifecycle.SavedStateHandle
import com.local.bookocr.data.local.entity.PageEntity
import com.local.bookocr.data.repository.FakeImageStorage
import com.local.bookocr.data.repository.FakeOcrResultDao
import com.local.bookocr.data.repository.FakePageDao
import com.local.bookocr.data.repository.OcrRepository
import com.local.bookocr.data.repository.PageRepository
import com.local.bookocr.ocr.FakeOcrEngine
import com.local.bookocr.ui.navigation.BookOcrDestinations
import com.local.bookocr.util.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class EditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var pageDao: FakePageDao
    private lateinit var ocrResultDao: FakeOcrResultDao
    private lateinit var ocrEngine: FakeOcrEngine
    private lateinit var pageRepository: PageRepository
    private lateinit var ocrRepository: OcrRepository
    private var pageId: Long = 0

    @Before
    fun setUp() {
        pageDao = FakePageDao()
        ocrResultDao = FakeOcrResultDao()
        ocrEngine = FakeOcrEngine()
        pageRepository = PageRepository(pageDao, FakeImageStorage(), ioDispatcher = mainDispatcherRule.dispatcher)
        ocrRepository = OcrRepository(ocrResultDao, ocrEngine, workDispatcher = mainDispatcherRule.dispatcher)
    }

    private suspend fun insertPage(): Long = pageDao.insert(
        PageEntity(bookId = 1L, pageNumber = 1, storedImagePath = "p.jpg", createdAt = 1, updatedAt = 1),
    )

    private fun createViewModel(): EditorViewModel {
        val handle = SavedStateHandle(mapOf(BookOcrDestinations.PAGE_ID_ARG to pageId))
        return EditorViewModel(handle, pageRepository, ocrRepository)
    }

    @Test
    fun `automatically runs OCR once for a fresh page and seeds editedText from rawText`() = runTest {
        pageId = insertPage()
        ocrEngine.setNextResult("自動認識結果")

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(OcrPhase.Ready, viewModel.uiState.value.phase)
        assertEquals("自動認識結果", viewModel.uiState.value.rawText)
        assertEquals("自動認識結果", viewModel.uiState.value.editedText)
        assertEquals(1, ocrEngine.recognizeCallCount)
    }

    @Test
    fun `editing then saving preserves rawText`() = runTest {
        pageId = insertPage()
        ocrEngine.setNextResult("元の認識結果")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onEditedTextChanged("修正後のテキスト")
        viewModel.onSave()
        advanceUntilIdle()

        val stored = ocrResultDao.getByPageIdOnce(pageId)!!
        assertEquals("元の認識結果", stored.rawText)
        assertEquals("修正後のテキスト", stored.editedText)
        assertEquals(false, viewModel.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun `reset to raw restores editedText from rawText`() = runTest {
        pageId = insertPage()
        ocrEngine.setNextResult("元の認識結果")
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onEditedTextChanged("修正後のテキスト")
        viewModel.onSave()
        advanceUntilIdle()

        viewModel.onResetToRaw()
        advanceUntilIdle()

        assertEquals("元の認識結果", viewModel.uiState.value.editedText)
        assertEquals("元の認識結果", viewModel.uiState.value.rawText)
    }

    @Test
    fun `rerun preserves edits when the user had edited the transcript`() = runTest {
        pageId = insertPage()
        ocrEngine.setNextResult("1回目の結果")
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onEditedTextChanged("ユーザーの修正")
        viewModel.onSave()
        advanceUntilIdle()

        ocrEngine.setNextResult("2回目の結果")
        viewModel.onRerunOcr()
        advanceUntilIdle()

        assertEquals("2回目の結果", viewModel.uiState.value.rawText)
        assertEquals("ユーザーの修正", viewModel.uiState.value.editedText)
    }

    @Test
    fun `rerun replaces editedText when the user never edited it`() = runTest {
        pageId = insertPage()
        ocrEngine.setNextResult("1回目の結果")
        val viewModel = createViewModel()
        advanceUntilIdle()

        ocrEngine.setNextResult("2回目の結果")
        viewModel.onRerunOcr()
        advanceUntilIdle()

        assertEquals("2回目の結果", viewModel.uiState.value.rawText)
        assertEquals("2回目の結果", viewModel.uiState.value.editedText)
    }

    @Test
    fun `OCR failure on a fresh page surfaces an error phase`() = runTest {
        pageId = insertPage()
        val failingEngine = FakeOcrEngine(shouldFail = true)
        val failingOcrRepository = OcrRepository(ocrResultDao, failingEngine, workDispatcher = mainDispatcherRule.dispatcher)
        val viewModel = EditorViewModel(
            SavedStateHandle(mapOf(BookOcrDestinations.PAGE_ID_ARG to pageId)),
            pageRepository,
            failingOcrRepository,
        )

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.phase is OcrPhase.Error)
    }
}
