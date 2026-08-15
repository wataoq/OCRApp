package com.local.bookocr.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.local.bookocr.data.repository.OcrRepository
import com.local.bookocr.data.repository.OcrRerunOutcome
import com.local.bookocr.data.repository.PageRepository
import com.local.bookocr.imageprocessor.model.ProcessingVariant
import com.local.bookocr.ui.navigation.BookOcrDestinations
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the OCR editor's rawText/editedText invariant: editedText is seeded from persistence
 * exactly once per page load (or from an in-memory draft after process death), then only ever
 * changed through explicit user actions or an explicit rerun outcome - never silently
 * overwritten by a reactive DB emission. See CLAUDE.md for the invariant this protects.
 */
class EditorViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val pageRepository: PageRepository,
    private val ocrRepository: OcrRepository,
) : ViewModel() {

    private val pageId: Long = checkNotNull(savedStateHandle[BookOcrDestinations.PAGE_ID_ARG])

    private val _uiState = MutableStateFlow(
        EditorUiState(
            editedText = savedStateHandle[DRAFT_KEY] ?: "",
            hasUnsavedChanges = savedStateHandle.get<String>(DRAFT_KEY) != null,
            availableEngines = ocrRepository.availableEngines,
            activeEngineId = ocrRepository.defaultEngineId,
        ),
    )
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var hasInitializedText = savedStateHandle.get<String>(DRAFT_KEY) != null
    private var hasStartedOcrForThisLoad = false
    private var hasLoadedPageOnce = false
    private var isRunningOcr = false

    init {
        viewModelScope.launch {
            combine(
                pageRepository.observePage(pageId),
                ocrRepository.observeResult(pageId),
            ) { page, result -> page to result }.collect { (page, result) ->
                if (page == null) {
                    if (hasLoadedPageOnce) _uiState.update { it.copy(pageDeletedExternally = true) }
                    return@collect
                }
                hasLoadedPageOnce = true
                val imageFile = pageRepository.resolveImageFile(page)
                val available = pageRepository.availableVariants(page)
                val stored = runCatching { ProcessingVariant.valueOf(page.activeVariant) }
                    .getOrDefault(ProcessingVariant.PERSPECTIVE)
                val effectiveVariant = if (stored in available) stored else ProcessingVariant.ORIGINAL

                _uiState.update { current ->
                    current.copy(
                        page = page,
                        imageFile = imageFile,
                        availableVariants = available,
                        activeVariant = effectiveVariant,
                        activeEngineId = result?.engineId
                            ?.takeIf { id -> ocrRepository.availableEngines.any { it.id == id } }
                            ?: current.activeEngineId,
                        rawText = result?.rawText ?: current.rawText,
                        editedText = if (result != null && !hasInitializedText) result.editedText else current.editedText,
                        phase = when {
                            result != null -> OcrPhase.Ready
                            current.phase is OcrPhase.Error -> current.phase
                            else -> OcrPhase.Loading
                        },
                    )
                }
                if (result != null) hasInitializedText = true

                if (result == null && !hasStartedOcrForThisLoad) {
                    hasStartedOcrForThisLoad = true
                    runInitialOcr(imageFile, _uiState.value.activeEngineId)
                }
            }
        }
    }

    private fun runInitialOcr(imageFile: File, engineId: String) {
        if (isRunningOcr) return
        isRunningOcr = true
        _uiState.update { it.copy(phase = OcrPhase.Loading) }
        viewModelScope.launch {
            val result = ocrRepository.runInitialOcr(pageId, imageFile, engineId)
            isRunningOcr = false
            result.onFailure { error ->
                _uiState.update { it.copy(phase = OcrPhase.Error(error.message ?: "文字認識に失敗しました")) }
            }
        }
    }

    fun onRetryOcr() {
        val imageFile = _uiState.value.imageFile ?: return
        hasStartedOcrForThisLoad = true
        runInitialOcr(imageFile, _uiState.value.activeEngineId)
    }

    fun onRerunOcr() {
        val imageFile = _uiState.value.imageFile ?: return
        rerunOcrOn(imageFile, _uiState.value.activeEngineId)
    }

    /**
     * Switches which correction variant OCR reads and re-runs OCR on it, so the user can compare
     * OCR output across Original / Perspective / Dewarped. The rawText/editedText invariant is
     * unchanged: the switch goes through [OcrRepository.rerunOcr], which preserves user edits.
     */
    fun onSelectVariant(variant: ProcessingVariant) {
        if (variant == _uiState.value.activeVariant || isRunningOcr) return
        viewModelScope.launch {
            pageRepository.setActiveVariant(pageId, variant)
            val page = pageRepository.getPageOnce(pageId) ?: return@launch
            val imageFile = pageRepository.resolveImageFile(page)
            _uiState.update { it.copy(imageFile = imageFile, activeVariant = variant) }
            rerunOcrOn(imageFile, _uiState.value.activeEngineId)
        }
    }

    fun onSelectEngine(engineId: String) {
        val state = _uiState.value
        if (engineId == state.activeEngineId || isRunningOcr) return
        if (state.availableEngines.none { it.id == engineId }) return
        val imageFile = state.imageFile ?: return
        _uiState.update { it.copy(activeEngineId = engineId) }
        rerunOcrOn(imageFile, engineId, restoreEngineIdOnFailure = state.activeEngineId)
    }

    private fun rerunOcrOn(
        imageFile: File,
        engineId: String,
        restoreEngineIdOnFailure: String = engineId,
    ) {
        if (isRunningOcr) return
        isRunningOcr = true
        _uiState.update { it.copy(phase = OcrPhase.Loading) }
        viewModelScope.launch {
            val outcome = ocrRepository.rerunOcr(pageId, imageFile, engineId)
            isRunningOcr = false
            outcome.onSuccess { result ->
                if (result == OcrRerunOutcome.EditsReplaced) {
                    ocrRepository.getResultOnce(pageId)?.let { fresh ->
                        savedStateHandle.remove<String>(DRAFT_KEY)
                        _uiState.update { it.copy(editedText = fresh.editedText, hasUnsavedChanges = false) }
                    }
                }
                val message = when (result) {
                    OcrRerunOutcome.EditsPreserved -> "編集済みのテキストは保持されました（OCR原文のみ更新）"
                    OcrRerunOutcome.EditsReplaced -> "OCR結果を更新しました"
                }
                _uiState.update { it.copy(phase = OcrPhase.Ready, infoMessage = message) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        phase = OcrPhase.Ready,
                        activeEngineId = restoreEngineIdOnFailure,
                        errorMessage = error.message ?: "文字認識に失敗しました",
                    )
                }
            }
        }
    }

    fun onEditedTextChanged(text: String) {
        savedStateHandle[DRAFT_KEY] = text
        _uiState.update { it.copy(editedText = text, hasUnsavedChanges = true) }
    }

    fun onSave() {
        if (_uiState.value.isSaving) return
        val text = _uiState.value.editedText
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            ocrRepository.saveEditedText(pageId, text)
                .onSuccess {
                    savedStateHandle.remove<String>(DRAFT_KEY)
                    _uiState.update { it.copy(isSaving = false, hasUnsavedChanges = false) }
                }
                .onFailure {
                    _uiState.update { it.copy(isSaving = false, errorMessage = "保存に失敗しました") }
                }
        }
    }

    fun onResetToRaw() {
        viewModelScope.launch {
            ocrRepository.resetToRaw(pageId)
                .onSuccess {
                    val raw = _uiState.value.rawText
                    savedStateHandle.remove<String>(DRAFT_KEY)
                    _uiState.update { it.copy(editedText = raw, hasUnsavedChanges = false) }
                }
                .onFailure { _uiState.update { it.copy(errorMessage = "リセットに失敗しました") } }
        }
    }

    fun onErrorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun onInfoMessageShown() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    companion object {
        private const val DRAFT_KEY = "draft_edited_text"

        fun factory(pageRepository: PageRepository, ocrRepository: OcrRepository) = viewModelFactory {
            initializer { EditorViewModel(createSavedStateHandle(), pageRepository, ocrRepository) }
        }
    }
}
