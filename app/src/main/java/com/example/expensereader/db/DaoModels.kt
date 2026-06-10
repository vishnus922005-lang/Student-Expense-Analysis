package com.example.expensereader.db

// Result row for merchant totals inside a category
data class MerchantRow(
    val merchant: String,
    val total: Double,
    val txnCount: Int
)

// Result row for one expense date+amount for a merchant
data class MerchantDateAmountRow(
    val date: Long,
    val amount: Double
)
