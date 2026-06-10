package com.example.expensereader.ui.analysis

data class WeekSplitItem(
    val weekNo: Int,
    val weekendAmt: Double,
    val weekendTxn: Int,
    val weekdayAmt: Double,
    val weekdayTxn: Int
)
