package com.local.bookocr.ocr

import com.local.bookocr.ocr.model.OcrDocument
import java.io.File

/**
 * Replaceable OCR abstraction. Implementations must not be referenced by the UI or
 * repository layer by concrete type - depend on this interface so a future engine
 * (e.g. a cloud OCR service) can be swapped in without touching screens or persistence.
 */
interface OcrEngine {
    val engineId: String
    val displayName: String get() = engineId

    /** [imageFile] is the app-private stored copy of the page photo. */
    suspend fun recognize(imageFile: File): OcrDocument
}

data class OcrEngineOption(val id: String, val displayName: String)

class OcrEngineRegistry(
    engines: List<OcrEngine>,
    val defaultEngineId: String = engines.firstOrNull()?.engineId
        ?: error("At least one OCR engine is required"),
) {
    private val enginesById = engines.associateBy { it.engineId }

    init {
        require(enginesById.size == engines.size) { "OCR engine ids must be unique" }
        require(defaultEngineId in enginesById) { "Default OCR engine is not registered" }
    }

    val options: List<OcrEngineOption> = engines.map { OcrEngineOption(it.engineId, it.displayName) }

    fun requireEngine(engineId: String): OcrEngine = enginesById[engineId]
        ?: throw OcrException("選択したOCRエンジンを利用できません")
}

class OcrException(message: String, cause: Throwable? = null) : Exception(message, cause)
