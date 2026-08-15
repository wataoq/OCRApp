package com.local.bookocr.ocr.model

import kotlinx.serialization.Serializable

/**
 * Engine-independent OCR result. UI and persistence code depend only on this model,
 * never on ML Kit (or any future engine's) types - see [com.local.bookocr.ocr.OcrEngine].
 */
@Serializable
data class OcrDocument(
    val rawText: String,
    val engineId: String,
    val blocks: List<OcrBlock> = emptyList(),
)

@Serializable
data class OcrBlock(
    val text: String,
    val boundingBox: OcrBoundingBox?,
    val lines: List<OcrLine> = emptyList(),
)

@Serializable
data class OcrLine(
    val text: String,
    val boundingBox: OcrBoundingBox?,
    val elements: List<OcrElement> = emptyList(),
)

@Serializable
data class OcrElement(
    val text: String,
    val boundingBox: OcrBoundingBox?,
)

@Serializable
data class OcrBoundingBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)
