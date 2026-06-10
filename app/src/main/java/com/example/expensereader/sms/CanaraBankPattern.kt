package com.example.expensereader.sms

class CanaraBankPattern : SmsPattern {

    // Debit:
    // "An amount of INR 10.00 has been DEBITED to your account XXX445 on 09/12/2025..."
    private val debitRegex = Regex(
        pattern = """INR\s+([\d.,]+)\s+has\s+been\s+DEBITED\s+to\s+your\s+account\s+([A-Z0-9]+)""",
        option = RegexOption.IGNORE_CASE
    )

    override fun tryParse(body: String, address: String, timestamp: Long): ParsedSms? {
        val text = body.replace("\u00A0", " ")

        // Debit only
        debitRegex.find(text)?.let { m ->
            val rawAmt = m.groupValues[1].replace(",", "")
            val amount = rawAmt.toDoubleOrNull() ?: return null
            val account = m.groupValues[2].trim()

            return ParsedSms(
                name = "Canara A/c $account",
                amount = amount,
                dateMillis = timestamp,
                type = "DEBIT",
                rawText = body
            )
        }

        return null
    }
}
