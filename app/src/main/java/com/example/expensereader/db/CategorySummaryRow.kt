package com.example.expensereader.db

data class CategorySummaryRow(
    val category: String,
    val totalAmount: Double,
    val txnCount: Int
)
