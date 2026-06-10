package com.example.expensereader.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_mapping")
data class PdfMapping(
    @PrimaryKey val key: String,   // "REF:116960..." or "ACC:1614"
    val name: String,
    val accNo: String? = null,
    val upiRef: String? = null
)
