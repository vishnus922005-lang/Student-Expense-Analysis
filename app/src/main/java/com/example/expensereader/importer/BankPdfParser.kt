package com.example.expensereader.importer

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.expensereader.db.AppDatabase
import com.example.expensereader.model.PdfMapping
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

object BankPdfParser {

    private const val TAG = "PDF_PARSE"

    private val dateFormats = listOf(
        SimpleDateFormat("dd MMM, yyyy", Locale.ENGLISH),
        SimpleDateFormat("ddMMM,yyyy", Locale.ENGLISH)
    )

    private fun digits(raw: String?): String? =
        raw?.replace(Regex("[^0-9]"), "")?.takeIf { it.isNotBlank() }

    private fun normRow(raw: String): String {
        return raw
            .replace("\u00A0", " ")                
            .replace(Regex("[\\t\\r\\n]+"), " ")    
            .replace(Regex("\\s+"), " ")            
            .trim()
    }
    
    private fun splitKnownAllCapsRuns(name: String): String {
        val n = name.trim()
        if (n.isBlank()) return n
        if (n.contains(" ")) return n 

        val letters = n.count { it.isLetter() }
        val upper = n.count { it.isUpperCase() }
        if (letters < 12) return n
        if (upper < (letters * 0.85)) return n 

        val chunks = listOf(
            "TRANSPORT", "CORPORATION", "CORP", "LIMITED", "LTD",
            "CHENNAI", "TAMILNADU", "TAMIL", "NADU",
            "PAY", "BANK", "INDIA", "SERVICE", "SERVICES",
            "ENGINEERING", "COLLEGE", "UNIVERSITY", "SCHOOL", "INSTITUTE",
            "SUPERMARKET", "MARKET", "MART", "BAKERY", "BAKKERY"
        )

        var out = n
        for (c in chunks) {
    
            out = out.replace(c, " $c ")
        }

        out = out.replace(Regex("\\s+"), " ").trim()
        
        return if (out.split(" ").size >= 2) out else n
    }

    private fun parseDateMillis(text: String): Long? {
        val t = text.trim()

        val m1 = Regex("""\b\d{2}\s+[A-Za-z]{3},\s*\d{4}\b""").find(t)?.value
        val m2 = Regex("""\b\d{2}[A-Za-z]{3},\d{4}\b""").find(t)?.value
        val dateStr = m1 ?: m2 ?: return null

        for (fmt in dateFormats) {
            try {
                val d = fmt.parse(dateStr)
                if (d != null) return d.time
            } catch (_: Throwable) { }
        }
        return null
    }

    private fun parseAmount(text: String): Double? {
        val amtStr = Regex("""₹\s*[\d,]+(?:\.\d+)?""").find(text)?.value ?: return null
        return amtStr
            .replace("₹", "")
            .replace(",", "")
            .trim()
            .toDoubleOrNull()
    }

    private fun parseName(text: String): String? {
        val t = normRow(text)

        val paidIdx = t.indexOf("Paid", ignoreCase = true)
        if (paidIdx < 0) return null

        val afterPaidTo = when {
            t.contains("Paid to", ignoreCase = true) ->
                t.substringAfter("Paid to", "")
            t.contains("Paidto", ignoreCase = true) ->
                t.substringAfter("Paidto", "")
            else -> ""
        }

        if (afterPaidTo.isBlank()) return null

        val beforeAmt = afterPaidTo.substringBefore("₹").trim()
        if (beforeAmt.isBlank()) return null

        val spacedCamel = beforeAmt.replace(Regex("([a-z])([A-Z])"), "$1 $2").trim()

        val cleaned = normRow(spacedCamel)

        val fixed = splitKnownAllCapsRuns(cleaned)

        return fixed.ifBlank { null }
    }

    private fun parseRef(row2: String): String? {
        val t = normRow(row2)
        val direct = Regex("""\b(\d{8,})\b""").find(t)?.groupValues?.getOrNull(1)
        return digits(direct ?: t)
    }

