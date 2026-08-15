package com.local.bookocr.ui.preprocessing

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.local.bookocr.imageprocessor.model.DewarpMesh
import com.local.bookocr.imageprocessor.model.EnhancementMode
import com.local.bookocr.imageprocessor.model.MeshPoint
import com.local.bookocr.imageprocessor.model.NormalizedRect
import com.local.bookocr.imageprocessor.model.PerspectiveQuad
import com.local.bookocr.ui.bookOcrContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagePreprocessingScreen(
    bookId: Long,
    encodedSourceUri: String,
    onPageCreated: (Long) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val container = context.bookOcrContainer()
    val sourceUri = remember(encodedSourceUri) { Uri.parse(Uri.decode(encodedSourceUri)) }
    val viewModel: ImagePreprocessingViewModel = viewModel(
        factory = ImagePreprocessingViewModel.factory(
            bookId = bookId,
            sourceUri = sourceUri,
            appContext = context.applicationContext,
            pageRepository = container.pageRepository,
            imageStorage = container.imageStorage,
            imageProcessor = container.imageProcessor,
            pageDewarper = container.pageDewarper,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.navigateToEditor) {
        uiState.navigateToEditor?.let(onPageCreated)
    }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onErrorShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("画像補正") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PreviewArea(
                modifier = Modifier.fillMaxWidth().weight(1f),
                uiState = uiState,
                onCropChanged = viewModel::onCropChanged,
                onPerspectiveChanged = viewModel::onPerspectiveChanged,
                onDewarpMeshChanged = viewModel::onDewarpMeshChanged,
                onTogglePreview = viewModel::onTogglePreview,
            )

            ScrollableTabRow(selectedTabIndex = uiState.activeTab.ordinal, edgePadding = 0.dp) {
                Tab(
                    selected = uiState.activeTab == PreprocessingTab.CROP,
                    onClick = { viewModel.onTabSelected(PreprocessingTab.CROP) },
                    text = { Text("トリミング") },
                )
                Tab(
                    selected = uiState.activeTab == PreprocessingTab.PERSPECTIVE,
                    onClick = { viewModel.onTabSelected(PreprocessingTab.PERSPECTIVE) },
                    text = { Text("遠近補正") },
                )
                Tab(
                    selected = uiState.activeTab == PreprocessingTab.ENHANCEMENT,
                    onClick = { viewModel.onTabSelected(PreprocessingTab.ENHANCEMENT) },
                    text = { Text("補正モード") },
                )
                Tab(
                    selected = uiState.activeTab == PreprocessingTab.DEWARP,
                    enabled = uiState.isDewarpSupported,
                    onClick = { viewModel.onTabSelected(PreprocessingTab.DEWARP) },
                    text = { Text("曲面補正") },
                )
            }

            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                when (uiState.activeTab) {
                    PreprocessingTab.CROP ->
                        Text("画像の角のハンドルをドラッグしてトリミング範囲を調整してください", style = MaterialTheme.typography.bodySmall)
                    PreprocessingTab.PERSPECTIVE ->
                        Text("4つのコーナーをページの角に合わせてドラッグしてください", style = MaterialTheme.typography.bodySmall)
                    PreprocessingTab.ENHANCEMENT ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EnhancementMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = uiState.settings.enhancementMode == mode,
                                    onClick = { viewModel.onEnhancementChanged(mode) },
                                    label = { Text(mode.label()) },
                                )
                            }
                        }
                    PreprocessingTab.DEWARP ->
                        if (uiState.dewarpMesh == null) {
                            Column {
                                Text(
                                    "本のノド側で曲がったページを補正します。上下の点を文字行の端に合わせます。",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                TextButton(onClick = viewModel::onEnableDewarp, enabled = uiState.isDewarpSupported) {
                                    Text("曲面補正を有効にする")
                                }
                            }
                        } else {
                            Text(
                                "上端・下端の点をページの文字行の曲がりに合わせてドラッグし、「プレビュー」で確認してください。",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = viewModel::onResetSettings, modifier = Modifier.weight(1f)) {
                    Text("リセット")
                }
                OutlinedButton(
                    onClick = viewModel::onUpdatePreview,
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isLoadingPreview && uiState.originalBitmap != null,
                ) {
                    Text("プレビュー")
                }
                Button(
                    onClick = viewModel::onConfirm,
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isSaving && uiState.originalBitmap != null,
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("OCRへ進む")
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewArea(
    modifier: Modifier,
    uiState: ImagePreprocessingUiState,
    onCropChanged: (NormalizedRect) -> Unit,
    onPerspectiveChanged: (PerspectiveQuad) -> Unit,
    onDewarpMeshChanged: (DewarpMesh) -> Unit,
    onTogglePreview: () -> Unit,
) {
    val displayBitmap = if (uiState.showProcessed && uiState.previewBitmap != null) {
        uiState.previewBitmap
    } else {
        uiState.originalBitmap
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (displayBitmap == null) {
            CircularProgressIndicator()
            return@Box
        }

        var containerSize by remember { mutableStateOf(IntSize.Zero) }
        Box(modifier = Modifier.fillMaxSize().onSizeChanged { containerSize = it }) {
            Image(
                bitmap = displayBitmap.asImageBitmap(),
                contentDescription = "ページ画像",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            if (!uiState.showProcessed && containerSize != IntSize.Zero) {
                when (uiState.activeTab) {
                    PreprocessingTab.CROP -> CropOverlay(
                        modifier = Modifier.fillMaxSize(),
                        bitmap = displayBitmap,
                        containerSize = containerSize,
                        cropRect = uiState.settings.cropRect ?: NormalizedRect(),
                        onCropChanged = onCropChanged,
                    )
                    PreprocessingTab.PERSPECTIVE -> PerspectiveOverlay(
                        modifier = Modifier.fillMaxSize(),
                        bitmap = displayBitmap,
                        containerSize = containerSize,
                        quad = uiState.settings.perspectivePoints ?: PerspectiveQuad(),
                        onQuadChanged = onPerspectiveChanged,
                    )
                    PreprocessingTab.DEWARP -> uiState.dewarpMesh?.let { mesh ->
                        DewarpOverlay(
                            modifier = Modifier.fillMaxSize(),
                            bitmap = displayBitmap,
                            containerSize = containerSize,
                            mesh = mesh,
                            onMeshChanged = onDewarpMeshChanged,
                        )
                    }
                    PreprocessingTab.ENHANCEMENT -> Unit
                }
            }
        }

        if (uiState.previewBitmap != null) {
            FilledTonalButton(
                onClick = onTogglePreview,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            ) {
                Text(if (uiState.showProcessed) "元画像を表示" else "補正後を表示")
            }
        }
        if (uiState.isLoadingPreview) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun CropOverlay(
    modifier: Modifier,
    bitmap: Bitmap,
    containerSize: IntSize,
    cropRect: NormalizedRect,
    onCropChanged: (NormalizedRect) -> Unit,
) {
    val imageRect = remember(bitmap.width, bitmap.height, containerSize) {
        computeFitRect(bitmap.width, bitmap.height, containerSize.width, containerSize.height)
    }
    // Local working copy in normalized coords; pushed to the VM on drag end to avoid feedback loops.
    var rect by remember(imageRect, cropRect) { mutableStateOf(cropRect) }
    var dragCorner by remember { mutableStateOf(-1) }

    fun corners(r: NormalizedRect): List<Offset> = listOf(
        Offset(imageRect.left + r.left * imageRect.width, imageRect.top + r.top * imageRect.height),
        Offset(imageRect.left + r.right * imageRect.width, imageRect.top + r.top * imageRect.height),
        Offset(imageRect.left + r.right * imageRect.width, imageRect.top + r.bottom * imageRect.height),
        Offset(imageRect.left + r.left * imageRect.width, imageRect.top + r.bottom * imageRect.height),
    )

    Canvas(
        modifier = modifier.pointerInput(imageRect) {
            detectDragGestures(
                onDragStart = { pos ->
                    dragCorner = corners(rect).indexOfFirst { (it - pos).getDistance() < HANDLE_TOUCH_PX }
                },
                onDrag = { change, _ ->
                    if (dragCorner < 0) return@detectDragGestures
                    change.consume()
                    val nx = ((change.position.x - imageRect.left) / imageRect.width).coerceIn(0f, 1f)
                    val ny = ((change.position.y - imageRect.top) / imageRect.height).coerceIn(0f, 1f)
                    rect = when (dragCorner) {
                        0 -> rect.copy(left = nx.coerceAtMost(rect.right), top = ny.coerceAtMost(rect.bottom))
                        1 -> rect.copy(right = nx.coerceAtLeast(rect.left), top = ny.coerceAtMost(rect.bottom))
                        2 -> rect.copy(right = nx.coerceAtLeast(rect.left), bottom = ny.coerceAtLeast(rect.top))
                        else -> rect.copy(left = nx.coerceAtMost(rect.right), bottom = ny.coerceAtLeast(rect.top))
                    }
                },
                onDragEnd = {
                    if (dragCorner >= 0) {
                        onCropChanged(rect)
                        dragCorner = -1
                    }
                },
            )
        },
    ) {
        val left = imageRect.left + rect.left * imageRect.width
        val top = imageRect.top + rect.top * imageRect.height
        val right = imageRect.left + rect.right * imageRect.width
        val bottom = imageRect.top + rect.bottom * imageRect.height
        val dim = Color.Black.copy(alpha = 0.45f)

        // Dim everything outside the crop rectangle (four strips - avoids BlendMode.Clear layers).
        drawRect(dim, topLeft = Offset(0f, 0f), size = Size(size.width, top))
        drawRect(dim, topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom))
        drawRect(dim, topLeft = Offset(0f, top), size = Size(left, bottom - top))
        drawRect(dim, topLeft = Offset(right, top), size = Size(size.width - right, bottom - top))

        drawRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            style = Stroke(width = 2.dp.toPx()),
        )
        corners(rect).forEach { c ->
            drawCircle(Color.White, radius = 12.dp.toPx(), center = c)
            drawCircle(Color(0xFF1565C0), radius = 8.dp.toPx(), center = c)
        }
    }
}

@Composable
private fun PerspectiveOverlay(
    modifier: Modifier,
    bitmap: Bitmap,
    containerSize: IntSize,
    quad: PerspectiveQuad,
    onQuadChanged: (PerspectiveQuad) -> Unit,
) {
    val imageRect = remember(bitmap.width, bitmap.height, containerSize) {
        computeFitRect(bitmap.width, bitmap.height, containerSize.width, containerSize.height)
    }
    // Points as normalized pairs [tl, tr, br, bl]; pushed to the VM on drag end.
    var points by remember(imageRect, quad) {
        mutableStateOf(
            listOf(
                quad.topLeftX to quad.topLeftY,
                quad.topRightX to quad.topRightY,
                quad.bottomRightX to quad.bottomRightY,
                quad.bottomLeftX to quad.bottomLeftY,
            ),
        )
    }
    var dragIndex by remember { mutableStateOf(-1) }

    fun toOffset(p: Pair<Float, Float>) =
        Offset(imageRect.left + p.first * imageRect.width, imageRect.top + p.second * imageRect.height)

    Canvas(
        modifier = modifier.pointerInput(imageRect) {
            detectDragGestures(
                onDragStart = { pos ->
                    dragIndex = points.indexOfFirst { (toOffset(it) - pos).getDistance() < HANDLE_TOUCH_PX }
                },
                onDrag = { change, _ ->
                    if (dragIndex < 0) return@detectDragGestures
                    change.consume()
                    val nx = ((change.position.x - imageRect.left) / imageRect.width).coerceIn(0f, 1f)
                    val ny = ((change.position.y - imageRect.top) / imageRect.height).coerceIn(0f, 1f)
                    points = points.toMutableList().also { it[dragIndex] = nx to ny }
                },
                onDragEnd = {
                    if (dragIndex >= 0) {
                        onQuadChanged(
                            PerspectiveQuad(
                                topLeftX = points[0].first, topLeftY = points[0].second,
                                topRightX = points[1].first, topRightY = points[1].second,
                                bottomRightX = points[2].first, bottomRightY = points[2].second,
                                bottomLeftX = points[3].first, bottomLeftY = points[3].second,
                            ),
                        )
                        dragIndex = -1
                    }
                },
            )
        },
    ) {
        val offsets = points.map(::toOffset)
        val path = Path().apply {
            moveTo(offsets[0].x, offsets[0].y)
            lineTo(offsets[1].x, offsets[1].y)
            lineTo(offsets[2].x, offsets[2].y)
            lineTo(offsets[3].x, offsets[3].y)
            close()
        }
        drawPath(path, Color(0x33FFEB3B))
        drawPath(path, Color(0xFFFFEB3B), style = Stroke(width = 2.dp.toPx()))
        val cornerColors = listOf(Color(0xFFE53935), Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFF8E24AA))
        offsets.forEachIndexed { i, o ->
            drawCircle(Color.White, radius = 12.dp.toPx(), center = o)
            drawCircle(cornerColors[i], radius = 8.dp.toPx(), center = o)
        }
    }
}

@Composable
private fun DewarpOverlay(
    modifier: Modifier,
    bitmap: Bitmap,
    containerSize: IntSize,
    mesh: DewarpMesh,
    onMeshChanged: (DewarpMesh) -> Unit,
) {
    val imageRect = remember(bitmap.width, bitmap.height, containerSize) {
        computeFitRect(bitmap.width, bitmap.height, containerSize.width, containerSize.height)
    }
    val topCount = mesh.topPoints.size
    // Flattened working points [top..., bottom...] in normalized coords; pushed on drag end.
    var points by remember(imageRect, mesh) {
        mutableStateOf((mesh.topPoints + mesh.bottomPoints).map { it.x to it.y })
    }
    var dragIndex by remember { mutableStateOf(-1) }

    fun toOffset(p: Pair<Float, Float>) =
        Offset(imageRect.left + p.first * imageRect.width, imageRect.top + p.second * imageRect.height)

    Canvas(
        modifier = modifier.pointerInput(imageRect) {
            detectDragGestures(
                onDragStart = { pos ->
                    dragIndex = points.indexOfFirst { (toOffset(it) - pos).getDistance() < HANDLE_TOUCH_PX }
                },
                onDrag = { change, _ ->
                    if (dragIndex < 0) return@detectDragGestures
                    change.consume()
                    val nx = ((change.position.x - imageRect.left) / imageRect.width).coerceIn(0f, 1f)
                    val ny = ((change.position.y - imageRect.top) / imageRect.height).coerceIn(0f, 1f)
                    points = points.toMutableList().also { it[dragIndex] = nx to ny }
                },
                onDragEnd = {
                    if (dragIndex >= 0) {
                        val top = points.take(topCount).map { MeshPoint(it.first, it.second) }
                        val bottom = points.drop(topCount).map { MeshPoint(it.first, it.second) }
                        onMeshChanged(DewarpMesh(top, bottom))
                        dragIndex = -1
                    }
                },
            )
        },
    ) {
        val offsets = points.map(::toOffset)
        val topOffsets = offsets.take(topCount)
        val bottomOffsets = offsets.drop(topCount)

        fun polyline(pts: List<Offset>) {
            for (i in 0 until pts.size - 1) {
                drawLine(Color(0xFF00E5FF), pts[i], pts[i + 1], strokeWidth = 2.dp.toPx())
            }
        }
        polyline(topOffsets)
        polyline(bottomOffsets)
        // Vertical connectors between paired columns.
        for (i in 0 until minOf(topOffsets.size, bottomOffsets.size)) {
            drawLine(Color(0x5500E5FF), topOffsets[i], bottomOffsets[i], strokeWidth = 1.dp.toPx())
        }
        offsets.forEach { o ->
            drawCircle(Color.White, radius = 11.dp.toPx(), center = o)
            drawCircle(Color(0xFF00838F), radius = 7.dp.toPx(), center = o)
        }
    }
}

private data class ImageRect(val left: Float, val top: Float, val width: Float, val height: Float)

private fun computeFitRect(bmpW: Int, bmpH: Int, containerW: Int, containerH: Int): ImageRect {
    if (bmpW <= 0 || bmpH <= 0 || containerW <= 0 || containerH <= 0) {
        return ImageRect(0f, 0f, 0f, 0f)
    }
    val scale = minOf(containerW.toFloat() / bmpW, containerH.toFloat() / bmpH)
    val w = bmpW * scale
    val h = bmpH * scale
    return ImageRect((containerW - w) / 2f, (containerH - h) / 2f, w, h)
}

private fun EnhancementMode.label(): String = when (this) {
    EnhancementMode.ORIGINAL -> "元画像"
    EnhancementMode.GRAYSCALE -> "グレー"
    EnhancementMode.HIGH_CONTRAST -> "高コントラスト"
}

private const val HANDLE_TOUCH_PX = 60f
