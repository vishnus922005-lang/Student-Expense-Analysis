package com.example.expensereader.model

data class CategorySummary(
    val category: String,
    val totalAmount: Double,
    val txnCount: Int,
    val percent: Float = 0f   // ✅ default so monthly code compiles
)
