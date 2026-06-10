package com.example.expensereader.ml

enum class InsightSeverity {
    GOOD, WARNING, CRITICAL
}

data class InsightModel(
    val title: String,
    val message: String,
    val severity: InsightSeverity,
    val score: Int
)
