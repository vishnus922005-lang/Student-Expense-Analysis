package com.example.expensereader.util

import android.content.Context
import com.example.expensereader.db.AppDatabase

object NameResolver {

    private fun normDigits(raw: String?): String? =
        raw?.replace(Regex("[^0-9]"), "")?.takeIf { it.isNotBlank() }

    private fun last4(raw: String?): String? =
        normDigits(raw)?.takeLast(4)

    /**
     * Resolve name using saved mapping table:
     * 1) by REF mapping (REF:xxxx)
     * 2) by ACC mapping (ACC:1234)
     * 3) fallback to original name
     */
    suspend fun resolve(
        context: Context,
        originalName: String,
        ref: String?,
        accNo: String?
    ): String {

        val mappingDao = AppDatabase.getInstance(context).pdfMappingDao()

        val refKey = normDigits(ref)
        if (!refKey.isNullOrBlank()) {
            val byRef = mappingDao.getNameByKey("REF:$refKey")
            if (!byRef.isNullOrBlank()) return byRef
        }

        val acc4 = last4(accNo)
        if (!acc4.isNullOrBlank()) {
            val byAcc = mappingDao.getNameByKey("ACC:$acc4")
            if (!byAcc.isNullOrBlank()) return byAcc
        }

        return originalName
    }
}
