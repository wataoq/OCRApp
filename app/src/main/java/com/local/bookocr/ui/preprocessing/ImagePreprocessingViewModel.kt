package com.local.bookocr.ui.preprocessing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.local.bookocr.data.repository.PageRepository
import com.local.bookocr.data.repository.PreparedPagePaths
import com.local.bookocr.imageprocessor.ImageProcessor
import com.local.bookocr.imageprocessor.GutterDetector
import com.local.bookocr.imageprocessor.PageDewarper
import com.local.bookocr.imageprocessor.model.DewarpMesh
import com.local.bookocr.imageprocessor.model.DocumentLayout
import com.local.bookocr.imageprocessor.model.EnhancementMode
import com.local.bookocr.imageprocessor.model.ImageProcessingSettings
import com.local.bookocr.imageprocessor.model.NormalizedRect
import com.local.bookocr.imageprocessor.model.PerspectiveQuad
import com.local.bookocr.imageprocessor.model.ProcessingVariant
import com.local.bookocr.imageprocessor.model.ReadingDirection
import com.local.bookocr.imageprocessor.model.SpreadPageSide
import com.local.bookocr.imageprocessor.model.orderedSides
import com.local.bookocr.storage.ImageStorage
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Drives the non-destructive image-correction screen. It imports the original once, lets the user
 * assemble a perspective correction ([ImageProcessingSettings]) and/or a curved-page dewarp
 * ([DewarpMesh]) against a downsampled preview, and on confirm generates the full-resolution
 * variant image(s) and persists a page row recording every path + the settings/mesh.
 *
 * The original photo is imported exactly once and never overwritten; if a correction fails to
 * generate, the page is still created (falling back to whatever variants succeeded, or the
 * original), so the user never loses their scan.
 */
