package com.example.expensereader.importer

data class PdfTxn(
    val dateTimeMillis: Long,
    val name: String?,
    val amount: Double,
    val direction: String,
    val accNo: String?,
    val ref: String?
)
