package com.local.bookocr.ocr

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.local.bookocr.ocr.model.OcrBlock
import com.local.bookocr.ocr.model.OcrBoundingBox
import com.local.bookocr.ocr.model.OcrDocument
import com.local.bookocr.ocr.model.OcrElement
import com.local.bookocr.ocr.model.OcrLine
import com.local.bookocr.imageprocessor.internal.BitmapDecoding
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * On-device Japanese OCR via ML Kit Text Recognition v2. The recognizer client is created
 * once and reused for the lifetime of this engine instance (held app-scoped by
 * [com.local.bookocr.DefaultAppContainer]) to avoid repeated unnecessary construction.
 */
class MlKitOcrEngine : OcrEngine {

    override val engineId: String = ENGINE_ID
    override val displayName: String = "ML Kit（日本語）"

    private val recognizer by lazy {
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    }

    override suspend fun recognize(imageFile: File): OcrDocument {
        val bitmap = BitmapDecoding.decodeUpright(imageFile, MAX_DIMENSION_PX)
            ?: throw OcrException("画像を読み込めませんでした")
        try {
            val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            return text.toOcrDocument(engineId)
        } catch (e: OcrException) {
            throw e
        } catch (e: Exception) {
            throw OcrException("文字認識に失敗しました", e)
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result -> continuation.resume(result) }
        addOnFailureListener { error -> continuation.resumeWithException(error) }
    }

    companion object {
        const val ENGINE_ID = "mlkit-text-recognition-japanese-v2"
        private const val MAX_DIMENSION_PX = 2000
    }
}

private fun Text.toOcrDocument(engineId: String): OcrDocument {
    val layout = textBlocks.mapIndexed { index, block ->
        val box = block.boundingBox
        ReadingOrderBlock(index, box?.left, box?.top, box?.right, box?.bottom)
    }
    val sortedBlocks = readingOrder(layout).map(textBlocks::get)
    return OcrDocument(
        rawText = sortedBlocks.joinToString("\n") { it.text },
        engineId = engineId,
        blocks = sortedBlocks.map { block ->
            OcrBlock(
                text = block.text,
                boundingBox = block.boundingBox?.toOcrBoundingBox(),
                lines = block.lines.map { line ->
                    OcrLine(
                        text = line.text,
                        boundingBox = line.boundingBox?.toOcrBoundingBox(),
                        elements = line.elements.map { element ->
                            OcrElement(
                                text = element.text,
                                boundingBox = element.boundingBox?.toOcrBoundingBox(),
                            )
                        },
                    )
                },
            )
        },
    )
}

private fun android.graphics.Rect.toOcrBoundingBox() = OcrBoundingBox(left, top, right, bottom)
