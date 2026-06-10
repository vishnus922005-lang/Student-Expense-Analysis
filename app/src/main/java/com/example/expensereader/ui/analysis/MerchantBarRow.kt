package com.example.expensereader.ui.analysis

data class MerchantBarRow(
    val merchant: String,
    val amount: Double,
    val txnCount: Int,
    val percent: Double,   // 0..100
    val ratio: Float       // 0..1 for bar fill
)
