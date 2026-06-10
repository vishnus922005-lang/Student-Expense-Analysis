package com.example.expensereader.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "category_budgets")
data class CategoryBudget(
    @PrimaryKey val category: String,   // "Food", "Travel", ...
    val monthlyBudget: Double           // e.g., 5000.0
)
