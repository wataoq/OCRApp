package com.local.bookocr.ui.preprocessing

import android.graphics.Bitmap
import com.local.bookocr.imageprocessor.model.DewarpMesh
import com.local.bookocr.imageprocessor.model.ImageProcessingSettings

data class ImagePreprocessingUiState(
    /** Downsampled original for on-screen editing; never the full-resolution bitmap. */
    val originalBitmap: Bitmap? = null,
    /** Downsampled processed preview, or null until the user requests a preview. */
    val previewBitmap: Bitmap? = null,
    /** When true the preview (corrected) bitmap is shown; otherwise the original with overlays. */
    val showProcessed: Boolean = false,
    val settings: ImageProcessingSettings = ImageProcessingSettings(),
    /** Curved-page dewarp control mesh; null until the user enables dewarp. */
    val dewarpMesh: DewarpMesh? = null,
    /** False when OpenCV is unavailable on this device/build (dewarp UI is then disabled). */
    val isDewarpSupported: Boolean = true,
    val activeTab: PreprocessingTab = PreprocessingTab.CROP,
    val isLoadingPreview: Boolean = false,
    val isSaving: Boolean = false,
    val navigateToEditor: Long? = null,
    val errorMessage: String? = null,
)

enum class PreprocessingTab { CROP, PERSPECTIVE, ENHANCEMENT, DEWARP }
