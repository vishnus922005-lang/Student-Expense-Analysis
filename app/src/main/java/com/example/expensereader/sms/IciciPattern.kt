package com.example.expensereader.sms

class IciciPattern : SmsPattern {

    // Example: "INR 456.78 has been debited from your A/C at AMAZON on 18-12-25"
    private val regex = Regex(
        pattern = """INR\s+(\d+\.?\d*).*(?:debited|spent).*(?:at|in)\s+(.+?)\b""",
        option = RegexOption.IGNORE_CASE
    )

    override fun tryParse(body: String, address: String, timestamp: Long): ParsedSms? {
        val match = regex.find(body) ?: return null
        val amount = match.groupValues[1].toDoubleOrNull() ?: return null
        val merchant = match.groupValues[2]
            .trim()
            .trimEnd('.', '!', ',')

        return ParsedSms(
            name = merchant,
            amount = amount,
            dateMillis = timestamp,
            type = "DEBIT",
            rawText = body
        )
    }
}
