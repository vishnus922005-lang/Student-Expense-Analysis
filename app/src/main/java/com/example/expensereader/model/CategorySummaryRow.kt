package com.example.expensereader.model

data class CategorySummaryRow(
    val category: String,
    val totalAmount: Double,
    val txnCount: Int
)
