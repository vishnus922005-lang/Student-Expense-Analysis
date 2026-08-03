package com.example.expensereader.db

data class MerchantRow(
    val merchant: String,
    val total: Double,
    val txnCount: Int
)

data class MerchantDateAmountRow(
    val date: Long,
    val amount: Double
)
