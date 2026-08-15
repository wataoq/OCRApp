package com.local.bookocr.ocr

import com.local.bookocr.ocr.model.OcrDocument
import java.io.File

/**
 * Deterministic test double for [OcrEngine]. Returns newly authored fixture text (not real
 * book content) so unit tests never depend on actual ML Kit recognition quality.
 */
class FakeOcrEngine(
    private var nextText: String = DEFAULT_TEXT,
    private val shouldFail: Boolean = false,
) : OcrEngine {

    override val engineId: String = "fake-ocr-engine"

    var recognizeCallCount = 0
        private set

    fun setNextResult(text: String) {
        nextText = text
    }

    override suspend fun recognize(imageFile: File): OcrDocument {
        recognizeCallCount++
        if (shouldFail) throw OcrException("文字認識に失敗しました")
        return OcrDocument(rawText = nextText, engineId = engineId)
    }

    companion object {
        const val DEFAULT_TEXT =
            "経済活動とは、この運動をわがものにし、それがもたらす可能性を一定目的に使用することである。"
    }
}
