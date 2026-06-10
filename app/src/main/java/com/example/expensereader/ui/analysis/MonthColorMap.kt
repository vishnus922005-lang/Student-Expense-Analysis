package com.example.expensereader.ui.analysis

import android.graphics.Color
import java.util.Locale

object MonthColorMap {

    private val map = mapOf(
        "jan" to Color.parseColor("#4F46E5"),
        "feb" to Color.parseColor("#06B6D4"),
        "mar" to Color.parseColor("#10B981"),
        "apr" to Color.parseColor("#F59E0B"),
        "may" to Color.parseColor("#EF4444"),
        "jun" to Color.parseColor("#8B5CF6"),
        "jul" to Color.parseColor("#14B8A6"),
        "aug" to Color.parseColor("#22C55E"),
        "sep" to Color.parseColor("#F97316"),
        "oct" to Color.parseColor("#EC4899"),
        "nov" to Color.parseColor("#0EA5E9"),
        "dec" to Color.parseColor("#64748B"),
        "other months" to Color.parseColor("#9CA3AF")
    )

    fun colorFor(label: String): Int {
        val key = label.trim().lowercase(Locale.getDefault())
        return map[key] ?: Color.parseColor("#9CA3AF")
    }
}
