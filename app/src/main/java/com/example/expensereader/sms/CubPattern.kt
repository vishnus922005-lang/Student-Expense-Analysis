// File: app/src/main/java/com/example/expensereader/sms/CubPattern.kt
package com.example.expensereader.sms

class CubPattern : SmsPattern {

    /*
      Example SMS (CUB):

      Your a/c no. XXXXXXXX0575 is debited for Rs.214.00 on 31-12-2025
      and credited to a/c no. XXXXXXXX7374 (UPI Ref no 116477514786)
    */

    // 1) YOUR account (debited / credited)
    private val myAccRegex = Regex(
        """Your\s+a/c\s+no\.?\s+[Xx*]*([0-9]{4})""",
        RegexOption.IGNORE_CASE
    )

    // 2) OTHER party account
    private val merchantAccRegex = Regex(
        """credited\s+to\s+a/c\s+no\.?\s+[Xx*]*([0-9]{4})""",
        RegexOption.IGNORE_CASE
    )

    // 3) Amount
    private val amountRegex =
        Regex("""Rs\.?\s*([\d,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)

    // 4) UPI Ref
    private val refRegex =
        Regex("""UPI\s+Ref\s+no\.?\s*([0-9]+)""", RegexOption.IGNORE_CASE)

    override fun tryParse(body: String, address: String, timestamp: Long): ParsedSms? {
        val text = body.replace("\u00A0", " ")

        val myAcc = myAccRegex.find(text)?.groupValues?.get(1) ?: return null
        val merchantAcc = merchantAccRegex.find(text)?.groupValues?.get(1) ?: return null
        val ref = refRegex.find(text)?.groupValues?.get(1) ?: return null

        val amtMatch = amountRegex.find(text) ?: return null
        val amount = amtMatch.groupValues[1].replace(",", "").toDouble()

        return ParsedSms(
            name = "",                     // resolved later
            amount = amount,
            dateMillis = timestamp,
            type = "DEBIT",
            rawText = body,

            // ✅ CORRECT FIELD ASSIGNMENT
            accNo = myAcc,                 // 0575 (YOUR account)
            merchantAcc = merchantAcc,     // 7374 (OTHER party)
            ref = ref
        )
    }
}
