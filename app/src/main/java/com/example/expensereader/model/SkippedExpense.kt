// File: app/src/main/java/com/example/expensereader/model/SkippedExpense.kt
package com.example.expensereader.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "skipped_expenses")   // ✅ MUST match your DAO queries
data class SkippedExpense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val expenseId: Long
)
