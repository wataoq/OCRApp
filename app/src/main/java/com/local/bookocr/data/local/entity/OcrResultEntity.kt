package com.local.bookocr.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per [PageEntity] (enforced by the unique index on [pageId]).
 * [rawText] is the untouched machine OCR output and must never be overwritten by edits -
 * see the rawText/editedText invariant in CLAUDE.md. [layoutJson] is an optional
 * engine-independent serialized [com.local.bookocr.ocr.model.OcrDocument] block/line/element
 * layout, kept as a JSON blob so future OCR engines and features do not require a schema
 * migration.
 */
@Entity(
    tableName = "ocr_results",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("pageId", unique = true)],
)
data class OcrResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val pageId: Long,
    val rawText: String,
    val editedText: String,
    val engineId: String,
    val ocrTimestamp: Long,
    val layoutJson: String?,
)
