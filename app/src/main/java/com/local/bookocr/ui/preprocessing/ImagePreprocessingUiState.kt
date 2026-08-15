package com.local.bookocr.ui.preprocessing

import android.graphics.Bitmap
import com.local.bookocr.imageprocessor.model.DewarpMesh
import com.local.bookocr.imageprocessor.model.DocumentLayout
import com.local.bookocr.imageprocessor.model.ImageProcessingSettings
import com.local.bookocr.imageprocessor.model.ReadingDirection
import com.local.bookocr.imageprocessor.model.SpreadPageSide

data class ImagePreprocessingUiState(
    /** Downsampled original for on-screen editing; never the full-resolution bitmap. */
    val originalBitmap: Bitmap? = null,
    /** Downsampled processed preview, or null until the user requests a preview. */
    val previewBitmap: Bitmap? = null,
    /** When true the preview (corrected) bitmap is shown; otherwise the original with overlays. */
    val showProcessed: Boolean = false,
    val settings: ImageProcessingSettings = ImageProcessingSettings(),
    val documentLayout: DocumentLayout = DocumentLayout.SINGLE_PAGE,
    val splitPosition: Float = DEFAULT_SPLIT_POSITION,
    val readingDirection: ReadingDirection = ReadingDirection.RIGHT_TO_LEFT,
    val activeSpreadSide: SpreadPageSide = SpreadPageSide.RIGHT,
    /** Curved-page dewarp control mesh; null until the user enables dewarp. */
    val dewarpMesh: DewarpMesh? = null,
    val rightPageMesh: DewarpMesh = DewarpMesh.forHorizontalRegion(
        DEFAULT_SPLIT_POSITION,
        1f,
        DEFAULT_SPREAD_MESH_POINTS,
    ),
    val leftPageMesh: DewarpMesh = DewarpMesh.forHorizontalRegion(
        0f,
        DEFAULT_SPLIT_POSITION,
        DEFAULT_SPREAD_MESH_POINTS,
    ),
    /** False when OpenCV is unavailable on this device/build (dewarp UI is then disabled). */
    val isDewarpSupported: Boolean = true,
    val activeTab: PreprocessingTab = PreprocessingTab.LAYOUT,
    val isLoadingPreview: Boolean = false,
    val isSaving: Boolean = false,
    val navigateToEditor: Long? = null,
    val errorMessage: String? = null,
)

enum class PreprocessingTab { LAYOUT, CROP, PERSPECTIVE, ENHANCEMENT, DEWARP }

const val DEFAULT_SPLIT_POSITION = 0.5f
const val DEFAULT_SPREAD_MESH_POINTS = 5
