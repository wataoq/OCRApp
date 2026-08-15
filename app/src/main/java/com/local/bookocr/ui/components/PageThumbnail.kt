package com.local.bookocr.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.local.bookocr.imageprocessor.internal.BitmapDecoding
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
    var bitmap by remember(imageFile, maxDimensionPx) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(imageFile, maxDimensionPx) {
        bitmap = withContext(Dispatchers.IO) {
            BitmapDecoding.decodeUpright(imageFile, maxDimensionPx)
        }
    }

    Box(modifier = modifier.size(64.dp), contentAlignment = Alignment.Center) {
        val currentBitmap = bitmap
        when {
            currentBitmap != null -> Image(bitmap = currentBitmap.asImageBitmap(), contentDescription = null)
            imageFile.exists() -> CircularProgressIndicator(modifier = Modifier.size(20.dp))
            else -> Icon(Icons.Default.BrokenImage, contentDescription = "画像が見つかりません")
        }
    }
}
