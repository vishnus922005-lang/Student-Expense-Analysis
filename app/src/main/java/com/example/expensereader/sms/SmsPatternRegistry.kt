package com.example.expensereader.sms

object SmsPatternRegistry {

    private val patterns: List<SmsPattern> = listOf(
        HdfcPattern(),
        IciciPattern(),
        CubPattern(),
        SbiUpiPattern(),
        CanaraBankPattern(),
        IndianBankUpiPattern(),
        UpiAutopayPattern()
    )

    fun parse(body: String, address: String, timestamp: Long): ParsedSms? {
        for (p in patterns) {
            val result = p.tryParse(body, address, timestamp)
            if (result != null) return result
        }
        return null
    }
}
