package com.local.bookocr.imageprocessor

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max

/** Finds the dark vertical gutter near the center of a photographed two-page spread. */
object GutterDetector {

    fun detect(bitmap: Bitmap): Float {
        if (bitmap.width < MIN_IMAGE_WIDTH || bitmap.height < 1) return DEFAULT_SPLIT
        val luminance = DoubleArray(bitmap.width)
        val samples = IntArray(bitmap.width)
        val row = IntArray(bitmap.width)
        val startY = (bitmap.height * VERTICAL_MARGIN).toInt()
        val endY = (bitmap.height * (1f - VERTICAL_MARGIN)).toInt().coerceAtLeast(startY + 1)
        val rowStep = max(1, (endY - startY) / TARGET_ROW_SAMPLES)

        var y = startY
        while (y < endY) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (x in row.indices) {
                val color = row[x]
                luminance[x] += 0.2126 * Color.red(color) +
                    0.7152 * Color.green(color) + 0.0722 * Color.blue(color)
                samples[x]++
            }
            y += rowStep
        }
        for (x in luminance.indices) {
            if (samples[x] > 0) luminance[x] /= samples[x]
        }
        return detectFromColumnLuminance(luminance)
    }

    internal fun detectFromColumnLuminance(columns: DoubleArray): Float {
        if (columns.size < MIN_IMAGE_WIDTH) return DEFAULT_SPLIT
        val start = (columns.size * SEARCH_START).toInt()
        val end = (columns.size * SEARCH_END).toInt().coerceAtMost(columns.lastIndex)
        val radius = max(MIN_SMOOTH_RADIUS, columns.size / SMOOTH_DIVISOR)
        var bestIndex = columns.size / 2
        var bestValue = Double.MAX_VALUE
        var searchSum = 0.0
        var searchCount = 0

        for (x in start..end) {
            var sum = 0.0
            var count = 0
            for (sampleX in (x - radius).coerceAtLeast(0)..(x + radius).coerceAtMost(columns.lastIndex)) {
                sum += columns[sampleX]
                count++
            }
            val average = sum / count
            searchSum += average
            searchCount++
            if (average < bestValue) {
                bestValue = average
                bestIndex = x
            }
        }

        val searchAverage = searchSum / searchCount.coerceAtLeast(1)
        if (bestValue > searchAverage * REQUIRED_DARKNESS_RATIO) return DEFAULT_SPLIT
        return bestIndex.toFloat() / columns.size
    }

    private const val DEFAULT_SPLIT = 0.5f
    private const val SEARCH_START = 0.35f
    private const val SEARCH_END = 0.65f
    private const val VERTICAL_MARGIN = 0.08f
    private const val TARGET_ROW_SAMPLES = 240
    private const val MIN_IMAGE_WIDTH = 20
    private const val MIN_SMOOTH_RADIUS = 2
    private const val SMOOTH_DIVISOR = 100
    private const val REQUIRED_DARKNESS_RATIO = 0.94
}
