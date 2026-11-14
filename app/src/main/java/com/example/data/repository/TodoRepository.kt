package com.example.data.repository

import com.example.data.local.TodoDao
import com.example.data.local.TodoEntity
import kotlinx.coroutines.flow.Flow

interface TodoRepository {
    val allTodos: Flow<List<TodoEntity>>
    suspend fun insertTodo(todo: TodoEntity): Long
    suspend fun updateTodo(todo: TodoEntity)
    suspend fun deleteTodo(todo: TodoEntity)
    suspend fun deleteCompletedTodos()
}

class RoomTodoRepository(private val todoDao: TodoDao) : TodoRepository {
    override val allTodos: Flow<List<TodoEntity>> = todoDao.getAllTodos()

    override suspend fun insertTodo(todo: TodoEntity): Long = todoDao.insertTodo(todo)

    override suspend fun updateTodo(todo: TodoEntity) = todoDao.updateTodo(todo)

    override suspend fun deleteTodo(todo: TodoEntity) = todoDao.deleteTodo(todo)

    override suspend fun deleteCompletedTodos() = todoDao.deleteCompletedTodos()
}
