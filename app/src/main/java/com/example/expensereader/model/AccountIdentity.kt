package com.example.expensereader.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "account_identity",
    indices = [Index(value = ["accNo"], unique = true)]
)
data class AccountIdentity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // store exactly what you store in expenses.accNo (ex: last4 or full)
    val accNo: String,

    // resolved name learned from PDF
    val name: String
)
