package com.example.expensereader.ml

import kotlin.math.abs

object SmartInsightEngine {

    fun generateInsights(
        total: Double,
        categoryMap: Map<String, Double>,
        lastWeekTotal: Double,
        thisWeekTotal: Double,
        monthlyBudget: Double,
        dailyLimit: Double
    ): List<InsightModel> {

        val insights = mutableListOf<InsightModel>()

        if (total <= 0) {
            insights.add(
                InsightModel(
                    "No Spending Detected",
                    "Great discipline! No expenses recorded.",
                    InsightSeverity.GOOD,
                    0
                )
            )
            return insights
        }

        // -------- CATEGORY DOMINANCE --------
        categoryMap.forEach { (category, amount) ->

            val percent = (amount / total) * 100

            if (percent > 45) {
                val extra = ((percent - 30) / 100) * total
                insights.add(
                    InsightModel(
                        "$category Dominating",
                        "$category accounts for ${percent.toInt()}% of your spending. Reducing it could save ₹${extra.toInt()}.",
                        InsightSeverity.CRITICAL,
                        80
                    )
                )
            } else if (percent > 35) {
                insights.add(
                    InsightModel(
                        "$category High Usage",
                        "$category forms ${percent.toInt()}% of expenses. Consider optimizing it.",
                        InsightSeverity.WARNING,
                        50
                    )
                )
            }
        }

        // -------- WEEKLY TREND --------
        if (lastWeekTotal > 0) {
            val change = ((thisWeekTotal - lastWeekTotal) / lastWeekTotal) * 100

            if (change > 20) {
                insights.add(
                    InsightModel(
                        "Spending Surge",
                        "Your spending increased by ${change.toInt()}% compared to last week.",
                        InsightSeverity.CRITICAL,
                        90
                    )
                )
            } else if (change > 10) {
                insights.add(
                    InsightModel(
                        "Spending Increased",
                        "Expenses rose by ${change.toInt()}% this week.",
                        InsightSeverity.WARNING,
                        60
                    )
                )
            } else if (change < -10) {
                insights.add(
                    InsightModel(
                        "Spending Reduced",
                        "Good job! You reduced spending by ${abs(change).toInt()}%.",
                        InsightSeverity.GOOD,
                        20
                    )
                )
            }
        }

        // -------- BUDGET CHECK --------
        if (monthlyBudget > 0 && total > monthlyBudget) {
            insights.add(
                InsightModel(
                    "Budget Exceeded",
                    "You exceeded your monthly budget by ₹${(total - monthlyBudget).toInt()}.",
                    InsightSeverity.CRITICAL,
                    95
                )
            )
        }

        // -------- DAILY LIMIT EFFICIENCY --------
        if (dailyLimit > 0) {
            val avgPerDay = thisWeekTotal / 7
            if (avgPerDay > dailyLimit) {
                insights.add(
                    InsightModel(
                        "Daily Limit Crossed",
                        "Your average daily spend ₹${avgPerDay.toInt()} exceeds limit ₹${dailyLimit.toInt()}.",
                        InsightSeverity.WARNING,
                        65
                    )
                )
            }
        }

        // -------- SORT BY PRIORITY SCORE --------
        return insights.sortedByDescending { it.score }
    }
}
