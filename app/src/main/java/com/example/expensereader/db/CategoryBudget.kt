package com.example.expensereader.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "category_budgets")
data class CategoryBudget(
    @PrimaryKey val category: String,   
    val monthlyBudget: Double           
)
