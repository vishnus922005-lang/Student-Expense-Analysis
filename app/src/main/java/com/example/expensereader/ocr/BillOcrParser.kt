package com.example.expensereader.ocr

import android.util.Log

object BillOcrParser {

    private const val TAG = "BILL_PARSER"

    data class Result(
        val merchant: String?,
        val amount: Double?
    )

    fun parse(rawText: String): Result {

        Log.d(TAG, "======= PARSER INPUT =======")
        rawText.lines().forEachIndexed { idx, line ->
            Log.d(TAG, "ROW ${idx + 1} -> '$line'")
        }

        val lines = rawText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        // --------------------------------------------------
        // 1️⃣ MERCHANT NAME (TOP ONLY) - UNCHANGED
        // --------------------------------------------------
        val merchant = lines
            .take(8)
            .firstOrNull { line ->
                val l = line.lowercase()
                l.length >= 4 &&
                        !l.contains("invoice") &&
                        !l.contains("tax") &&
                        !l.contains("gst") &&
                        !l.contains("date") &&
                        !l.contains("phone") &&
                        !l.contains("email") &&
                        !l.contains("billed") &&
                        !l.contains("hsn") &&
                        !l.contains("rate") &&
                        !l.any { it.isDigit() }
            }

        Log.d(TAG, "Merchant = $merchant")

        // --------------------------------------------------
        // 2️⃣ AMOUNT DETECTION (PRIORITY + NEXT LINE SUPPORT)
        // --------------------------------------------------
        data class AmountCandidate(
            val amount: Double,
            val priority: Int
        )

        val amountCandidates = mutableListOf<AmountCandidate>()

        fun addCandidate(priority: Int, value: Double, label: String, atIndex: Int) {
            Log.d(TAG, "Found $label at row ${atIndex + 1}: $value (priority=$priority)")
            amountCandidates.add(AmountCandidate(value, priority))
        }

        for (i in lines.indices) {
            val line = lines[i]
            val lower = line.lowercase()

            // ❌ IGNORE PHONE / GST / RATE / HSN / PERCENT LINES
            if (
                lower.contains("phone") ||
                lower.contains("gstin") ||
                lower.contains("hsn") ||
                lower.contains("rate") ||
                lower.contains("%")
            ) continue

            // helper: get money from current or next line
            fun moneyHereOrNext(): Double? {
                extractMoney(line)?.let { return it }

                if (i + 1 < lines.size) {
                    val next = lines[i + 1]
                    val nextLower = next.lowercase()

                    if (
                        nextLower.contains("gstin") ||
                        nextLower.contains("phone") ||
                        nextLower.contains("hsn") ||
                        nextLower.contains("rate") ||
                        nextLower.contains("%")
                    ) return null

                    extractMoney(next)?.let {
                        Log.d(TAG, "Amount is on NEXT row ${i + 2} for keyword row ${i + 1}")
                        return it
                    }
                }
                return null
            }

            when {
                lower.contains("grand total") -> {
                    val n = moneyHereOrNext() ?: continue
                    addCandidate(100, n, "GRAND TOTAL", i)
                }

                lower.contains("total amount after tax") -> {
                    val n = moneyHereOrNext() ?: continue
                    addCandidate(80, n, "TOTAL AFTER TAX", i)
                }

                lower.contains("amount after tax") -> {
                    val n = moneyHereOrNext() ?: continue
                    addCandidate(70, n, "AMOUNT AFTER TAX", i)
                }

                lower.contains("total") -> {
                    val n = moneyHereOrNext() ?: continue
                    addCandidate(50, n, "TOTAL", i)
                }
            }
        }

        // ✅ Pick best from keyword candidates
        // ✅ CHANGE ONLY HERE: val -> var (so fallback can assign)
        var finalAmount: Double? = amountCandidates
        .withIndex()
        .maxWithOrNull(
            compareBy<IndexedValue<AmountCandidate>> { it.value.priority }
                .thenBy { it.index }   // 👈 later rows win
        )
        ?.value
        ?.amount

        // --------------------------------------------------
        // ✅ FALLBACK FOR TICKETS (NO "TOTAL" KEYWORDS)
        // pick last currency-looking amount from bottom
        // --------------------------------------------------
        if (finalAmount == null) {
            Log.d(TAG, "No keyword total found. Using bottom-up fallback...")

            for (i in lines.size - 1 downTo 0) {
                val line = lines[i]
                val lower = line.lowercase()

                // skip common non-amount text on tickets
                if (
                    lower.contains("ticket") ||
                    lower.contains("passenger") ||
                    lower.contains("seater") ||
                    lower.contains("mode of pay") ||
                    lower.contains("general") ||
                    lower.matches(Regex("""\d{4}-\d{2}-\d{2}""")) ||   // date
                    lower.matches(Regex("""\d{1,2}:\d{2}(:\d{2})?""")) // time
                ) continue

                val hasCurrency = lower.contains("rs") || lower.contains("inr") || line.contains("₹")

                val n = extractMoney(line)
                if (n != null && n in 1.0..100_000.0 && hasCurrency) {
                    Log.d(TAG, "Fallback picked amount from row ${i + 1}: $n (line='$line')")
                    finalAmount = n
                    break
                }
            }

            // last resort: pick any last numeric amount
            if (finalAmount == null) {
                for (i in lines.size - 1 downTo 0) {
                    val n = extractMoney(lines[i])
                    if (n != null && n in 1.0..100_000.0) {
                        Log.d(TAG, "Fallback (no currency) picked amount from row ${i + 1}: $n")
                        finalAmount = n
                        break
                    }
                }
            }
        }

        Log.d(TAG, "Final Amount = $finalAmount")

        return Result(
            merchant = merchant,
            amount = finalAmount
        )
    }

