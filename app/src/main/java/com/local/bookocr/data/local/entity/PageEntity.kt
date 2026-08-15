package com.local.bookocr.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId")],
)
data class PageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val bookId: Long,
    val pageNumber: Int?,
    /** The untouched original photo copied from the Photo Picker. Never overwritten. */
    val storedImagePath: String,
    /**
     * The corrected image generated from [storedImagePath] + [processingSettingsJson].
     * Null when the user applied no corrections; OCR then falls back to [storedImagePath].
     * Regenerable at any time, so image correction is fully non-destructive.
     */
    val processedImagePath: String? = null,
    /** Serialized [com.local.bookocr.imageprocessor.model.ImageProcessingSettings], or null. */
    val processingSettingsJson: String? = null,
    /**
     * [com.local.bookocr.imageprocessor.ImageProcessor.PROCESSING_VERSION] used to produce the
     * current [processedImagePath] (0 when none). Lets a future algorithm change detect and
     * regenerate stale processed images.
     */
    val processingVersion: Int = 0,
    /**
     * The curved-page dewarp variant generated from [storedImagePath] + [dewarpMeshJson] via
     * OpenCV. Null when the user did not dewarp. A sibling of [processedImagePath] (perspective) so
     * the two corrections can be compared for OCR; both regenerable, both non-destructive.
     */
    val dewarpImagePath: String? = null,
    /** Serialized [com.local.bookocr.imageprocessor.model.DewarpMesh], or null. */
    val dewarpMeshJson: String? = null,
    /**
     * [com.local.bookocr.imageprocessor.model.ProcessingVariant] name choosing which image OCR
     * reads. Defaults to PERSPECTIVE (with original fallback) so existing pages behave unchanged.
     */
    val activeVariant: String = "PERSPECTIVE",
    val createdAt: Long,
    val updatedAt: Long,
)
