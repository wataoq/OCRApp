package com.local.bookocr.data.repository

import android.graphics.Bitmap
import com.local.bookocr.imageprocessor.PageDewarper
import com.local.bookocr.imageprocessor.model.DewarpMesh
import java.io.File

/**
 * Test double for [PageDewarper]. [dewarp] writes placeholder bytes so repository/VM tests can
 * assert a dewarp file was produced without OpenCV. Preview is unsupported (unit tests never render).
 */
class FakePageDewarper : PageDewarper {
    var available: Boolean = true
    var dewarpResult: Result<Unit> = Result.success(Unit)
    var dewarpCallCount = 0
    var lastMesh: DewarpMesh? = null

    override fun isAvailable(): Boolean = available

    override suspend fun dewarp(sourceFile: File, mesh: DewarpMesh, outputFile: File): Result<Unit> {
        dewarpCallCount++
        lastMesh = mesh
        if (dewarpResult.isSuccess) {
            outputFile.parentFile?.mkdirs()
            outputFile.writeText("fake-dewarped")
        }
        return dewarpResult
    }

    override suspend fun dewarpForPreview(
        sourceFile: File,
        mesh: DewarpMesh,
        maxDimension: Int,
    ): Result<Bitmap> = Result.failure(UnsupportedOperationException("preview not needed in unit tests"))
}
