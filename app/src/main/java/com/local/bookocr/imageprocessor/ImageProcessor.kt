package com.local.bookocr.imageprocessor

import android.graphics.Bitmap
import com.local.bookocr.imageprocessor.model.ImageProcessingSettings
import java.io.File

/**
 * Abstraction over image correction. Kept as an interface (like [com.local.bookocr.ocr.OcrEngine]
 * and [com.local.bookocr.storage.ImageStorage]) so the concrete implementation - and any library
 * it uses - never leaks into UI, Room, or the OCR engine. A future OpenCV-backed processor could
 * replace [BitmapImageProcessor] without touching any caller.
 *
 * All work runs off the main thread; callers pass files and receive files/bitmaps, never Android
 * framework decoding concerns.
 */
interface ImageProcessor {

    /**
     * Applies [settings] to [sourceFile] (the untouched original) and writes the full-resolution
     * result to [outputFile]. The source is never modified. On failure the caller is expected to
     * fall back to the original image, so a failed process must not leave the original in a bad
     * state (it is only ever read here).
     */
    suspend fun process(
        sourceFile: File,
        settings: ImageProcessingSettings,
        outputFile: File,
    ): Result<Unit>

    /**
     * Produces a downsampled bitmap (max dimension [maxDimension] px) for on-screen preview.
     * Separate from [process] so the UI never holds a full-resolution processed bitmap in memory.
     */
    suspend fun processForPreview(
        sourceFile: File,
        settings: ImageProcessingSettings,
        maxDimension: Int = 800,
    ): Result<Bitmap>

    companion object {
        /**
         * Bumped whenever the processing algorithm changes in a way that would make existing
         * processed images stale. Persisted per page ([PageEntity.processingVersion]) so a future
         * version can detect and regenerate outdated processed images from the original + settings.
         */
        const val PROCESSING_VERSION = 1
    }
}
