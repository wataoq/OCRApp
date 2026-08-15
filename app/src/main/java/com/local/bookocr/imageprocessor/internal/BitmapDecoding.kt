package com.local.bookocr.imageprocessor.internal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * Shared image decoding for the correction pipeline: always returns an *upright* bitmap by applying
 * the source EXIF orientation, and optionally downsamples to bound memory. Used by both
 * [com.local.bookocr.imageprocessor.BitmapImageProcessor] and the OpenCV dewarper so the perspective
 * and dewarp paths operate in the exact same (EXIF-corrected) coordinate space the UI displays.
 * The original file is never modified.
 */
internal object BitmapDecoding {

    /**
     * Decodes [file] upright. When [maxDimension] is non-null the bitmap is downsampled so its
     * larger side is roughly [maxDimension] px (power-of-two inSampleSize). Returns null if the file
     * cannot be decoded.
     */
    fun decodeUpright(file: File, maxDimension: Int? = null): Bitmap? {
        val decoded = if (maxDimension == null) {
            BitmapFactory.decodeFile(file.absolutePath)
        } else {
            decodeSampled(file, maxDimension)
        } ?: return null
        return applyExifOrientation(decoded, file)
    }

    private fun decodeSampled(file: File, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= maxDimension ||
            bounds.outHeight / (sampleSize * 2) >= maxDimension
        ) {
            sampleSize *= 2
        }
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        )
    }

    private fun applyExifOrientation(bitmap: Bitmap, file: File): Bitmap {
        val exif = runCatching { ExifInterface(file.absolutePath) }.getOrNull() ?: return bitmap
        val matrix = Matrix()
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f); matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(-90f); matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            .also { if (it !== bitmap) bitmap.recycle() }
    }
}
