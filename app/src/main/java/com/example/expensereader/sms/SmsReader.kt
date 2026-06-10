// File: app/src/main/java/com/example/expensereader/sms/SmsReader.kt
package com.example.expensereader.sms

import android.content.Context
import android.content.SharedPreferences
import android.provider.Telephony
import android.util.Log
import com.example.expensereader.db.AppDatabase
import com.example.expensereader.importer.MappingKeys
import com.example.expensereader.model.Expense
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object SmsReader {

    private const val TAG = "SmsReader"

    private const val PREFS = "sms_import_prefs"
    private const val KEY_LAST_DATE = "last_imported_sms_date"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun getLastImportedDate(context: Context): Long =
        prefs(context).getLong(KEY_LAST_DATE, 0L)

    private fun setLastImportedDate(context: Context, value: Long) {
        prefs(context).edit().putLong(KEY_LAST_DATE, value).apply()
    }

    private fun sixMonthsAgoMillis(): Long =
        Calendar.getInstance().apply { add(Calendar.MONTH, -6) }.timeInMillis

    private fun digits(raw: String?): String? =
        raw?.replace(Regex("[^0-9]"), "")?.takeIf { it.isNotBlank() }

    private fun last4(raw: String?): String? =
        digits(raw)?.takeLast(4)

    private fun extractMyAccLast4(text: String): String? =
        Regex("""Your\s+a/c\s+no\.?\s+[Xx*]*([0-9]{4})""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)

    private fun extractMerchantAccLast4(text: String): String? =
        Regex(
            """(credited\s+to|debited\s+from)\s+a/c\s+no\.?\s+[Xx*]*([0-9]{4})""",
            RegexOption.IGNORE_CASE
        ).find(text)?.groupValues?.get(2)

    private fun isRealName(rawName: String?): Boolean {
        val n = rawName?.trim().orEmpty()
        if (n.isBlank()) return false
        if (n.matches(Regex("""\d+"""))) return false
        if (n.matches(Regex(""".*\b\d{4}\b.*""")) && n.length <= 12) return false
        if (n.startsWith("unknown", ignoreCase = true)) return false
        return true
    }

    suspend fun importAll(context: Context) {
        importSince(context, sinceMillis = 0L, updateLastDate = true)
    }

    suspend fun importNew(context: Context) {
        val since = getLastImportedDate(context)
        importSince(context, sinceMillis = since, updateLastDate = true)
    }

    private suspend fun importSince(
        context: Context,
        sinceMillis: Long,
        updateLastDate: Boolean
    ) = withContext(Dispatchers.IO) {

        val resolver = context.contentResolver

        val sixMonthsAgo = sixMonthsAgoMillis()
        val effectiveSince = maxOf(sinceMillis, sixMonthsAgo)

        val selection = "${Telephony.Sms.DATE} >= ?"
        val selectionArgs = arrayOf(effectiveSince.toString())

        val cursor = resolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.BODY, Telephony.Sms.ADDRESS, Telephony.Sms.DATE),
            selection,
            selectionArgs,
            Telephony.Sms.DEFAULT_SORT_ORDER
        ) ?: return@withContext

        val db = AppDatabase.getInstance(context)
        val dao = db.expenseDao()
        val mappingDao = db.pdfMappingDao()

        val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
        val addrIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
        val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

        var total = 0
        var parsedCount = 0
        var inserted = 0
        var dupSkipped = 0
        var dupRepaired = 0
        var dupCatFixed = 0
        var notParsed = 0

        var maxSeenDate = effectiveSince

        val sdf = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.ENGLISH)

        try {
            while (cursor.moveToNext()) {
                total++

                val body = cursor.getString(bodyIdx).orEmpty()
                val address = cursor.getString(addrIdx).orEmpty()
                val smsReceivedMillis = cursor.getLong(dateIdx)

                if (smsReceivedMillis > maxSeenDate) maxSeenDate = smsReceivedMillis

                val parsed = SmsPatternRegistry.parse(body, address, smsReceivedMillis)
                if (parsed == null) {
                    notParsed++
                    // ✅ IMPORTANT: show why it is skipping
                    if (
                        body.contains("autopay", ignoreCase = true) ||
                        body.contains("safe gold", ignoreCase = true) ||
                        body.contains("debited with", ignoreCase = true)
                    ) {
                        Log.w(
                            "SMS_NOT_PARSED",
                            "Skipped (no pattern matched). from=$address date=${sdf.format(smsReceivedMillis)} body=${body.take(220)}"
                        )
                    }
                    continue
                }

                val amount = parsed.amount ?: continue
                parsedCount++

                val rawName = parsed.name?.trim()
                val refDigits = digits(parsed.ref)

                val myAcc4 =
                    last4(parsed.accNo) ?: extractMyAccLast4(body)?.let { last4(it) }

                val merchantFromBody = extractMerchantAccLast4(body)?.let { last4(it) }
                val merchantFromParsed = last4(parsed.merchantAcc)
                val merchant4 = merchantFromBody ?: merchantFromParsed

                val hasRealName = isRealName(rawName)

                var finalName: String
                if (hasRealName) {
                    finalName = rawName!!.trim()
                } else {
                    finalName =
                        if (!merchant4.isNullOrBlank()) "Unknown-$merchant4"
                        else "Unknown"

                    if (!refDigits.isNullOrBlank()) {
                        val mapped = mappingDao.getNameByKey(MappingKeys.refKey(refDigits))
                        if (!mapped.isNullOrBlank()) {
                            finalName = mapped
                        }
                    }

                    if (finalName == "Unknown" && !merchant4.isNullOrBlank()) {
                        val mapped = mappingDao.getNameByKey(MappingKeys.accKey(merchant4))
                        if (!mapped.isNullOrBlank()) {
                            finalName = mapped
                        }
                    }

                    if (finalName == "Unknown" && !merchant4.isNullOrBlank()) {
                        val known = dao.findKnownNameByMerchantAcc(merchant4)
                        if (!known.isNullOrBlank()) {
                            finalName = known
                        }
                    }
                }

                val category =
                    if (finalName.isNotBlank() && !finalName.startsWith("unknown", ignoreCase = true))
                        com.example.expensereader.ml.CategoryResolver.resolve(context, finalName)
                    else "Others"

                Log.d(
                    "SMS_READ",
                    "SELF_ACC=$myAcc4 | MERCHANT_ACC=$merchant4 | AMOUNT=$amount | DATE=${sdf.format(smsReceivedMillis)} | REF=$refDigits | NAME=$finalName | CAT=$category"
                )

                val refKey = refDigits ?: ""
                val isDup = dao.existsSms(refNo = refKey, date = smsReceivedMillis, amount = amount) > 0
                if (isDup) {
                    val updated = dao.updateAccsForExistingSms(
                        refNo = refKey,
                        date = smsReceivedMillis,
                        amount = amount,
                        myAcc4 = myAcc4,
                        merchant4 = merchant4
                    )

                    if (updated > 0) {
                        dupRepaired++
                        Log.w(
                            "SMS_DUP_REPAIR",
                            "Repaired existing SMS → REF=$refKey DATE=${sdf.format(smsReceivedMillis)} AMT=$amount self=$myAcc4 merchant=$merchant4"
                        )
                    }

                    val fixed = dao.updateNameCategoryForExistingSms(
                        refNo = refKey,
                        date = smsReceivedMillis,
                        amount = amount,
                        newName = finalName,
                        newCategory = category
                    )
                    if (fixed > 0) {
                        dupCatFixed++
                        Log.w(
                            "SMS_DUP_FIX",
                            "Updated existing row -> REF=$refKey DATE=${sdf.format(smsReceivedMillis)} AMT=$amount name='$finalName' cat='$category'"
                        )
                    }

                    dupSkipped++
                    continue
                }

                if (finalName.isNotBlank() &&
                    !finalName.startsWith("Unknown", ignoreCase = true) &&
                    !merchant4.isNullOrBlank()
                ) {
                    dao.updateByMerchantAccIfUnknown(
                        merchantAcc = merchant4,
                        newName = finalName,
                        newCategory = autoCategory(context, finalName)
                    )
                }

                val expense = Expense(
                    id = 0L,
                    name = finalName,
                    amount = amount,
                    date = smsReceivedMillis,
                    category = category,
                    type = parsed.type ?: "DEBIT",
                    source = "SMS",
                    accNo = myAcc4,
                    merchantAcc = merchant4,
                    upiRef = refDigits,
                    refNo = refDigits ?: "",
                    accountId = null,
                    userEdited = false,
                    needsStatementImport = false,
                    smsBody = body
                )

                dao.insert(expense)
                inserted++
            }
        } finally {
            cursor.close()
        }

        if (updateLastDate && maxSeenDate > getLastImportedDate(context)) {
            setLastImportedDate(context, maxSeenDate)
        }

        Log.d(
            TAG,
            "IMPORT (last 6 months enforced) effectiveSince=$effectiveSince total=$total parsed=$parsedCount notParsed=$notParsed inserted=$inserted dupSkipped=$dupSkipped dupRepaired=$dupRepaired dupCatFixed=$dupCatFixed lastSaved=${getLastImportedDate(context)}"
        )
    }

    private fun autoCategory(context: Context, name: String): String {
        if (name.trim().startsWith("unknown", ignoreCase = true)) return "Others"
        return com.example.expensereader.ml.CategoryResolver.resolve(context, name)
    }
}
