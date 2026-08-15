package com.local.bookocr.imageprocessor

import android.graphics.Bitmap
import com.local.bookocr.imageprocessor.model.DewarpMesh
import java.io.File

/**
 * Curved-page dewarp abstraction. Kept separate from [ImageProcessor] and behind this interface so
 * the OpenCV dependency and all `org.opencv.*` types stay confined to [OpenCvPageDewarper] - UI,
 * Room, repositories, and the OCR engine never see OpenCV, exactly as the perspective/enhancement
 * path keeps ML Kit and Bitmap details out of callers. A future non-OpenCV dewarper could drop in
 * without touching any caller.
 */
interface PageDewarper {

    /**
     * Whether the underlying engine is usable on this device/build (e.g. OpenCV native lib loaded).
     * Callers should hide or disable dewarp UI when false rather than failing at process time.
     */
    fun isAvailable(): Boolean

    /**
     * Remaps the curved region described by [mesh] in [sourceFile] (the untouched original) onto a
     * straightened rectangle written to [outputFile]. The source is never modified.
     */
    suspend fun dewarp(
        sourceFile: File,
        mesh: DewarpMesh,
        outputFile: File,
    ): Result<Unit>

    /** Downsampled preview (max dimension [maxDimension] px) so the UI never holds a full bitmap. */
    suspend fun dewarpForPreview(
        sourceFile: File,
        mesh: DewarpMesh,
        maxDimension: Int = 800,
    ): Result<Bitmap>

    companion object {
        /** Bumped when the dewarp algorithm changes, so stale outputs can be regenerated. */
        const val DEWARP_VERSION = 1
    }
}
