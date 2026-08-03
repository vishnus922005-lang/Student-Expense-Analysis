package com.example.expensereader.importer

import android.content.Context
import android.util.Log
import com.example.expensereader.db.AppDatabase
import com.example.expensereader.model.PdfMapping

object StatementFromPdfSaver {

    private const val TAG = "PDF_SMS_MATCH"

    private fun normRef(raw: String?): String? =
        raw?.replace(Regex("[^0-9]"), "")?.takeIf { it.isNotBlank() }

    suspend fun applyPdfToSms(context: Context, txns: List<PdfTxn>) {

        val db = AppDatabase.getInstance(context)
        val dao = db.expenseDao()
        val mappingDao = db.pdfMappingDao()

        var total = 0
        var rowsUpdatedByRef = 0
        var rowsSpreadByMerchantAcc = 0
        var mappingsSaved = 0
        var backfilledByUpi = 0
        var backfilledByAcc = 0
        var spreadSkippedNotUnique = 0

        Log.d(TAG, "===== APPLY PDF START =====")
        Log.d(TAG, "pdfTxns=${txns.size}")

        for (t in txns) {
            total++

            val name = t.name?.trim().orEmpty()
            if (name.isBlank() || name.equals("UNKNOWN", true) || name.startsWith("Unknown", true)) {
                Log.d(TAG, "SKIP invalid PDF name='$name' ref=${t.ref}")
                continue
            }

            val ref = normRef(t.ref)
            if (ref == null) {
                Log.d(TAG, "SKIP[$total] ref missing/invalid | name=$name | rawRef=${t.ref}")
                continue
            }

            val category = com.example.expensereader.ml.CategoryResolver.resolve(context, name)

            Log.d(
                TAG,
                "PDF[$total] name='$name' | ref='$ref' | cat='$category' | amount=${t.amount} | accRaw=${t.accNo}"
            )

      
            mappingDao.insertOrUpdate(
                PdfMapping(
                    key = MappingKeys.refKey(ref),
                    name = name,
                    accNo = null,
                    upiRef = ref
                )
            )
            mappingsSaved++
            Log.d(TAG, "MAP_SAVE_REF[$total] key=${MappingKeys.refKey(ref)} name='$name'")

            val updated = dao.updateByRefIfUnknown(ref, name, category)

            if (updated > 0) {
                rowsUpdatedByRef += updated
                Log.d(TAG, "MATCH_REF[$total] ref=$ref -> rowsUpdated=$updated")
            } else {
                Log.d(TAG, "NO_MATCH_REF[$total] ref=$ref (not found OR not Unknown)")
                continue
            }

            val merchantAcc = dao.findMerchantAccByRef(ref)?.trim().orEmpty()
            if (merchantAcc.isBlank()) {
                Log.d(TAG, "NO_MERCHANT_ACC[$total] ref=$ref -> cannot spread")
                continue
            }

            val distinctNames = dao.countDistinctKnownNamesForMerchantAcc(merchantAcc)
            if (distinctNames != 1) {
                spreadSkippedNotUnique++
                Log.w(
                    TAG,
                    "SPREAD_BLOCKED[$total] merchantAcc=$merchantAcc distinctKnownNames=$distinctNames -> NOT SAFE, skip spread"
                )
                continue
            }

            Log.d(TAG, "MERCHANT_ACC[$total] ref=$ref -> merchantAcc='$merchantAcc' (safeSpread=true)")

            val spread = dao.updateByMerchantAccIfUnknown(merchantAcc, name, category)
            rowsSpreadByMerchantAcc += spread
            Log.d(TAG, "SPREAD_ACC[$total] acc=$merchantAcc -> rowsUpdated=$spread")
        }

        val filledPending = dao.fillUnknownNamesForPendingSms()
        Log.d(TAG, "fillUnknownNamesForPendingSms() -> rows=$filledPending")

        try {
            backfilledByUpi = dao.backfillUnknownNamesFromPdfMappingByUpiRef()
            backfilledByAcc = dao.backfillUnknownNamesFromPdfMappingByAccNo()
            Log.d(TAG, "GLOBAL_BACKFILL -> byUpi=$backfilledByUpi | byAcc=$backfilledByAcc")
        } catch (e: Throwable) {
            Log.e(TAG, "Backfill DAO methods missing/failed: ${e.message}", e)
        }

        Log.d(
            TAG,
            """
            ===== APPLY PDF SUMMARY =====
            pdfTxnsTotal=$total
            mappingsSaved=$mappingsSaved
            rowsUpdatedByRef=$rowsUpdatedByRef
            rowsSpreadByMerchantAcc=$rowsSpreadByMerchantAcc
            spreadSkippedNotUnique=$spreadSkippedNotUnique
            unknownFilledPending=$filledPending
            backfilledByUpi=$backfilledByUpi
            backfilledByAcc=$backfilledByAcc
            =============================
            """.trimIndent()
        )
    }
}
