package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.local.Priority
import com.example.data.local.TodoEntity
import com.example.data.repository.TodoRepository
import com.example.domain.export.DocumentExporter
import com.example.domain.export.ExportFormat
import com.example.domain.export.ExportTarget
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class TodoFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    COMPLETED("Done")
}

data class TodoEditorState(
    val editingTodo: TodoEntity? = null,
    val title: String = "",
    val note: String = "",
    val priority: Priority = Priority.NORMAL,
    val showTitleError: Boolean = false
)

sealed interface TodoUiState {
    data object Loading : TodoUiState
    data class Success(
        val allTodos: List<TodoEntity>,
        val filteredTodos: List<TodoEntity>,
        val filter: TodoFilter,
        val searchQuery: String,
        val isSearchExpanded: Boolean,
        val activeCount: Int,
        val completedCount: Int,
        val editorState: TodoEditorState?,
        val exportTarget: ExportTarget?,
        val isExporting: Boolean
    ) : TodoUiState
}

sealed interface TodoUiEvent {
    data class ShowMessage(val message: String) : TodoUiEvent
    data class ShowDeletedWithUndo(val todo: TodoEntity) : TodoUiEvent
    data class ShareExportedFile(val file: File, val format: ExportFormat) : TodoUiEvent
}

class TodoViewModel(
    private val repository: TodoRepository,
    private val exporter: DocumentExporter,
    private val application: Application
) : ViewModel() {

    private val filterFlow = MutableStateFlow(TodoFilter.ALL)
    private val searchQueryFlow = MutableStateFlow("")
    private val searchExpandedFlow = MutableStateFlow(false)
    private val editorStateFlow = MutableStateFlow<TodoEditorState?>(null)
    private val exportTargetFlow = MutableStateFlow<ExportTarget?>(null)
    private val isExportingFlow = MutableStateFlow(false)

    private var pendingSafExport: Pair<ExportTarget, ExportFormat>? = null

    private val _events = Channel<TodoUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState: StateFlow<TodoUiState> = combine(
        repository.allTodos,
        filterFlow,
        searchQueryFlow,
        searchExpandedFlow,
        editorStateFlow,
        exportTargetFlow,
        isExportingFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val todos = values[0] as List<TodoEntity>
        val filter = values[1] as TodoFilter
        val query = values[2] as String
        val searchExpanded = values[3] as Boolean
        val editor = values[4] as TodoEditorState?
        val exportTarget = values[5] as ExportTarget?
        val isExporting = values[6] as Boolean

        val trimmedQuery = query.trim()
        val filtered = todos.filter { todo ->
            val matchesFilter = when (filter) {
                TodoFilter.ALL -> true
                TodoFilter.ACTIVE -> !todo.isCompleted
                TodoFilter.COMPLETED -> todo.isCompleted
            }
            val matchesQuery = trimmedQuery.isEmpty() ||
                todo.title.contains(trimmedQuery, ignoreCase = true) ||
                todo.note.contains(trimmedQuery, ignoreCase = true)
            matchesFilter && matchesQuery
        }

        TodoUiState.Success(
            allTodos = todos,
            filteredTodos = filtered,
            filter = filter,
            searchQuery = query,
            isSearchExpanded = searchExpanded,
            activeCount = todos.count { !it.isCompleted },
            completedCount = todos.count { it.isCompleted },
            editorState = editor,
            exportTarget = exportTarget,
            isExporting = isExporting
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TodoUiState.Loading
    )

    fun setFilter(filter: TodoFilter) {
        filterFlow.value = filter
    }

    fun toggleSearch() {
        val next = !searchExpandedFlow.value
        searchExpandedFlow.value = next
        if (!next) {
            searchQueryFlow.value = ""
        }
    }

    fun updateSearchQuery(query: String) {
        searchQueryFlow.value = query
    }

    fun openNewTodoEditor() {
        editorStateFlow.value = TodoEditorState()
    }

    fun openEditTodoEditor(todo: TodoEntity) {
        editorStateFlow.value = TodoEditorState(
            editingTodo = todo,
            title = todo.title,
            note = todo.note,
            priority = todo.priority
        )
    }

    fun dismissEditor() {
        editorStateFlow.value = null
    }

    fun updateEditorTitle(title: String) {
        editorStateFlow.value = editorStateFlow.value?.copy(
            title = title,
            showTitleError = false
        )
    }

    fun updateEditorNote(note: String) {
        editorStateFlow.value = editorStateFlow.value?.copy(note = note)
    }

    fun updateEditorPriority(priority: Priority) {
        editorStateFlow.value = editorStateFlow.value?.copy(priority = priority)
    }

    fun saveEditorTodo() {
        val current = editorStateFlow.value ?: return
        val cleanTitle = current.title.trim()
        if (cleanTitle.isEmpty()) {
            editorStateFlow.value = current.copy(showTitleError = true)
            return
        }
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            if (current.editingTodo == null) {
                repository.insertTodo(
                    TodoEntity(
                        title = cleanTitle,
                        note = current.note.trimEnd(),
                        priority = current.priority,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            } else {
                repository.updateTodo(
                    current.editingTodo.copy(
                        title = cleanTitle,
                        note = current.note.trimEnd(),
                        priority = current.priority,
                        updatedAt = now
                    )
                )
            }
            editorStateFlow.value = null
        }
    }

    fun toggleTodoCompleted(todo: TodoEntity) {
        viewModelScope.launch {
            repository.updateTodo(
                todo.copy(
                    isCompleted = !todo.isCompleted,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteTodo(todo: TodoEntity) {
        viewModelScope.launch {
            repository.deleteTodo(todo)
            if (editorStateFlow.value?.editingTodo?.id == todo.id) {
                editorStateFlow.value = null
            }
            _events.send(TodoUiEvent.ShowDeletedWithUndo(todo))
        }
    }

    fun restoreTodo(todo: TodoEntity) {
        viewModelScope.launch {
            repository.insertTodo(todo)
        }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            repository.deleteCompletedTodos()
        }
    }

    fun openExportForSingle(todo: TodoEntity) {
        exportTargetFlow.value = ExportTarget.Single(todo)
    }

    fun openExportFromEditor() {
        val editor = editorStateFlow.value ?: return
        val cleanTitle = editor.title.trim()
        if (cleanTitle.isEmpty()) {
            editorStateFlow.value = editor.copy(showTitleError = true)
            return
        }
        val snapshot = (editor.editingTodo ?: TodoEntity(title = cleanTitle)).copy(
            title = cleanTitle,
            note = editor.note.trimEnd(),
            priority = editor.priority,
            updatedAt = System.currentTimeMillis()
        )
        exportTargetFlow.value = ExportTarget.Single(snapshot)
    }

    fun openExportForAll() {
        val state = uiState.value as? TodoUiState.Success ?: return
        val items = state.filteredTodos
        if (items.isEmpty()) return
        exportTargetFlow.value = ExportTarget.All(
            todos = items,
            filterLabel = state.filter.label
        )
    }

    fun dismissExportSheet() {
        exportTargetFlow.value = null
    }

    fun prepareSafExport(format: ExportFormat): String? {
        val target = exportTargetFlow.value ?: return null
        pendingSafExport = target to format
        return exporter.buildSuggestedFileName(target, format)
    }

    fun completeSafExport(uri: Uri?) {
        val pending = pendingSafExport
        pendingSafExport = null
        if (uri == null || pending == null) return
        val (target, format) = pending

        viewModelScope.launch {
            isExportingFlow.value = true
            val result = exporter.exportToUri(application, uri, target, format)
            isExportingFlow.value = false
            exportTargetFlow.value = null

            result.onSuccess {
                val fileName = exporter.buildSuggestedFileName(target, format)
                _events.send(
                    TodoUiEvent.ShowMessage(
                        application.getString(R.string.export_saved_message, fileName)
                    )
                )
            }.onFailure { error ->
                _events.send(
                    TodoUiEvent.ShowMessage(
                        application.getString(
                            R.string.export_error_message,
                            error.localizedMessage ?: "Unknown error"
                        )
                    )
                )
            }
        }
    }

    fun shareExport(format: ExportFormat) {
        val target = exportTargetFlow.value ?: return
        viewModelScope.launch {
            isExportingFlow.value = true
            val result = exporter.exportToCacheFile(application, target, format)
            isExportingFlow.value = false
            exportTargetFlow.value = null

            result.onSuccess { file ->
                _events.send(TodoUiEvent.ShareExportedFile(file, format))
            }.onFailure { error ->
                _events.send(
                    TodoUiEvent.ShowMessage(
                        application.getString(
                            R.string.export_error_message,
                            error.localizedMessage ?: "Unknown error"
                        )
                    )
                )
            }
        }
    }
}

class TodoViewModelFactory(
    private val repository: TodoRepository,
    private val exporter: DocumentExporter,
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TodoViewModel::class.java)) {
            return TodoViewModel(repository, exporter, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
