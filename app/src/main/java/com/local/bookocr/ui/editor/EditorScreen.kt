package com.local.bookocr.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.local.bookocr.imageprocessor.model.ProcessingVariant
import com.local.bookocr.imageprocessor.internal.BitmapDecoding
import com.local.bookocr.ocr.OcrEngineOption
import com.local.bookocr.ui.bookOcrContainer
import com.local.bookocr.ui.components.ZoomableImage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun EditorScreen(onNavigateBack: () -> Unit) {
    val container = LocalContext.current.bookOcrContainer()
    val viewModel: EditorViewModel = viewModel(
        factory = EditorViewModel.factory(container.pageRepository, container.ocrRepository),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.pageDeletedExternally) {
        if (uiState.pageDeletedExternally) onNavigateBack()
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onErrorShown()
        }
    }

    LaunchedEffect(uiState.infoMessage) {
        uiState.infoMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onInfoMessageShown()
        }
    }

    EditorScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onEditedTextChanged = viewModel::onEditedTextChanged,
        onSave = viewModel::onSave,
        onResetToRaw = viewModel::onResetToRaw,
        onRerunOcr = viewModel::onRerunOcr,
        onRetryOcr = viewModel::onRetryOcr,
        onSelectVariant = viewModel::onSelectVariant,
        onSelectEngine = viewModel::onSelectEngine,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScreenContent(
    uiState: EditorUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onEditedTextChanged: (String) -> Unit,
    onSave: () -> Unit,
    onResetToRaw: () -> Unit,
    onRerunOcr: () -> Unit,
    onRetryOcr: () -> Unit,
    onSelectVariant: (ProcessingVariant) -> Unit,
    onSelectEngine: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(uiState.page?.pageNumber?.let { "ページ $it" } ?: "ページ編集")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    if (uiState.hasUnsavedChanges) {
                        Text("未保存", modifier = Modifier.padding(end = 8.dp))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SourceImageSection(imageFile = uiState.imageFile)

            if (uiState.availableVariants.size >= 2) {
                VariantSelector(
                    available = uiState.availableVariants,
                    active = uiState.activeVariant,
                    onSelectVariant = onSelectVariant,
                )
            }

            if (uiState.availableEngines.size >= 2) {
                EngineSelector(
                    available = uiState.availableEngines,
                    activeEngineId = uiState.activeEngineId,
                    onSelectEngine = onSelectEngine,
                )
            }

            when (val phase = uiState.phase) {
                is OcrPhase.Loading -> OcrLoadingSection()
                is OcrPhase.Error -> OcrErrorSection(message = phase.message, onRetry = onRetryOcr)
                is OcrPhase.Ready -> {
                    TranscriptSection(
                        editedText = uiState.editedText,
                        rawText = uiState.rawText,
                        isSaving = uiState.isSaving,
                        hasUnsavedChanges = uiState.hasUnsavedChanges,
                        onEditedTextChanged = onEditedTextChanged,
                        onSave = onSave,
                        onResetToRaw = onResetToRaw,
                        onRerunOcr = onRerunOcr,
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceImageSection(imageFile: File?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        if (imageFile == null) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Card
        }

        var bitmap by remember(imageFile) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(imageFile) {
            bitmap = withContext(Dispatchers.IO) {
                BitmapDecoding.decodeUpright(imageFile, MAX_DISPLAY_DIMENSION_PX)
            }
        }
        val currentBitmap = bitmap
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            when {
                currentBitmap != null -> ZoomableImage(
                    bitmap = currentBitmap.asImageBitmap(),
                    contentDescription = "ページの原稿画像",
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                )
                imageFile.exists() -> Box(Modifier.fillMaxWidth().height(360.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> Column(
                    modifier = Modifier.fillMaxWidth().height(360.dp).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Default.BrokenImage, contentDescription = null)
                    Text("画像が見つかりません")
                }
            }
        }
    }
}

@Composable
private fun VariantSelector(
    available: List<ProcessingVariant>,
    active: ProcessingVariant,
    onSelectVariant: (ProcessingVariant) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("OCR対象の画像", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            available.forEach { variant ->
                FilterChip(
                    selected = variant == active,
                    onClick = { onSelectVariant(variant) },
                    label = { Text(variant.label()) },
                )
            }
        }
    }
}

private fun ProcessingVariant.label(): String = when (this) {
    ProcessingVariant.ORIGINAL -> "元画像"
    ProcessingVariant.PERSPECTIVE -> "遠近補正"
    ProcessingVariant.DEWARPED -> "曲面補正"
}

@Composable
private fun EngineSelector(
    available: List<OcrEngineOption>,
    activeEngineId: String,
    onSelectEngine: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("OCRエンジン", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            available.forEach { engine ->
                FilterChip(
                    selected = engine.id == activeEngineId,
                    onClick = { onSelectEngine(engine.id) },
                    label = { Text(engine.displayName) },
                )
            }
        }
    }
}

@Composable
private fun OcrLoadingSection() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator()
        Text("文字認識中…")
    }
}

@Composable
private fun OcrErrorSection(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("文字認識に失敗しました")
        Text(message)
        Button(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Text("再試行")
        }
    }
}

@Composable
private fun TranscriptSection(
    editedText: String,
    rawText: String,
    isSaving: Boolean,
    hasUnsavedChanges: Boolean,
    onEditedTextChanged: (String) -> Unit,
    onSave: () -> Unit,
    onResetToRaw: () -> Unit,
    onRerunOcr: () -> Unit,
) {
    var showRawText by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("編集テキスト")
        OutlinedTextField(
            value = editedText,
            onValueChange = onEditedTextChanged,
            modifier = Modifier.fillMaxWidth(),
            minLines = 6,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSave, enabled = !isSaving && hasUnsavedChanges) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                } else {
                    Text("保存")
                }
            }
            TextButton(onClick = onResetToRaw, enabled = editedText != rawText) {
                Text("OCR原文に戻す")
            }
            TextButton(onClick = onRerunOcr) {
                Text("再認識")
            }
        }

        TextButton(onClick = { showRawText = !showRawText }) {
            Text(if (showRawText) "OCR原文を隠す" else "OCR原文を表示")
        }

        if (showRawText) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("OCR原文（機械認識結果・変更不可）")
                    Text(rawText.ifBlank { "（文字が検出されませんでした）" })
                }
            }
        }
    }
}

private const val MAX_DISPLAY_DIMENSION_PX = 1600
