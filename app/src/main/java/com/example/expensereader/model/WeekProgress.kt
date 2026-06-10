package com.example.expensereader.model

import com.google.firebase.Timestamp

data class WeekProgress(
    val currentOrder: Int = 1,
    val cycle: Int = 1,
    val weekStart: Long = 0L,
    val status: String = "ACTIVE",   // ACTIVE / ACCEPTED / COMPLETED
    val progress: Int = 0,
    val target: Int = 0,
    val title: String = "",
    val acceptedAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)
