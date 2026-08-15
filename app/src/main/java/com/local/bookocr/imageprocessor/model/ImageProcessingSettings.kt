package com.local.bookocr.imageprocessor.model

import kotlinx.serialization.Serializable

/**
 * Declarative, engine-independent description of every correction applied to a page's original
 * photo. This is the *only* state needed to regenerate the processed image from the untouched
 * original - persisting it (see [com.local.bookocr.data.local.entity.PageEntity]) is what makes
 * image correction non-destructive: original + settings -> processed image, always reproducible.
 *
 * The pipeline order is fixed and matches how a human would prepare a scan:
 * orientation normalization (implicit, always applied from EXIF) -> rotation -> crop ->
 * perspective correction -> enhancement.
 */
@Serializable
data class ImageProcessingSettings(
    val rotationDegrees: Int = 0,
    val cropRect: NormalizedRect? = null,
    val perspectivePoints: PerspectiveQuad? = null,
    val enhancementMode: EnhancementMode = EnhancementMode.ORIGINAL,
) {
    /**
     * True when no user correction has been requested. EXIF orientation normalization is applied
     * separately at load time and is intentionally *not* part of this check - an identity settings
     * value means "no processed image is needed, OCR can use the original as-is".
     */
    val isIdentity: Boolean
        get() = rotationDegrees == 0 &&
            cropRect == null &&
            perspectivePoints == null &&
            enhancementMode == EnhancementMode.ORIGINAL
}

/** Crop rectangle in normalized image coordinates, each component in [0.0, 1.0]. */
@Serializable
data class NormalizedRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
)

/**
 * Four-point perspective quad in normalized image coordinates, each component in [0.0, 1.0].
 * Points are the page corners the user marks; the image processor maps this quad onto an
 * axis-aligned rectangle (see [com.local.bookocr.imageprocessor.ImageProcessor]).
 */
@Serializable
data class PerspectiveQuad(
    val topLeftX: Float = 0f,
    val topLeftY: Float = 0f,
    val topRightX: Float = 1f,
    val topRightY: Float = 0f,
    val bottomRightX: Float = 1f,
    val bottomRightY: Float = 1f,
    val bottomLeftX: Float = 0f,
    val bottomLeftY: Float = 1f,
)

/**
 * Enhancement presets. Deliberately conservative: [ORIGINAL] is the safe default and no
 * destructive binarization is offered, because aggressive thresholding erases thin Japanese
 * strokes and ruby - the opposite of this feature's goal (improving OCR accuracy, not looks).
 */
@Serializable
enum class EnhancementMode { ORIGINAL, GRAYSCALE, HIGH_CONTRAST }
