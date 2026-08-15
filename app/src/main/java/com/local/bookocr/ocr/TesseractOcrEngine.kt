package com.local.bookocr.ocr

import android.content.Context
import com.googlecode.tesseract.android.TessBaseAPI
import com.local.bookocr.imageprocessor.internal.BitmapDecoding
import com.local.bookocr.ocr.model.OcrDocument
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TesseractOcrEngine(
    context: Context,
    private val model: TesseractLanguageModel,
    override val engineId: String,
    override val displayName: String,
    private val pageSegMode: Int,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : OcrEngine {

    private val installer = TesseractDataInstaller(context.applicationContext)

    override suspend fun recognize(imageFile: File): OcrDocument = withContext(workDispatcher) {
        val dataRoot = installer.ensureInstalled(model)
        val bitmap = BitmapDecoding.decodeUpright(imageFile, MAX_DIMENSION_PX)
            ?: throw OcrException("画像を読み込めませんでした")
        val api = TessBaseAPI()
        try {
            val initialized = api.init(dataRoot.absolutePath, model.language, TessBaseAPI.OEM_LSTM_ONLY)
            if (!initialized) throw OcrException("Tesseractの初期化に失敗しました")
            api.setPageSegMode(pageSegMode)
            api.setImage(bitmap)
            OcrDocument(
                rawText = api.utF8Text.orEmpty().trimEnd(),
                engineId = engineId,
            )
        } catch (error: OcrException) {
            throw error
        } catch (error: Exception) {
            throw OcrException("Tesseractによる文字認識に失敗しました", error)
        } finally {
            runCatching { api.clear() }
            api.recycle()
            bitmap.recycle()
        }
    }

    companion object {
        const val HORIZONTAL_ENGINE_ID = "tesseract-5.5.1-jpn-fast"
        const val VERTICAL_ENGINE_ID = "tesseract-5.5.1-jpn-vert-fast"

        val JAPANESE_MODEL = TesseractLanguageModel(
            language = "jpn",
            assetName = "jpn.traineddata",
            sha256 = "1F5DE9236D2E85F5FDF4B3C500F2D4926F8D9449F28F5394472D9E8D83B91B4D",
        )
        val JAPANESE_VERTICAL_MODEL = TesseractLanguageModel(
            language = "jpn_vert",
            assetName = "jpn_vert.traineddata",
            sha256 = "BF1E2640954691797E2DC14F38533E601B59EE37958698AE0F0B81DC6F09C71B",
        )

        const val HORIZONTAL_PAGE_SEG_MODE = TessBaseAPI.PageSegMode.PSM_AUTO
        const val VERTICAL_PAGE_SEG_MODE = TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK_VERT_TEXT
        private const val MAX_DIMENSION_PX = 2400
    }
}
