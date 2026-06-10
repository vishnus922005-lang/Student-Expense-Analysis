package com.example.expensereader.sms

class IndianBankUpiPattern : SmsPattern {

    // Debit:
    // "A/c *6014 debited Rs. 6.00 on 04-12-25 to MTC CH LTDCH. UPI:115119902818."
    private val debitRegex = Regex(
        pattern = """A/c\s+[*X0-9]+\s+debited\s+Rs\.?\s*([\d.]+).*?\s+to\s+(.+?)\.\s+UPI""",
        option = RegexOption.IGNORE_CASE
    )

    override fun tryParse(body: String, address: String, timestamp: Long): ParsedSms? {
        val text = body.replace("\u00A0", " ")

        // Debit only
        debitRegex.find(text)?.let { m ->
            val amount = m.groupValues[1].toDoubleOrNull() ?: return null
            val merchant = m.groupValues[2].trim()

            return ParsedSms(
                name = merchant,
                amount = amount,
                dateMillis = timestamp,
                type = "DEBIT",
                rawText = body
            )
        }

        return null
    }
}
