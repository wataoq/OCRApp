package com.local.bookocr.imageprocessor.model

/**
 * Which image a page's OCR reads. The three variants exist side by side so the user can compare
 * OCR accuracy between them (the core reason this feature exists):
 *
 * - [ORIGINAL]    - the untouched photo.
 * - [PERSPECTIVE] - crop + planar perspective + enhancement (see [ImageProcessingSettings]).
 * - [DEWARPED]    - nonlinear curved-page correction (see [DewarpMesh]).
 *
 * Persisted as the enum name on the page row; [com.local.bookocr.data.repository.PageRepository]
 * resolves the active variant to a concrete file (falling back to the original when the selected
 * variant has no generated image).
 */
enum class ProcessingVariant {
    ORIGINAL,
    PERSPECTIVE,
    DEWARPED,
}
