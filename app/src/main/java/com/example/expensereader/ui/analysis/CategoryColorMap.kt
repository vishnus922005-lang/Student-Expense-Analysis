package com.example.expensereader.ui.analysis

import android.graphics.Color
import java.util.Locale

object CategoryColorMap {

    // ✅ One source of truth: category -> color
    private val map: Map<String, Int> = mapOf(
        "food" to Color.parseColor("#1E88E5"),
        "shopping" to Color.parseColor("#43A047"),
        "travel" to Color.parseColor("#FB8C00"),
        "groceries" to Color.parseColor("#8E24AA"),
        "bills & utilities" to Color.parseColor("#E53935"),
        "entertainment" to Color.parseColor("#00ACC1"),
        "rent/hostel" to Color.parseColor("#FDD835"),
        "education" to Color.parseColor("#6D4C41"),
        "health medicine & personal care" to Color.parseColor("#3949AB"),
        "savings" to Color.parseColor("#00897B"),
        "friends & family" to Color.parseColor("#F4511E"),
        "others" to Color.parseColor("#37687b"),
        
    )

    fun colorFor(category: String): Int {
        val c = category.trim().lowercase(Locale.ROOT)

        // Normalize your health variations
        val normalized = when {
            c.contains("health") -> "health medicine & personal care"
            c.contains("medicine") && c.contains("personal") -> "health medicine & personal care"
            else -> c
        }

        return map[normalized] ?: map.getValue("others")
    }
}
