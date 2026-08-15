package com.local.bookocr.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import com.local.bookocr.data.local.dao.BookWithPageCount
import com.local.bookocr.ui.bookOcrContainer

@Composable
fun LibraryScreen(onOpenBook: (Long) -> Unit) {
    val container = LocalContext.current.bookOcrContainer()
    val viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container.bookRepository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onErrorShown()
        }
    }

    LibraryScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onOpenBook = onOpenBook,
        onAddBookClick = viewModel::onShowCreateDialog,
        onDeleteRequest = viewModel::onRequestDelete,
        onDismissCreateDialog = viewModel::onDismissCreateDialog,
        onCreateBook = viewModel::onCreateBook,
        onDismissDeleteRequest = viewModel::onDismissDeleteRequest,
        onConfirmDelete = viewModel::onConfirmDelete,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScreenContent(
    uiState: LibraryUiState,
    snackbarHostState: SnackbarHostState,
    onOpenBook: (Long) -> Unit,
    onAddBookClick: () -> Unit,
    onDeleteRequest: (BookWithPageCount) -> Unit,
    onDismissCreateDialog: () -> Unit,
    onCreateBook: (String, String?) -> Unit,
    onDismissDeleteRequest: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("本棚") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (uiState.books.isNotEmpty()) {
                FloatingActionButton(onClick = onAddBookClick) {
                    Icon(Icons.Default.Add, contentDescription = "本を追加")
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                uiState.books.isEmpty() -> LibraryEmptyState(onCreateClick = onAddBookClick)
                else -> BookList(books = uiState.books, onOpenBook = onOpenBook, onDeleteRequest = onDeleteRequest)
            }
        }
    }

    if (uiState.showCreateDialog) {
        CreateBookDialog(onDismiss = onDismissCreateDialog, onCreate = onCreateBook)
    }

    uiState.bookPendingDeletion?.let { book ->
        DeleteBookDialog(book = book, onDismiss = onDismissDeleteRequest, onConfirm = onConfirmDelete)
    }
}

@Composable
private fun LibraryEmptyState(onCreateClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.MenuBook,
            contentDescription = null,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Text(text = "まだ本が登録されていません")
        Text(text = "最初の本を追加して読書メモを記録しましょう")
        TextButton(onClick = onCreateClick, modifier = Modifier.padding(top = 16.dp)) {
            Text("最初の本を追加")
        }
    }
}

@Composable
private fun BookList(
    books: List<BookWithPageCount>,
    onOpenBook: (Long) -> Unit,
    onDeleteRequest: (BookWithPageCount) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        items(books, key = { it.id }) { book ->
            BookRow(
                book = book,
                onClick = { onOpenBook(book.id) },
                onDeleteClick = { onDeleteRequest(book) },
            )
        }
    }
}

@Composable
private fun BookRow(book: BookWithPageCount, onClick: () -> Unit, onDeleteClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        ListItem(
            modifier = Modifier.fillMaxWidth(),
            headlineContent = { Text(book.title) },
            supportingContent = {
                val author = book.author
                val pageLabel = "${book.pageCount}ページ"
                Text(if (author != null) "$author ・ $pageLabel" else pageLabel)
            },
            trailingContent = {
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.Delete, contentDescription = "削除")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateBookDialog(onDismiss: () -> Unit, onCreate: (String, String?) -> Unit) {
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("本を追加") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("タイトル") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("著者（任意）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(title, author.ifBlank { null }) },
                enabled = title.isNotBlank(),
            ) {
                Text("追加")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}

@Composable
private fun DeleteBookDialog(book: BookWithPageCount, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("本を削除しますか？") },
        text = { Text("「${book.title}」とすべてのページ・画像・OCR結果が削除されます。この操作は取り消せません。") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("削除") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}
