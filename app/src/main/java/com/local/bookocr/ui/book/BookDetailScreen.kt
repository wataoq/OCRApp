package com.local.bookocr.ui.book

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.local.bookocr.data.local.entity.PageEntity
import com.local.bookocr.ui.bookOcrContainer
import com.local.bookocr.ui.components.PageThumbnail

@Composable
fun BookDetailScreen(onNavigateBack: () -> Unit, onOpenPage: (Long) -> Unit) {
    val container = LocalContext.current.bookOcrContainer()
    val viewModel: BookDetailViewModel = viewModel(
        factory = BookDetailViewModel.factory(container.bookRepository, container.pageRepository),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.bookDeletedExternally) {
        if (uiState.bookDeletedExternally) onNavigateBack()
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onErrorShown()
        }
    }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::importPage)
    }

    BookDetailScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onOpenPage = onOpenPage,
        onAddPageClick = {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onDeleteRequest = viewModel::onDeleteRequest,
        onDismissDeleteRequest = viewModel::onDismissDeleteRequest,
        onConfirmDelete = viewModel::onConfirmDeletePage,
        onEditPageNumberRequest = viewModel::onEditPageNumberRequest,
        onDismissPageNumberEdit = viewModel::onDismissPageNumberEdit,
        onConfirmPageNumber = viewModel::onConfirmPageNumber,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookDetailScreenContent(
    uiState: BookDetailUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onOpenPage: (Long) -> Unit,
    onAddPageClick: () -> Unit,
    onDeleteRequest: (PageEntity) -> Unit,
    onDismissDeleteRequest: () -> Unit,
    onConfirmDelete: () -> Unit,
    onEditPageNumberRequest: (PageEntity) -> Unit,
    onDismissPageNumberEdit: () -> Unit,
    onConfirmPageNumber: (Int?) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.book?.title ?: "本") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddPageClick) {
                if (uiState.isImporting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Icon(Icons.Default.Add, contentDescription = "ページを追加")
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                uiState.pages.isEmpty() -> BookDetailEmptyState(onAddPageClick)
                else -> PageList(pages = uiState.pages, onOpenPage = onOpenPage, onDeleteRequest = onDeleteRequest, onEditPageNumberRequest = onEditPageNumberRequest)
            }
        }
    }

    uiState.pagePendingDeletion?.let { page ->
        DeletePageDialog(page = page, onDismiss = onDismissDeleteRequest, onConfirm = onConfirmDelete)
    }

    uiState.pagePendingNumberEdit?.let { page ->
        EditPageNumberDialog(page = page, onDismiss = onDismissPageNumberEdit, onConfirm = onConfirmPageNumber)
    }
}

@Composable
private fun BookDetailEmptyState(onAddPageClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(imageVector = Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.padding(bottom = 16.dp))
        Text("まだページがありません")
        Text("画像を選択してページを追加しましょう")
        TextButton(onClick = onAddPageClick, modifier = Modifier.padding(top = 16.dp)) {
            Text("ページを追加")
        }
    }
}

@Composable
private fun PageList(
    pages: List<PageEntity>,
    onOpenPage: (Long) -> Unit,
    onDeleteRequest: (PageEntity) -> Unit,
    onEditPageNumberRequest: (PageEntity) -> Unit,
) {
    val container = LocalContext.current.bookOcrContainer()
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        items(pages, key = { it.id }) { page ->
            val imageFile = remember(page.storedImagePath) { container.pageRepository.resolveImageFile(page) }
            Card(onClick = { onOpenPage(page.id) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                ListItem(
                    leadingContent = { PageThumbnail(imageFile = imageFile) },
                    headlineContent = {
                        Text(page.pageNumber?.let { "ページ $it" } ?: "番号未設定")
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { onEditPageNumberRequest(page) }) {
                                Icon(Icons.Default.Edit, contentDescription = "ページ番号を編集")
                            }
                            IconButton(onClick = { onDeleteRequest(page) }) {
                                Icon(Icons.Default.Delete, contentDescription = "削除")
                            }
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeletePageDialog(page: PageEntity, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ページを削除しますか？") },
        text = { Text("画像とOCR結果が削除されます。この操作は取り消せません。") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("削除") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPageNumberDialog(page: PageEntity, onDismiss: () -> Unit, onConfirm: (Int?) -> Unit) {
    var text by remember(page.id) { mutableStateOf(page.pageNumber?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ページ番号を編集") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { input -> if (input.all(Char::isDigit)) text = input },
                label = { Text("ページ番号（空欄で未設定）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.toIntOrNull()) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}
