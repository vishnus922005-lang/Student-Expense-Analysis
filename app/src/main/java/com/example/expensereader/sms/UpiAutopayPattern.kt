// File: app/src/main/java/com/example/expensereader/sms/UpiAutopayPattern.kt
package com.example.expensereader.sms

class UpiAutopayPattern : SmsPattern {

    override fun tryParse(body: String, address: String, timestamp: Long): ParsedSms? {
        val text = body.trim()

        // must contain these signals
        if (!text.contains("autopay", ignoreCase = true)) return null
        if (!text.contains("debited", ignoreCase = true)) return null

        // Amount: "debited with 20.00" OR "debited with Rs. 20.00"
        val amt = Regex(
            """debited\s+with\s+(?:rs\.?\s*)?([0-9]+(?:\.[0-9]+)?)""",
            RegexOption.IGNORE_CASE
        ).find(text)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null

        // Name: "towards SAFE GOLD UPI Autopay" (grab SAFE GOLD)
        val name = Regex(
            """towards\s+(.+?)(?:\s+upi\s+autopay|,|\s+-)""",
            RegexOption.IGNORE_CASE
        ).find(text)?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotBlank() }

        // UPI id like "5cc8...@ybl"
        val upiId = Regex(
            """([a-z0-9]{6,}@[a-z]{2,})""",
            RegexOption.IGNORE_CASE
        ).find(text)?.groupValues?.get(1)

        return ParsedSms(
            amount = amt,
            name = name ?: "Autopay",
            ref = upiId,
            accNo = null,
            merchantAcc = null,
            type = "DEBIT",
            dateMillis = timestamp   // ✅ FIX: required by your ParsedSms
        )
    }
}
