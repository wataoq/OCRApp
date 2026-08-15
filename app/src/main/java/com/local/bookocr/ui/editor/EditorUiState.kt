package com.local.bookocr.ui.editor

import com.local.bookocr.data.local.entity.PageEntity
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
    val infoMessage: String? = null,
    val errorMessage: String? = null,
    val pageDeletedExternally: Boolean = false,
)