    // --------------------------------------------------
    // MONEY EXTRACTOR (SAFE + Rs variants + ₹ misread fix)
    // --------------------------------------------------
    private fun extractMoney(line: String): Double? {

        val normalized = line
            .replace("\u00A0", " ")
            .replace(Regex("""R\s*s""", RegexOption.IGNORE_CASE), "Rs")  // "R s" -> "Rs"
            .replace(Regex("""R\s*5""", RegexOption.IGNORE_CASE), "Rs")  // "R5" -> "Rs"
            .replace(Regex("""R\s*\$""", RegexOption.IGNORE_CASE), "Rs") // "R$" -> "Rs"
            .replace(Regex("""\s+"""), " ")
            .trim()

        // Prefer currency marker (₹ / Rs / INR)
        val currencyRegex = Regex(
            """(?i)(?:₹|rs\.?|inr)\s*[:\-]?\s*(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?)"""
        )

        // Fallback: ": 245.00"
        val colonAmountRegex = Regex(
            """:\s*(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?)"""
        )

        // Fallback: any number
        val plainNumberRegex = Regex(
            """(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?)"""
        )

        fun toDouble(group: String): Double? =
            group.replace(",", "").toDoubleOrNull()

        // 1) currency-based
        currencyRegex.find(normalized)?.groupValues?.getOrNull(1)?.let { g ->
            val token = g
            val value = toDouble(token) ?: return null
            val fixed = fixLeadingDigitOcr(token) ?: value
            if (fixed > 100_000) return null
            return fixed
        }

        // 2) colon-based
        colonAmountRegex.find(normalized)?.groupValues?.getOrNull(1)?.let { g ->
            val token = g
            val value = toDouble(token) ?: return null
            val fixed = fixLeadingDigitOcr(token) ?: value
            if (fixed > 100_000) return null
            return fixed
        }

        // 3) any number
        plainNumberRegex.find(normalized)?.groupValues?.getOrNull(1)?.let { g ->
            val token = g
            val value = toDouble(token) ?: return null
            val fixed = fixLeadingDigitOcr(token) ?: value
            if (fixed > 100_000) return null
            return fixed
        }

        return null
    }

    private fun fixLeadingDigitOcr(token: String): Double? {
        // "24,490.00" -> "4,490.00" (₹ misread as leading '2')
        val m = Regex("""^(\d)(\d),(\d{3}(?:\.\d{1,2})?)$""").find(token) ?: return null
        val corrected = "${m.groupValues[2]},${m.groupValues[3]}"
        val correctedValue = corrected.replace(",", "").toDoubleOrNull() ?: return null
        return if (correctedValue in 1.0..9999.99) correctedValue else null
    }
}
