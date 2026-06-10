package com.example.expensereader.sms

class HdfcPattern : SmsPattern {

    // Example: "Your HDFC Bank card ending 1234 spent INR 456.78 at SWIGGY on 18-12-25"
    private val regex = Regex(
        pattern = """spent\s+INR\s+(\d+\.?\d*)\s+.*\s+at\s+(.+?)\b""",
        option = RegexOption.IGNORE_CASE
    )

    override fun tryParse(body: String, address: String, timestamp: Long): ParsedSms? {
        val match = regex.find(body) ?: return null
        val amount = match.groupValues[1].toDoubleOrNull() ?: return null
        val merchant = match.groupValues[2].trim()

        return ParsedSms(
            name = merchant,
            amount = amount,
            dateMillis = timestamp,
            type = "DEBIT",
            rawText = body
        )
    }
}
