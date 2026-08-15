package com.local.bookocr.imageprocessor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import com.local.bookocr.imageprocessor.internal.BitmapDecoding
import com.local.bookocr.imageprocessor.model.EnhancementMode
import com.local.bookocr.imageprocessor.model.ImageProcessingSettings
import com.local.bookocr.imageprocessor.model.PerspectiveQuad
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android-SDK-only [ImageProcessor]. No OpenCV: EXIF handling comes from androidx.exifinterface,
 * perspective correction from [Matrix.setPolyToPoly] (a genuine projective transform for 4 point
 * pairs), and enhancement from [ColorMatrix]. This keeps the dependency footprint minimal per the
 * project's dependency policy - OpenCV would add tens of MB of native libs for functionality the
 * platform already provides at the precision this feature needs.
 *
 * Intermediate bitmaps are recycled deterministically to avoid OOM on high-resolution book photos.
 */
class BitmapImageProcessor : ImageProcessor {

    override suspend fun process(
        sourceFile: File,
        settings: ImageProcessingSettings,
        outputFile: File,
    ): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            val source = BitmapDecoding.decodeUpright(sourceFile) ?: error("画像を読み込めませんでした")
            val processed = applySettings(source, settings)
            try {
                outputFile.parentFile?.mkdirs()
                FileOutputStream(outputFile).use { out ->
                    processed.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
                Unit
            } finally {
                processed.recycle()
            }
        }
    }

    override suspend fun processForPreview(
        sourceFile: File,
        settings: ImageProcessingSettings,
        maxDimension: Int,
    ): Result<Bitmap> = withContext(Dispatchers.Default) {
        runCatching {
            val source = BitmapDecoding.decodeUpright(sourceFile, maxDimension)
                ?: error("画像を読み込めませんでした")
            applySettings(source, settings)
        }
    }

    // --- Pipeline --------------------------------------------------------------------------

    private fun applySettings(source: Bitmap, settings: ImageProcessingSettings): Bitmap {
        // Track intermediates so we can recycle everything except [source] (owned by the caller
        // chain / recycled at the loader boundary) and the final returned bitmap.
        val intermediates = mutableListOf<Bitmap>()
        var current = source
        var perspectivePoints = settings.perspectivePoints

        fun advance(next: Bitmap) {
            if (next !== current && current !== source) intermediates += current
            current = next
        }

        if (settings.rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(settings.rotationDegrees.toFloat()) }
            advance(Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true))
        }

        settings.cropRect?.let { rect ->
            val x = (rect.left * current.width).roundToInt().coerceIn(0, current.width - 1)
            val y = (rect.top * current.height).roundToInt().coerceIn(0, current.height - 1)
            val w = ((rect.right - rect.left) * current.width).roundToInt().coerceIn(1, current.width - x)
            val h = ((rect.bottom - rect.top) * current.height).roundToInt().coerceIn(1, current.height - y)
            perspectivePoints = perspectivePoints?.relativeTo(rect)
            advance(Bitmap.createBitmap(current, x, y, w, h))
        }

        perspectivePoints?.let { quad ->
            advance(applyPerspective(current, quad))
        }

        if (settings.enhancementMode != EnhancementMode.ORIGINAL) {
            advance(applyEnhancement(current, settings.enhancementMode))
        }

        intermediates.forEach { it.recycle() }
        // If nothing changed [current] is still [source]; hand back a copy so the caller can freely
        // recycle the returned bitmap without touching a source it may reuse.
        return if (current === source) source.copy(source.config ?: Bitmap.Config.ARGB_8888, false) else current
    }

    /**
     * Maps the user's [quad] (the four page corners) onto an axis-aligned rectangle whose size is
     * derived from the quad's edge lengths, straightening keystone/trapezoid distortion from an
     * angled shot. Uses [Matrix.setPolyToPoly] with 4 point pairs - a true perspective transform.
     */
    private fun applyPerspective(src: Bitmap, quad: PerspectiveQuad): Bitmap {
        val w = src.width.toFloat()
        val h = src.height.toFloat()

        val tlx = quad.topLeftX * w; val tly = quad.topLeftY * h
        val trx = quad.topRightX * w; val tryy = quad.topRightY * h
        val brx = quad.bottomRightX * w; val bry = quad.bottomRightY * h
        val blx = quad.bottomLeftX * w; val bly = quad.bottomLeftY * h

        val destW = max(
            hypot((trx - tlx).toDouble(), (tryy - tly).toDouble()),
            hypot((brx - blx).toDouble(), (bry - bly).toDouble()),
        ).roundToInt().coerceAtLeast(1)
        val destH = max(
            hypot((blx - tlx).toDouble(), (bly - tly).toDouble()),
            hypot((brx - trx).toDouble(), (bry - tryy).toDouble()),
        ).roundToInt().coerceAtLeast(1)

        val matrix = Matrix()
        matrix.setPolyToPoly(
            floatArrayOf(tlx, tly, trx, tryy, brx, bry, blx, bly), 0,
            floatArrayOf(0f, 0f, destW.toFloat(), 0f, destW.toFloat(), destH.toFloat(), 0f, destH.toFloat()), 0,
            4,
        )
        val dest = Bitmap.createBitmap(destW, destH, Bitmap.Config.ARGB_8888)
        Canvas(dest).drawBitmap(src, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return dest
    }

    private fun applyEnhancement(src: Bitmap, mode: EnhancementMode): Bitmap {
        val colorMatrix = when (mode) {
            EnhancementMode.GRAYSCALE -> ColorMatrix().apply { setSaturation(0f) }
            EnhancementMode.HIGH_CONTRAST -> ColorMatrix().apply {
                setSaturation(0f)
                // Moderate contrast stretch (~1.5x around mid-gray). Intentionally not a hard
                // threshold: preserves thin strokes and ruby that binarization would destroy.
                postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            CONTRAST, 0f, 0f, 0f, CONTRAST_SHIFT,
                            0f, CONTRAST, 0f, 0f, CONTRAST_SHIFT,
                            0f, 0f, CONTRAST, 0f, CONTRAST_SHIFT,
                            0f, 0f, 0f, 1f, 0f,
                        ),
                    ),
                )
            }
            EnhancementMode.ORIGINAL -> return src
        }
        val dest = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(dest).drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(colorMatrix) })
        return dest
    }

    private companion object {
        const val JPEG_QUALITY = 90
        const val CONTRAST = 1.5f
        const val CONTRAST_SHIFT = -40f
    }
}
