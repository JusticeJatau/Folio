package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.AppDatabase
import com.example.data.repository.RoomTodoRepository
import com.example.domain.export.DocumentExporter
import com.example.ui.screens.TodoScreen
import com.example.ui.theme.FolioTheme
import com.example.ui.viewmodel.TodoViewModel
import com.example.ui.viewmodel.TodoViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getInstance(applicationContext)
        val repository = RoomTodoRepository(database.todoDao())
        val exporter = DocumentExporter()

        setContent {
            FolioTheme {
                val todoViewModel: TodoViewModel = viewModel(
                    factory = TodoViewModelFactory(
                        repository = repository,
                        exporter = exporter,
                        application = application
                    )
                )
                TodoScreen(viewModel = todoViewModel)
            }
        }
    }
}
