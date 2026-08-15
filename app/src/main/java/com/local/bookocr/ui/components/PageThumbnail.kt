package com.local.bookocr.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads a small downsampled preview of a page image off the main thread. Deliberately does
 * not load the full-resolution bitmap - only the OCR editor screen does that, and even there
 * via a sampled decode (see [com.local.bookocr.ocr.MlKitOcrEngine]).
 */
@Composable
fun PageThumbnail(imageFile: File, modifier: Modifier = Modifier, maxDimensionPx: Int = 200) {
    val bitmapState = produceState<Bitmap?>(initialValue = null, imageFile, maxDimensionPx) {
        value = withContext(Dispatchers.IO) { decodeSampledThumbnail(imageFile, maxDimensionPx) }
    }

    Box(modifier = modifier.size(64.dp), contentAlignment = Alignment.Center) {
        val bitmap = bitmapState.value
        when {
            bitmap != null -> Image(bitmap = bitmap.asImageBitmap(), contentDescription = null)
            imageFile.exists() -> CircularProgressIndicator(modifier = Modifier.size(20.dp))
            else -> Icon(Icons.Default.BrokenImage, contentDescription = "画像が見つかりません")
        }
    }
}

private fun decodeSampledThumbnail(file: File, maxDimension: Int): Bitmap? {
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (bounds.outWidth / (sampleSize * 2) >= maxDimension ||
        bounds.outHeight / (sampleSize * 2) >= maxDimension
    ) {
        sampleSize *= 2
    }

    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return BitmapFactory.decodeFile(file.absolutePath, options)
}
