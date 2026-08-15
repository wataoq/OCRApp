package com.local.bookocr.imageprocessor

import android.graphics.Bitmap
import com.local.bookocr.imageprocessor.internal.BitmapDecoding
import com.local.bookocr.imageprocessor.model.DewarpMesh
import com.local.bookocr.imageprocessor.model.MeshPoint
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * OpenCV-backed curved-page dewarper. This is the *only* file that touches `org.opencv.*`; the rest
 * of the app sees only [PageDewarper] and plain data ([DewarpMesh]).
 *
 * Method: a user-guided ruled-surface remap. The user's [DewarpMesh] gives the text block's top and
 * bottom edges as curves (paired columns). For each output pixel at normalized (u, v) we find the
 * source point by interpolating along the top curve and the bottom curve at u, then linearly
 * blending by v - so a page bowed near the spine is straightened. The displacement field is built on
 * a coarse grid and upscaled with [Imgproc.resize] before [Imgproc.remap], which is fast and smooth
 * (no per-pixel Kotlin loop over millions of pixels). Core OpenCV only - no contrib/TPS module.
 */
class OpenCvPageDewarper : PageDewarper {

    private val initialized: Boolean by lazy { runCatching { OpenCVLoader.initLocal() }.getOrDefault(false) }

    override fun isAvailable(): Boolean = initialized

    override suspend fun dewarp(
        sourceFile: File,
        mesh: DewarpMesh,
        outputFile: File,
    ): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            check(initialized) { "OpenCVを初期化できませんでした" }
            require(mesh.isValid) { "ゆがみ補正のメッシュが不正です" }
            val source = BitmapDecoding.decodeUpright(sourceFile) ?: error("画像を読み込めませんでした")
            val result = try {
                remapToBitmap(source, mesh)
            } finally {
                source.recycle()
            }
            try {
                outputFile.parentFile?.mkdirs()
                FileOutputStream(outputFile).use { out ->
                    result.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
                Unit
            } finally {
                result.recycle()
            }
        }
    }

    override suspend fun dewarpForPreview(
        sourceFile: File,
        mesh: DewarpMesh,
        maxDimension: Int,
    ): Result<Bitmap> = withContext(Dispatchers.Default) {
        runCatching {
            check(initialized) { "OpenCVを初期化できませんでした" }
            require(mesh.isValid) { "ゆがみ補正のメッシュが不正です" }
            val source = BitmapDecoding.decodeUpright(sourceFile, maxDimension)
                ?: error("画像を読み込めませんでした")
            try {
                remapToBitmap(source, mesh)
            } finally {
                source.recycle()
            }
        }
    }

    private fun remapToBitmap(source: Bitmap, mesh: DewarpMesh): Bitmap {
        val srcW = source.width
        val srcH = source.height

        // Denormalize the two edge curves into source-pixel coordinates.
        val top = mesh.topPoints.map { it.toPixel(srcW, srcH) }
        val bottom = mesh.bottomPoints.map { it.toPixel(srcW, srcH) }

        // Output rectangle: width from the longer horizontal edge, height from the longer side edge.
        val destW = max(polylineLength(top), polylineLength(bottom))
            .roundToInt().coerceIn(1, srcW * 2)
        val destH = max(distance(top.first(), bottom.first()), distance(top.last(), bottom.last()))
            .roundToInt().coerceIn(1, srcH * 2)

        val srcMat = Mat()
        val coarseX = Mat(GRID, GRID, CvType.CV_32FC1)
        val coarseY = Mat(GRID, GRID, CvType.CV_32FC1)
        val mapX = Mat()
        val mapY = Mat()
        val dstMat = Mat()
        try {
            Utils.bitmapToMat(source, srcMat)

            // Build the displacement field on a coarse GRID x GRID lattice.
            val xs = FloatArray(GRID * GRID)
            val ys = FloatArray(GRID * GRID)
            for (row in 0 until GRID) {
                val v = row.toFloat() / (GRID - 1)
                for (col in 0 until GRID) {
                    val u = col.toFloat() / (GRID - 1)
                    val topPt = interpolate(top, u)
                    val botPt = interpolate(bottom, u)
                    val sx = topPt.first + v * (botPt.first - topPt.first)
                    val sy = topPt.second + v * (botPt.second - topPt.second)
                    xs[row * GRID + col] = sx
                    ys[row * GRID + col] = sy
                }
            }
            coarseX.put(0, 0, xs)
            coarseY.put(0, 0, ys)

            // Upscale the coarse field to the full output size, then remap.
            val destSize = Size(destW.toDouble(), destH.toDouble())
            Imgproc.resize(coarseX, mapX, destSize, 0.0, 0.0, Imgproc.INTER_LINEAR)
            Imgproc.resize(coarseY, mapY, destSize, 0.0, 0.0, Imgproc.INTER_LINEAR)
            Imgproc.remap(srcMat, dstMat, mapX, mapY, Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE)

            val out = Bitmap.createBitmap(destW, destH, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(dstMat, out)
            return out
        } finally {
            srcMat.release(); coarseX.release(); coarseY.release()
            mapX.release(); mapY.release(); dstMat.release()
        }
    }

    /** Linear interpolation along a polyline parameterized uniformly by control-point index. */
    private fun interpolate(points: List<Pair<Float, Float>>, u: Float): Pair<Float, Float> {
        if (points.size == 1) return points[0]
        val t = (u.coerceIn(0f, 1f)) * (points.size - 1)
        val i = t.toInt().coerceIn(0, points.size - 2)
        val f = t - i
        val a = points[i]
        val b = points[i + 1]
        return (a.first + f * (b.first - a.first)) to (a.second + f * (b.second - a.second))
    }

    private fun polylineLength(points: List<Pair<Float, Float>>): Double {
        var len = 0.0
        for (i in 0 until points.size - 1) len += distance(points[i], points[i + 1])
        return len
    }

    private fun distance(a: Pair<Float, Float>, b: Pair<Float, Float>): Double =
        hypot((a.first - b.first).toDouble(), (a.second - b.second).toDouble())

    private fun MeshPoint.toPixel(width: Int, height: Int): Pair<Float, Float> =
        (x * width) to (y * height)

    private companion object {
        const val JPEG_QUALITY = 90
        // Coarse displacement-field resolution. Smooth for page curvature; upscaled before remap.
        const val GRID = 64
    }
}
