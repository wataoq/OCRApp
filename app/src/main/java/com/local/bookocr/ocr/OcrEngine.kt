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

    /** [imageFile] is the app-private stored copy of the page photo. */
    suspend fun recognize(imageFile: File): OcrDocument
}

class OcrException(message: String, cause: Throwable? = null) : Exception(message, cause)
