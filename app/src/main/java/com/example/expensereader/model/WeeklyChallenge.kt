package com.example.expensereader.model

data class WeeklyChallenge(
    val id: Int,
    val title: String,
    val description: String,
    val emoji: String = "🏆",
    val rewardPoints: Int = 50,
    val durationDays: Int = 7,
    val startDayMillis: Long = 0L
)
