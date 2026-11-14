package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.Priority
import com.example.data.local.TodoEntity
import com.example.data.repository.RoomTodoRepository
import com.example.domain.export.DocumentExporter
import com.example.domain.export.ExportFormat
import com.example.domain.export.ExportTarget
import com.example.ui.viewmodel.TodoFilter
import com.example.ui.viewmodel.TodoUiState
import com.example.ui.viewmodel.TodoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TodoRepositoryAndExportTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var database: AppDatabase
    private lateinit var repository: RoomTodoRepository
    private lateinit var exporter: DocumentExporter
    private lateinit var application: Application

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomTodoRepository(database.todoDao())
        exporter = DocumentExporter()
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `room repository inserts updates and deletes todos`() = runTest {
        val id = repository.insertTodo(
            TodoEntity(
                title = "Prepare presentation",
                note = "Include Q4 metrics & team highlights",
                priority = Priority.HIGH
            )
        )
        assertTrue(id > 0L)

        var items = repository.allTodos.first()
        assertEquals(1, items.size)
        assertEquals("Prepare presentation", items.first().title)

        repository.updateTodo(items.first().copy(isCompleted = true))
        items = repository.allTodos.first()
        assertTrue(items.first().isCompleted)

        repository.deleteCompletedTodos()
        items = repository.allTodos.first()
        assertTrue(items.isEmpty())
    }

    @Test
    fun `exporter generates valid txt and docx documents`() {
        val todo = TodoEntity(
            id = 1L,
            title = "Grocery & Meal Prep",
            note = "Apples & greens\nOlive oil <extra virgin>",
            priority = Priority.NORMAL,
            isCompleted = false
        )

        // TXT
        val txtOut = ByteArrayOutputStream()
        exporter.writeDocument(listOf(todo), "Grocery & Meal Prep", ExportFormat.TXT, txtOut)
        val txtContent = txtOut.toString(Charsets.UTF_8.name())
        assertTrue(txtContent.contains("Grocery & Meal Prep"))
        assertTrue(txtContent.contains("Apples & greens"))

        // DOCX
        val docxOut = ByteArrayOutputStream()
        exporter.writeDocument(listOf(todo), "Grocery & Meal Prep", ExportFormat.DOCX, docxOut)
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(docxOut.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        assertTrue(entries.containsKey("[Content_Types].xml"))
        assertTrue(entries.containsKey("_rels/.rels"))
        assertTrue(entries.containsKey("word/document.xml"))
        val docXml = entries.getValue("word/document.xml")
        assertTrue(docXml.contains("Grocery &amp; Meal Prep"))
        assertTrue(docXml.contains("Olive oil &lt;extra virgin&gt;"))
    }

    @Test
    fun `exporter generates valid pdf document`() {
        val todo = TodoEntity(
            id = 1L,
            title = "Grocery & Meal Prep",
            note = "Apples & greens\nOlive oil <extra virgin>",
            priority = Priority.NORMAL,
            isCompleted = false
        )

        val pdfOut = ByteArrayOutputStream()
        exporter.writeDocument(listOf(todo), "Grocery & Meal Prep", ExportFormat.PDF, pdfOut)
        val pdfBytes = pdfOut.toByteArray()
        assertTrue(pdfBytes.isNotEmpty())
        val header = String(pdfBytes.copyOfRange(0, 5), Charsets.US_ASCII)
        assertEquals("%PDF-", header)
    }

    @Test
    fun `viewmodel filters and searches todos reactively`() = runTest {
        val viewModel = TodoViewModel(repository, exporter, application)

        repository.insertTodo(TodoEntity(title = "Buy coffee beans", note = "Ethiopian roast", isCompleted = false))
        repository.insertTodo(TodoEntity(title = "Send invoice", note = "Client A", isCompleted = true))

        val allState = viewModel.uiState.first {
            it is TodoUiState.Success && it.allTodos.size == 2
        } as TodoUiState.Success
        assertEquals(2, allState.filteredTodos.size)
        assertEquals(1, allState.activeCount)
        assertEquals(1, allState.completedCount)

        viewModel.setFilter(TodoFilter.ACTIVE)
        val activeState = viewModel.uiState.first {
            it is TodoUiState.Success && it.filter == TodoFilter.ACTIVE
        } as TodoUiState.Success
        assertEquals(1, activeState.filteredTodos.size)
        assertEquals("Buy coffee beans", activeState.filteredTodos.first().title)

        viewModel.setFilter(TodoFilter.ALL)
        viewModel.toggleSearch()
        viewModel.updateSearchQuery("Ethiopian")
        val searchState = viewModel.uiState.first {
            it is TodoUiState.Success && it.searchQuery == "Ethiopian"
        } as TodoUiState.Success
        assertEquals(1, searchState.filteredTodos.size)
        assertEquals("Buy coffee beans", searchState.filteredTodos.first().title)

        val suggested = exporter.buildSuggestedFileName(
            ExportTarget.Single(searchState.filteredTodos.first()),
            ExportFormat.PDF
        )
        assertEquals("buy_coffee_beans.pdf", suggested)
    }
}