class ImagePreprocessingViewModel(
    private val bookId: Long,
    private val sourceUri: Uri,
    private val applicationContext: Context,
    private val pageRepository: PageRepository,
    private val imageStorage: ImageStorage,
    private val imageProcessor: ImageProcessor,
    private val pageDewarper: PageDewarper,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(
        ImagePreprocessingUiState(isDewarpSupported = pageDewarper.isAvailable()),
    )
    val uiState: StateFlow<ImagePreprocessingUiState> = _uiState.asStateFlow()

    init {
        loadOriginalBitmap()
    }

    private fun loadOriginalBitmap() {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) { loadUprightSampled(sourceUri, EDIT_MAX_DIMENSION) }
            if (bitmap == null) {
                _uiState.update { it.copy(errorMessage = "画像を読み込めませんでした") }
            } else {
                _uiState.update { it.copy(originalBitmap = bitmap) }
                if (_uiState.value.documentLayout == DocumentLayout.TWO_PAGE_SPREAD) {
                    detectGutter(bitmap)
                }
            }
        }
    }

    fun onTabSelected(tab: PreprocessingTab) = _uiState.update { state ->
        val unavailableForSpread = tab == PreprocessingTab.CROP ||
            tab == PreprocessingTab.PERSPECTIVE || tab == PreprocessingTab.ENHANCEMENT
        if (state.documentLayout == DocumentLayout.TWO_PAGE_SPREAD && unavailableForSpread) state
        else state.copy(activeTab = tab, previewBitmap = null, showProcessed = false)
    }

    fun onDocumentLayoutChanged(layout: DocumentLayout) {
        val originalBitmap = _uiState.value.originalBitmap
        _uiState.update { state -> state.copy(
            documentLayout = layout,
            activeTab = PreprocessingTab.LAYOUT,
            previewBitmap = null,
            showProcessed = false,
        ) }
        if (layout == DocumentLayout.TWO_PAGE_SPREAD && originalBitmap != null) {
            detectGutter(originalBitmap)
        }
    }

    private fun detectGutter(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.Default) {
            val detected = GutterDetector.detect(bitmap)
            _uiState.update { state ->
                if (state.documentLayout == DocumentLayout.TWO_PAGE_SPREAD &&
                    state.splitPosition == DEFAULT_SPLIT_POSITION
                ) {
                    state.withSplitPosition(detected)
                } else {
                    state
                }
            }
        }
    }

    fun onReadingDirectionChanged(direction: ReadingDirection) = _uiState.update {
        it.copy(readingDirection = direction)
    }

    fun onActiveSpreadSideChanged(side: SpreadPageSide) = _uiState.update {
        it.copy(activeSpreadSide = side, previewBitmap = null, showProcessed = false)
    }

    fun onSplitPositionChanged(position: Float) = _uiState.update { state ->
        state.withSplitPosition(position)
    }

    private fun ImagePreprocessingUiState.withSplitPosition(position: Float): ImagePreprocessingUiState {
        val split = position.coerceIn(MIN_SPLIT_POSITION, MAX_SPLIT_POSITION)
        return copy(
            splitPosition = split,
            rightPageMesh = DewarpMesh.forHorizontalRegion(split, 1f, DEFAULT_SPREAD_MESH_POINTS),
            leftPageMesh = DewarpMesh.forHorizontalRegion(0f, split, DEFAULT_SPREAD_MESH_POINTS),
            previewBitmap = null,
            showProcessed = false,
        )
    }

    fun onCropChanged(rect: NormalizedRect) = _uiState.update {
        it.copy(settings = it.settings.copy(cropRect = rect), showProcessed = false)
    }

    fun onPerspectiveChanged(quad: PerspectiveQuad) = _uiState.update {
        it.copy(settings = it.settings.copy(perspectivePoints = quad), showProcessed = false)
    }

    fun onEnhancementChanged(mode: EnhancementMode) = _uiState.update {
        it.copy(settings = it.settings.copy(enhancementMode = mode), showProcessed = false)
    }

    /** Enables the dewarp mesh with a default straight grid the user then drags to the page edges. */
    fun onEnableDewarp() = _uiState.update {
        it.copy(dewarpMesh = it.dewarpMesh ?: DewarpMesh.default(DEFAULT_MESH_POINTS), showProcessed = false)
    }

    fun onDewarpMeshChanged(mesh: DewarpMesh) = _uiState.update { state ->
        when (state.documentLayout) {
            DocumentLayout.SINGLE_PAGE -> state.copy(dewarpMesh = mesh, showProcessed = false)
            DocumentLayout.TWO_PAGE_SPREAD -> when (state.activeSpreadSide) {
                SpreadPageSide.RIGHT -> state.copy(rightPageMesh = mesh, showProcessed = false)
                SpreadPageSide.LEFT -> state.copy(leftPageMesh = mesh, showProcessed = false)
            }
        }
    }

    fun onResetSettings() = _uiState.update {
        it.copy(
            settings = ImageProcessingSettings(),
            dewarpMesh = null,
            documentLayout = DocumentLayout.SINGLE_PAGE,
            splitPosition = DEFAULT_SPLIT_POSITION,
            readingDirection = ReadingDirection.RIGHT_TO_LEFT,
            activeSpreadSide = SpreadPageSide.RIGHT,
            rightPageMesh = DewarpMesh.forHorizontalRegion(
                DEFAULT_SPLIT_POSITION,
                1f,
                DEFAULT_SPREAD_MESH_POINTS,
            ),
            leftPageMesh = DewarpMesh.forHorizontalRegion(
                0f,
                DEFAULT_SPLIT_POSITION,
                DEFAULT_SPREAD_MESH_POINTS,
            ),
            activeTab = PreprocessingTab.LAYOUT,
            previewBitmap = null,
            showProcessed = false,
        )
    }

    /**
     * Renders a downsampled preview. On the dewarp tab it uses the OpenCV dewarper against the mesh;
     * otherwise it applies the perspective settings.
     */
    fun onUpdatePreview() {
        val state = _uiState.value
        val useDewarp = state.activeTab == PreprocessingTab.DEWARP
        val previewMesh = state.activeDewarpMesh()
        if (useDewarp) {
            val mesh = previewMesh
            if (mesh == null || !mesh.isValid) {
                _uiState.update { it.copy(previewBitmap = null, showProcessed = false) }
                return
            }
        } else if (state.settings.isIdentity) {
            _uiState.update { it.copy(previewBitmap = null, showProcessed = false) }
            return
        }

        _uiState.update { it.copy(isLoadingPreview = true) }
        viewModelScope.launch {
            val previewInput = stageOriginalForProcessing()
            if (previewInput == null) {
                _uiState.update { it.copy(isLoadingPreview = false, errorMessage = "プレビューの生成に失敗しました") }
                return@launch
            }
            val result = if (useDewarp) {
                pageDewarper.dewarpForPreview(previewInput, previewMesh!!, PREVIEW_MAX_DIMENSION)
            } else {
                imageProcessor.processForPreview(previewInput, state.settings, PREVIEW_MAX_DIMENSION)
            }
            result
                .onSuccess { preview ->
                    _uiState.update { it.copy(previewBitmap = preview, showProcessed = true, isLoadingPreview = false) }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoadingPreview = false, errorMessage = "プレビューの生成に失敗しました") }
                }
        }
    }

    fun onTogglePreview() = _uiState.update {
        it.copy(showProcessed = !it.showProcessed && it.previewBitmap != null)
    }

    fun onConfirm() {
        val state = _uiState.value
        if (state.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (state.documentLayout) {
                DocumentLayout.SINGLE_PAGE -> confirmSinglePage(state)
                DocumentLayout.TWO_PAGE_SPREAD -> confirmTwoPageSpread(state)
            }
        }
    }

    private suspend fun confirmSinglePage(state: ImagePreprocessingUiState) {
        val settings = state.settings
        val mesh = state.dewarpMesh
        val originalPath = imageStorage.importImage(sourceUri).getOrElse {
            failSave("画像の読み込みに失敗しました")
            return
        }
        val originalFile = imageStorage.resolveFile(originalPath)

        var processedPath: String? = null
        if (!settings.isIdentity) {
            val processedFile = imageStorage.allocateProcessedFile()
            imageProcessor.process(originalFile, settings, processedFile)
                .onSuccess { processedPath = processedFile.name }
        }

        var dewarpPath: String? = null
        if (mesh != null && mesh.isValid && pageDewarper.isAvailable()) {
            val dewarpFile = imageStorage.allocateDewarpFile()
            pageDewarper.dewarp(originalFile, mesh, dewarpFile)
                .onSuccess { dewarpPath = dewarpFile.name }
        }

        val activeVariant = when {
            dewarpPath != null -> ProcessingVariant.DEWARPED
            processedPath != null -> ProcessingVariant.PERSPECTIVE
            else -> ProcessingVariant.ORIGINAL
        }
        val settingsJson = if (processedPath != null) json.encodeToString(settings) else null
        val version = if (processedPath != null) ImageProcessor.PROCESSING_VERSION else 0
        val meshJson = if (dewarpPath != null) json.encodeToString(mesh) else null

        pageRepository.importPageWithPaths(
            bookId = bookId,
            storedImagePath = originalPath,
            processedImagePath = processedPath,
            processingSettingsJson = settingsJson,
            processingVersion = version,
            pageNumber = null,
            dewarpImagePath = dewarpPath,
            dewarpMeshJson = meshJson,
            activeVariant = activeVariant,
        ).onSuccess { pageId ->
            _uiState.update { it.copy(isSaving = false, navigateToEditor = pageId) }
        }.onFailure {
            imageStorage.delete(originalPath)
            processedPath?.let { path -> imageStorage.deleteProcessed(path) }
            dewarpPath?.let { path -> imageStorage.deleteDewarp(path) }
            failSave("ページの作成に失敗しました")
        }
    }

    /**
     * Treats a spread as two independent physical surfaces. Each page gets its own copy of the
     * untouched capture and its own flattened output, so deleting or reprocessing one page cannot
     * invalidate the other. The supplied reading direction determines insertion/display order.
     */
    private suspend fun confirmTwoPageSpread(state: ImagePreprocessingUiState) {
        if (!pageDewarper.isAvailable()) {
            failSave("見開きの分割には曲面補正を利用できる端末が必要です")
            return
        }

        val storedPaths = mutableListOf<String>()
        val flatPaths = mutableListOf<String>()
        val preparedPages = mutableListOf<PreparedPagePaths>()

        for (side in state.readingDirection.orderedSides()) {
            val originalPath = imageStorage.importImage(sourceUri).getOrElse {
                cleanupPreparedSpread(storedPaths, flatPaths)
                failSave("見開き画像の読み込みに失敗しました")
                return
            }
            storedPaths += originalPath
            val mesh = state.meshFor(side)
            val flatFile = imageStorage.allocateDewarpFile()
            pageDewarper.dewarp(imageStorage.resolveFile(originalPath), mesh, flatFile).getOrElse {
                cleanupPreparedSpread(storedPaths, flatPaths)
                imageStorage.deleteDewarp(flatFile.name)
                failSave("${side.label()}ページの平坦化に失敗しました")
                return
            }
            flatPaths += flatFile.name
            preparedPages += PreparedPagePaths(
                storedImagePath = originalPath,
                dewarpImagePath = flatFile.name,
                dewarpMeshJson = json.encodeToString(mesh),
                activeVariant = ProcessingVariant.DEWARPED,
            )
        }

        pageRepository.importPreparedPages(bookId, preparedPages)
            .onSuccess { pageIds ->
                _uiState.update { it.copy(isSaving = false, navigateToEditor = pageIds.first()) }
            }
            .onFailure {
                cleanupPreparedSpread(storedPaths, flatPaths)
                failSave("見開きページの作成に失敗しました")
            }
    }

    private suspend fun cleanupPreparedSpread(storedPaths: List<String>, flatPaths: List<String>) {
        storedPaths.distinct().forEach { imageStorage.delete(it) }
        flatPaths.distinct().forEach { imageStorage.deleteDewarp(it) }
    }

    private fun failSave(message: String) {
        _uiState.update { it.copy(isSaving = false, errorMessage = message) }
    }

    private fun ImagePreprocessingUiState.activeDewarpMesh(): DewarpMesh? = when (documentLayout) {
        DocumentLayout.SINGLE_PAGE -> dewarpMesh
        DocumentLayout.TWO_PAGE_SPREAD -> meshFor(activeSpreadSide)
    }

    private fun ImagePreprocessingUiState.meshFor(side: SpreadPageSide): DewarpMesh = when (side) {
        SpreadPageSide.RIGHT -> rightPageMesh
        SpreadPageSide.LEFT -> leftPageMesh
    }

    private fun SpreadPageSide.label(): String = when (this) {
        SpreadPageSide.RIGHT -> "右"
        SpreadPageSide.LEFT -> "左"
    }

    fun onErrorShown() = _uiState.update { it.copy(errorMessage = null) }

    /**
     * Writes the in-memory (already upright, downsampled) original to a cache file so the processor
     * / dewarper can read it for preview. The bitmap is EXIF-corrected already and re-encoded with
     * no orientation tag, so downstream EXIF handling is a no-op (no double rotation).
     */
    private suspend fun stageOriginalForProcessing(): File? = withContext(Dispatchers.IO) {
        val bitmap = _uiState.value.originalBitmap ?: return@withContext null
        runCatching {
            val file = File(applicationContext.cacheDir, PREVIEW_INPUT_FILENAME)
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, PREVIEW_JPEG_QUALITY, it) }
            file
        }.getOrNull()
    }

    /** Decodes the picked image downsampled AND rotated upright per its EXIF orientation. */
    private fun loadUprightSampled(uri: Uri, maxDimension: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        applicationContext.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= maxDimension ||
            bounds.outHeight / (sampleSize * 2) >= maxDimension
        ) {
            sampleSize *= 2
        }
        val decoded = applicationContext.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        } ?: return@runCatching null

        val orientation = applicationContext.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        applyOrientation(decoded, orientation)
    }.getOrNull()

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(-90f); matrix.postScale(-1f, 1f) }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            .also { if (it !== bitmap) bitmap.recycle() }
    }

    companion object {
        private const val EDIT_MAX_DIMENSION = 1200
        private const val PREVIEW_MAX_DIMENSION = 900
        private const val PREVIEW_JPEG_QUALITY = 90
        private const val PREVIEW_INPUT_FILENAME = "preprocess_preview_input.jpg"
        private const val DEFAULT_MESH_POINTS = 3
        private const val MIN_SPLIT_POSITION = 0.25f
        private const val MAX_SPLIT_POSITION = 0.75f

        fun factory(
            bookId: Long,
            sourceUri: Uri,
            appContext: Context,
            pageRepository: PageRepository,
            imageStorage: ImageStorage,
            imageProcessor: ImageProcessor,
            pageDewarper: PageDewarper,
        ) = viewModelFactory {
            initializer {
                ImagePreprocessingViewModel(
                    bookId = bookId,
                    sourceUri = sourceUri,
                    applicationContext = appContext,
                    pageRepository = pageRepository,
                    imageStorage = imageStorage,
                    imageProcessor = imageProcessor,
                    pageDewarper = pageDewarper,
                )
            }
        }
    }
}
