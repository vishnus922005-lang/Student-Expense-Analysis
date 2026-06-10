// SmsPattern.kt
package com.example.expensereader.sms

interface SmsPattern {
    fun tryParse(body: String, address: String, timestamp: Long): ParsedSms?
}
