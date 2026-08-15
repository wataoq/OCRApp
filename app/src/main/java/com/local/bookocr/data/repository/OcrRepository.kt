package com.local.bookocr.data.repository

import com.local.bookocr.data.local.dao.OcrResultDao
import com.local.bookocr.data.local.entity.OcrResultEntity
import com.local.bookocr.ocr.OcrEngine
import com.local.bookocr.ocr.OcrEngineOption
import com.local.bookocr.ocr.OcrEngineRegistry
import com.local.bookocr.ocr.OcrException
import com.local.bookocr.ocr.model.layoutBlocksToJsonOrNull
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

sealed interface OcrRerunOutcome {
    data object EditsPreserved : OcrRerunOutcome
    data object EditsReplaced : OcrRerunOutcome
}

class OcrRepository(
    private val ocrResultDao: OcrResultDao,
    private val engineRegistry: OcrEngineRegistry,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    constructor(
        ocrResultDao: OcrResultDao,
        ocrEngine: OcrEngine,
        workDispatcher: CoroutineDispatcher = Dispatchers.Default,
    ) : this(ocrResultDao, OcrEngineRegistry(listOf(ocrEngine)), workDispatcher)

    val availableEngines: List<OcrEngineOption> get() = engineRegistry.options
    val defaultEngineId: String get() = engineRegistry.defaultEngineId

    fun observeResult(pageId: Long): Flow<OcrResultEntity?> = ocrResultDao.observeByPageId(pageId)

    suspend fun getResultOnce(pageId: Long): OcrResultEntity? = ocrResultDao.getByPageIdOnce(pageId)

    /** First OCR pass for a page: editedText starts out equal to rawText. */
    suspend fun runInitialOcr(
        pageId: Long,
        imageFile: File,
        engineId: String = defaultEngineId,
    ): Result<OcrResultEntity> =
        withContext(workDispatcher) {
            runCatching {
                val doc = engineRegistry.requireEngine(engineId).recognize(imageFile)
                val now = System.currentTimeMillis()
                ocrResultDao.upsert(
                    OcrResultEntity(
                        pageId = pageId,
                        rawText = doc.rawText,
                        editedText = doc.rawText,
                        engineId = doc.engineId,
                        ocrTimestamp = now,
                        layoutJson = doc.layoutBlocksToJsonOrNull(),
                    ),
                )
                ocrResultDao.getByPageIdOnce(pageId)
                    ?: throw OcrException("OCR結果の保存に失敗しました")
            }
        }

    /**
     * Re-runs OCR on a page that may already have a user-edited transcript. If the user
     * never touched editedText (it still equals the previous rawText), the new raw output
     * replaces it too. Otherwise the edited transcript is preserved untouched and only
     * rawText is refreshed - a rerun can never silently destroy corrections.
     */
    suspend fun rerunOcr(
        pageId: Long,
        imageFile: File,
        engineId: String? = null,
    ): Result<OcrRerunOutcome> =
        withContext(workDispatcher) {
            runCatching {
                val existing = ocrResultDao.getByPageIdOnce(pageId)
                val requestedEngineId = engineId ?: existing?.engineId ?: defaultEngineId
                val doc = engineRegistry.requireEngine(requestedEngineId).recognize(imageFile)
                val now = System.currentTimeMillis()
                val userHadEdited = existing != null && existing.editedText != existing.rawText
                val newEditedText = if (userHadEdited) existing!!.editedText else doc.rawText
                ocrResultDao.upsert(
                    OcrResultEntity(
                        pageId = pageId,
                        rawText = doc.rawText,
                        editedText = newEditedText,
                        engineId = doc.engineId,
                        ocrTimestamp = now,
                        layoutJson = doc.layoutBlocksToJsonOrNull(),
                    ),
                )
                if (userHadEdited) OcrRerunOutcome.EditsPreserved else OcrRerunOutcome.EditsReplaced
            }
        }

    suspend fun saveEditedText(pageId: Long, editedText: String): Result<Unit> = runCatching {
        ocrResultDao.updateEditedText(pageId, editedText, System.currentTimeMillis())
    }

    suspend fun resetToRaw(pageId: Long): Result<Unit> = runCatching {
        ocrResultDao.resetEditedToRaw(pageId, System.currentTimeMillis())
    }
}