    private fun parseMyAccLast4(row3: String): String? {
        val t = normRow(row3)
        return Regex("""\b(\d{4})\b""").find(t)?.groupValues?.getOrNull(1)
    }

    private fun buildRefKey(refDigits: String) = MappingKeys.refKey(refDigits)

    suspend fun parse(context: Context, uri: Uri): List<PdfTxn> =
        withContext(Dispatchers.IO) {

            val db = AppDatabase.getInstance(context)
            val mappingDao = db.pdfMappingDao()
            val expenseDao = db.expenseDao()

            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext emptyList()

            val pdf = PdfDocument(PdfReader(input))

            val rows = mutableListOf<String>()
            for (page in 1..pdf.numberOfPages) {
                val text = PdfTextExtractor.getTextFromPage(pdf.getPage(page))

                rows += text.split("\n")
                    .map { normRow(it) }
                    .filter { it.isNotEmpty() }
            }
            pdf.close()

            Log.d(TAG, "PDF rows=${rows.size}")

            val result = mutableListOf<PdfTxn>()

            var i = 0
            while (i + 2 < rows.size) {

                val row1 = rows[i]
                val row2 = rows[i + 1]
                val row3 = rows[i + 2]

                val hasAmt = row1.contains("₹")
                val hasDate = Regex("""\d{2}\s*[A-Za-z]{3},\s*\d{4}""").containsMatchIn(row1)
                val hasId = row2.contains("ID", ignoreCase = true) || Regex("""\d{8,}""").containsMatchIn(row2)

                if (!(hasAmt && hasDate && hasId)) {
                    i++
                    continue
                }

                val dateMillis = parseDateMillis(row1) ?: 0L
                val name = parseName(row1) ?: "UNKNOWN"
                val amount = parseAmount(row1)

                val ref = parseRef(row2)
                val myAccLast4 = parseMyAccLast4(row3)

                if (amount == null || ref.isNullOrBlank()) {
                    Log.w(TAG, "SKIP i=$i amount/ref missing row1='$row1' row2='$row2'")
                    i++
                    continue
                }

                val txn = PdfTxn(
                    dateTimeMillis = dateMillis,
                    name = name,
                    amount = amount,
                    direction = "DEBIT",
                    accNo = myAccLast4,
                    ref = ref
                )

                result.add(txn)

                mappingDao.insertOrUpdate(
                    PdfMapping(
                        key = buildRefKey(ref),
                        name = name,          
                        accNo = myAccLast4,
                        upiRef = ref
                    )
                )

                if (dateMillis == 0L) {
                    Log.w(TAG, "WARN(ref=$ref) date_not_found -> dateMillis=0 row1='$row1'")
                }

                Log.d(TAG, "PARSED → i=$i ref=$ref name='$name' amount=$amount myAcc=$myAccLast4 dateMillis=$dateMillis")

                i += 3
            }

            expenseDao.backfillUnknownNamesFromPdfMappingByUpiRef()
            expenseDao.backfillUnknownNamesFromPdfMappingByAccNo()

            val updated = expenseDao.propagateResolvedNamesToSameMerchantAccSafe()
            Log.d(TAG, "SAFE_PROPAGATE updated=$updated")

            try {
                val sdf = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.ENGLISH)
                val matched = expenseDao.getMatchedSmsForLog()

                Log.d(TAG, "MATCHED_SMS total=${matched.size}")

                for (sms in matched) {
                    Log.d(
                        "MATCHED_SMS",
                        "ACC=${sms.merchantAcc} | NAME=${sms.name} | AMOUNT=${sms.amount} | DATE=${sdf.format(sms.date)}"
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "MATCHED_SMS log failed: ${t.message}", t)
            }

            Log.i(TAG, "Parsed ${result.size} transactions from PDF")
            result
        }
}
