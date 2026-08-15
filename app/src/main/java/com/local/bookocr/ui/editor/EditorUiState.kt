package com.local.bookocr.ui.editor

import com.local.bookocr.data.local.entity.PageEntity
import com.local.bookocr.imageprocessor.model.ProcessingVariant
import com.local.bookocr.ocr.OcrEngineOption
import java.io.File

sealed interface OcrPhase {
    data object Loading : OcrPhase
    data class Error(val message: String) : OcrPhase
    data object Ready : OcrPhase
}

data class EditorUiState(
    val page: PageEntity? = null,
    val imageFile: File? = null,
    val phase: OcrPhase = OcrPhase.Loading,
    val rawText: String = "",
    val editedText: String = "",
    val hasUnsavedChanges: Boolean = false,
    val isSaving: Boolean = false,
    /** Correction variants that exist for this page; a selector shows only these (>=2 to compare). */
    val availableVariants: List<ProcessingVariant> = emptyList(),
    /** The variant OCR currently reads; switching it re-runs OCR for comparison. */
    val activeVariant: ProcessingVariant = ProcessingVariant.ORIGINAL,
    /** OCR implementations available for on-device comparison. */
    val availableEngines: List<OcrEngineOption> = emptyList(),
    /** Engine selected for the next OCR pass and used by the current persisted result. */
    val activeEngineId: String = "",
    val infoMessage: String? = null,
    val errorMessage: String? = null,
    val pageDeletedExternally: Boolean = false,
)
