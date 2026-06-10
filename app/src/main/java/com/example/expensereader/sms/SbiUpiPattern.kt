package com.example.expensereader.sms

class SbiUpiPattern : SmsPattern {

    // Debit: "A/C X4211 debited by 95.0 on date 27Sep25 trf to Mr SHEIK  MOHAMM Refno ..."
    private val debitRegex = Regex(
        pattern = """A/C\s+([A-Z0-9]+).*?debited\s+by\s+([\d.]+).*?trf\s+to\s+(.+?)\s+Refno""",
        option = RegexOption.IGNORE_CASE
    )

    override fun tryParse(body: String, address: String, timestamp: Long): ParsedSms? {
        val text = body.replace("\u00A0", " ") // normalize non‑breaking spaces

        // Debit only
        debitRegex.find(text)?.let { m ->
            val amount = m.groupValues[2].toDoubleOrNull() ?: return null
            val counterparty = m.groupValues[3].trim()

            return ParsedSms(
                name = counterparty,
                amount = amount,
                dateMillis = timestamp,
                type = "DEBIT",
                rawText = body
            )
        }

        return null
    }
}
