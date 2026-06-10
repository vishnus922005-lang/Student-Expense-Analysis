package com.example.expensereader.repo

import com.example.expensereader.db.ExpenseDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class InsightRepository(
    private val dao: ExpenseDao
) {

    suspend fun generateSmartTips(): List<String> =
        withContext(Dispatchers.IO) {

            val tips = mutableListOf<String>()

            val now = Calendar.getInstance()
            val end = now.timeInMillis

            now.add(Calendar.DAY_OF_YEAR, -7)
            val lastWeekStart = now.timeInMillis

            now.add(Calendar.DAY_OF_YEAR, -7)
            val previousWeekStart = now.timeInMillis

            val thisWeekTotal =
                dao.getTotalBetween(lastWeekStart, end) ?: 0.0

            val lastWeekTotal =
                dao.getTotalBetween(previousWeekStart, lastWeekStart) ?: 0.0

            val monthlyTotal =
                dao.getThisMonthTotal() ?: 0.0

            val categoryTotals =
                dao.getCategoryTotals()

            val foodTotal = categoryTotals
                .find { it.name.equals("Food", true) }
                ?.total ?: 0.0

            if (monthlyTotal > 0) {
                val percent = (foodTotal / monthlyTotal) * 100
                if (percent > 40) {
                    tips.add(
                        "⚠️ You are spending %.1f%% on Food. Try reducing restaurant visits."
                            .format(percent)
                    )
                }
            }

            if (lastWeekTotal > 0) {
                val change =
                    ((thisWeekTotal - lastWeekTotal) / lastWeekTotal) * 100

                if (change > 15) {
                    tips.add(
                        "📈 Your spending increased by %.1f%% compared to last week."
                            .format(change)
                    )
                }
            }

            if (monthlyTotal > 10000) {
                tips.add(
                    "💡 High monthly spending detected. Consider setting a budget."
                )
            }

            if (tips.isEmpty()) {
                tips.add("✅ Your spending is under control. Great job!")
            }

            tips
        }
}
