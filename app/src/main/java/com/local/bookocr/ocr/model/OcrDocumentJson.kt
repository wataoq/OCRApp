package com.local.bookocr.ocr.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

/** Returns null when there is no layout detail worth persisting (keeps rows compact). */
fun OcrDocument.layoutBlocksToJsonOrNull(): String? =
    if (blocks.isEmpty()) null else json.encodeToString(blocks)

fun decodeLayoutBlocks(layoutJson: String?): List<OcrBlock> =
    if (layoutJson.isNullOrBlank()) emptyList() else json.decodeFromString(layoutJson)
