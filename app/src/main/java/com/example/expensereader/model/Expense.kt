// File: app/src/main/java/com/example/expensereader/model/Expense.kt
package com.example.expensereader.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    // NOTE: keep non-null. Unknown rows use name = ""
    val name: String = "",

    val amount: Double,
    val date: Long,
    val category: String,

    val type: String = "DEBIT",     // DEBIT / CREDIT
    val source: String = "SMS",     // SMS / PDF / MANUAL

    val accNo: String? = null,          // last 4 digits if possible
    val merchantAcc: String? = null,
    val upiRef: String? = null,

    val userEdited: Boolean = false,
    val needsStatementImport: Boolean = false,

    val refNo: String = "",

    // ✅ MUST have default, else you must always pass it
    val accountId: Long? = null,

    val smsBody: String = ""
)
