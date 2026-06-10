// File: app/src/main/java/com/example/expensereader/sms/ParsedSms.kt
package com.example.expensereader.sms

data class ParsedSms(
    val name: String? = null,
    val amount: Double? = null,
    val dateMillis: Long,
    val type: String? = null,      // "DEBIT" / "CREDIT"
    val rawText: String = "",
    val accNo: String? = null,      // YOUR account last4 (0575)
    val merchantAcc: String? = null,// OTHER party last4 (0025/1173)
    val ref: String? = null
)
