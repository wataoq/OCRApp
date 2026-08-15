package com.local.bookocr.data.repository

import android.graphics.Bitmap
import com.local.bookocr.imageprocessor.ImageProcessor
import com.local.bookocr.imageprocessor.model.ImageProcessingSettings
import java.io.File

/**
 * Test double for [ImageProcessor]. [process] writes placeholder bytes to the output file so
 * repository tests can assert a processed file was produced without decoding real bitmaps.
 * Preview is unsupported (unit tests never render).
 */
class FakeImageProcessor : ImageProcessor {
    var processResult: Result<Unit> = Result.success(Unit)
    var processCallCount = 0
    var lastSettings: ImageProcessingSettings? = null

    override suspend fun process(
        sourceFile: File,
        settings: ImageProcessingSettings,
        outputFile: File,
    ): Result<Unit> {
        processCallCount++
        lastSettings = settings
        if (processResult.isSuccess) {
            outputFile.parentFile?.mkdirs()
            outputFile.writeText("fake-processed")
        }
        return processResult
    }

    override suspend fun processForPreview(
        sourceFile: File,
        settings: ImageProcessingSettings,
        maxDimension: Int,
    ): Result<Bitmap> = Result.failure(UnsupportedOperationException("preview not needed in unit tests"))
}
