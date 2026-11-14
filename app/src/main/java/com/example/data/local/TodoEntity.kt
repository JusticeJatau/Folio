package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Priority(val label: String, val rank: Int) {
    LOW("Low", 0),
    NORMAL("Normal", 1),
    HIGH("High", 2)
}

@Entity(tableName = "todos")
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val note: String = "",
    val isCompleted: Boolean = false,
    val priority: Priority = Priority.NORMAL,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
