package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.export.ExportFormat
import com.example.ui.components.ExportBottomSheet
import com.example.ui.components.TodoCard
import com.example.ui.components.TodoEditorSheet
import com.example.ui.viewmodel.TodoFilter
import com.example.ui.viewmodel.TodoUiEvent
import com.example.ui.viewmodel.TodoUiState
import com.example.ui.viewmodel.TodoViewModel

@Composable
fun TodoScreen(
    viewModel: TodoViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ExportFormat.PDF.mimeType)
    ) { uri -> viewModel.completeSafExport(uri) }

    val docxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ExportFormat.DOCX.mimeType)
    ) { uri -> viewModel.completeSafExport(uri) }

    val txtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ExportFormat.TXT.mimeType)
    ) { uri -> viewModel.completeSafExport(uri) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TodoUiEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        duration = SnackbarDuration.Short
                    )
                }
                is TodoUiEvent.ShowDeletedWithUndo -> {
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.task_deleted_message),
                        actionLabel = context.getString(R.string.action_undo),
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.restoreTodo(event.todo)
                    }
                }
                is TodoUiEvent.ShareExportedFile -> {
                    val authority = "${context.packageName}.fileprovider"
                    val shareUri = FileProvider.getUriForFile(context, authority, event.file)
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = event.format.mimeType
                        putExtra(Intent.EXTRA_STREAM, shareUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(sendIntent, context.getString(R.string.action_share))
                    )
                }
            }
        }
    }

    val accentGlow = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    val warmGlow = MaterialTheme.colorScheme.secondary.copy(alpha = 0.06f)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accentGlow, Color.Transparent),
                        center = Offset(size.width * 0.9f, size.height * 0.08f),
                        radius = size.width * 0.7f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(warmGlow, Color.Transparent),
                        center = Offset(size.width * 0.1f, size.height * 0.85f),
                        radius = size.width * 0.65f
                    )
                )
            },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            if (uiState is TodoUiState.Success) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.openNewTodoEditor() },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.cd_add_todo)
                        )
                    },
                    text = { Text(stringResource(R.string.new_task_fab)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.testTag("add_todo_fab")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            when (val state = uiState) {
                TodoUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                is TodoUiState.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 680.dp)
                            .padding(horizontal = 20.dp)
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))

                        FolioHeader(
                            activeCount = state.activeCount,
                            completedCount = state.completedCount,
                            hasExportableItems = state.filteredTodos.isNotEmpty(),
                            isSearchExpanded = state.isSearchExpanded,
                            onToggleSearch = { viewModel.toggleSearch() },
                            onExportAll = { viewModel.openExportForAll() }
                        )

                        AnimatedVisibility(
                            visible = state.isSearchExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            OutlinedTextField(
                                value = state.searchQuery,
                                onValueChange = { viewModel.updateSearchQuery(it) },
                                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                                singleLine = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = null
                                    )
                                },
                                trailingIcon = {
                                    if (state.searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { viewModel.updateSearchQuery("") },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = stringResource(R.string.cd_close_search)
                                            )
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                                    .testTag("search_input")
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        FilterBar(
                            selectedFilter = state.filter,
                            showClearDone = state.completedCount > 0,
                            onSelectFilter = { viewModel.setFilter(it) },
                            onClearDone = { viewModel.clearCompleted() }
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        if (state.filteredTodos.isEmpty()) {
                            EmptyTodosState(
                                isFilteringOrSearching = state.allTodos.isNotEmpty(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(bottom = 96.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(
                                    items = state.filteredTodos,
                                    key = { it.id }
                                ) { todo ->
                                    TodoCard(
                                        todo = todo,
                                        onToggleComplete = { viewModel.toggleTodoCompleted(todo) },
                                        onClick = { viewModel.openEditTodoEditor(todo) },
                                        onExportClick = { viewModel.openExportForSingle(todo) },
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }
                    }

                    state.editorState?.let { editorState ->
                        TodoEditorSheet(
                            state = editorState,
                            onTitleChange = viewModel::updateEditorTitle,
                            onNoteChange = viewModel::updateEditorNote,
                            onPriorityChange = viewModel::updateEditorPriority,
                            onSave = viewModel::saveEditorTodo,
                            onDelete = editorState.editingTodo?.let { todo ->
                                { viewModel.deleteTodo(todo) }
                            },
                            onExportNote = viewModel::openExportFromEditor,
                            onDismiss = viewModel::dismissEditor
                        )
                    }

                    state.exportTarget?.let { exportTarget ->
                        ExportBottomSheet(
                            target = exportTarget,
                            isExporting = state.isExporting,
                            onSaveFormat = { format ->
                                val suggestedName = viewModel.prepareSafExport(format)
                                if (suggestedName != null) {
                                    when (format) {
                                        ExportFormat.PDF -> pdfLauncher.launch(suggestedName)
                                        ExportFormat.DOCX -> docxLauncher.launch(suggestedName)
                                        ExportFormat.TXT -> txtLauncher.launch(suggestedName)
                                    }
                                }
                            },
                            onShareFormat = { format ->
                                viewModel.shareExport(format)
                            },
                            onDismiss = viewModel::dismissExportSheet
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolioHeader(
    activeCount: Int,
    completedCount: Int,
    hasExportableItems: Boolean,
    isSearchExpanded: Boolean,
    onToggleSearch: () -> Unit,
    onExportAll: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(2.dp))
            val subtitleText = if (activeCount == 0 && completedCount == 0) {
                stringResource(R.string.header_subtitle_empty)
            } else {
                stringResource(R.string.header_subtitle_format, activeCount, completedCount)
            }
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onToggleSearch,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("toggle_search_button")
            ) {
                Icon(
                    imageVector = if (isSearchExpanded) Icons.Rounded.Close else Icons.Rounded.Search,
                    contentDescription = stringResource(
                        if (isSearchExpanded) R.string.cd_close_search else R.string.cd_search
                    ),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            if (hasExportableItems) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    IconButton(
                        onClick = onExportAll,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("export_all_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.IosShare,
                            contentDescription = stringResource(R.string.cd_export_all),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterBar(
    selectedFilter: TodoFilter,
    showClearDone: Boolean,
    onSelectFilter: (TodoFilter) -> Unit,
    onClearDone: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TodoFilter.entries.forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { onSelectFilter(filter) },
                    label = {
                        Text(
                            text = filter.label,
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("filter_chip_${filter.name.lowercase()}")
                )
            }
        }

        if (showClearDone) {
            TextButton(
                onClick = onClearDone,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("clear_completed_button")
            ) {
                Text(
                    text = stringResource(R.string.clear_completed),
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun EmptyTodosState(
    isFilteringOrSearching: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_empty_notes),
            contentDescription = stringResource(R.string.cd_empty_illustration),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 240.dp, height = 180.dp)
                .clip(RoundedCornerShape(22.dp))
        )
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            text = stringResource(
                if (isFilteringOrSearching) R.string.empty_search_title else R.string.empty_title
            ),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(
                if (isFilteringOrSearching) R.string.empty_search_subtitle else R.string.empty_subtitle
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))
    }
}
