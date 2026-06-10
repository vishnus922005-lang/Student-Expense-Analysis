// File: app/src/main/java/com/example/expensereader/ui/analysis/AnalysisBudgetHeaderBinder.kt
package com.example.expensereader.ui.analysis

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.LifecycleCoroutineScope
import com.example.expensereader.R
import com.example.expensereader.util.BudgetManager
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max

class AnalysisBudgetHeaderBinder(
    private val root: View,
    private val scope: LifecycleCoroutineScope
) {
    private val tvTitle: TextView = root.findViewById(R.id.tvBudgetTitle)
    private val tvBadge: TextView = root.findViewById(R.id.tvBudgetBadge)

    private val tvMyBudget: TextView = root.findViewById(R.id.tvMyBudget)
    private val tvPerDayBudget: TextView = root.findViewById(R.id.tvPerDayBudget)

    fun bind() {
        // title text you want in analysis
        tvTitle.text = "Daily Budget"
        refresh()
    }

    fun refresh() {
        scope.launch {
            val ctx = root.context

            val blue = ContextCompat.getColor(ctx, R.color.cobalt_blue)
            val red = ContextCompat.getColor(ctx, android.R.color.holo_red_dark)

            val enabled = BudgetManager.isEnabled(ctx)
            val mode = BudgetManager.getMode(ctx)

            if (!enabled) {
                setBadgeUi("OFF", blue)
                tvMyBudget.text = " "
                tvPerDayBudget.text = " "
                return@launch
            }

            // ✅ weekly/monthly: compute dynamic per-day from remaining overall / remaining days
            val perDay: Double =
                if (mode == BudgetManager.MODE_WEEKLY || mode == BudgetManager.MODE_MONTHLY) {
                    val overallLimit = BudgetManager.getOverallLimit(ctx)
                    val overallSpent = BudgetManager.getOverallSpent(ctx)
                    val remainingOverall = (overallLimit - overallSpent).coerceAtLeast(0.0)

                    val days =
                        if (mode == BudgetManager.MODE_WEEKLY) remainingDaysInThisWeekIncludingToday()
                        else remainingDaysInThisMonthIncludingToday()

                    val dynamic = if (days > 0) remainingOverall / days else 0.0
                    // keep BudgetManager daily updated (same behavior as budget screen)
                    BudgetManager.setDailyLimit(ctx, dynamic, false)
                    dynamic
                } else {
                    BudgetManager.getDailyLimit(ctx)
                }

            val myBudget = BudgetManager.getMyBudget(ctx)

            val hasBudget =
                (perDay > 0.0) ||
                    (BudgetManager.getWeeklyLimit(ctx) > 0.0) ||
                    (BudgetManager.getMonthlyLimit(ctx) > 0.0)

            tvMyBudget.text = "₹${myBudget.toInt()}"
            tvPerDayBudget.text =
                if (hasBudget) "₹${perDay.toInt()}"
                else "-"

            if (!hasBudget) {
                setBadgeUi("SET", blue)
                return@launch
            }

            val todaySpent = BudgetManager.getTodaySpend(ctx)
            val dailyOver = todaySpent > perDay

            setBadgeUi(if (dailyOver) "OVER" else "SAFE", if (dailyOver) red else blue)

            // optional: if you want to show more info in badge text (not required)
            // tvBadge.text = if (dailyOver) "OVER ₹${abs(todaySpent - perDay).toInt()}" else "SAFE"
        }
    }

    private fun setBadgeUi(text: String, bgColor: Int) {
        tvBadge.text = text
        ViewCompat.setBackgroundTintList(tvBadge, ColorStateList.valueOf(bgColor))
    }

    private fun remainingDaysInThisWeekIncludingToday(): Int {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY

        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val mondayIndex = Calendar.MONDAY
        val todayIndex = if (dow == Calendar.SUNDAY) 6 else (dow - mondayIndex)

        return (7 - todayIndex).coerceIn(1, 7)
    }

    private fun remainingDaysInThisMonthIncludingToday(): Int {
        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_MONTH)
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return (maxDay - today + 1).coerceAtLeast(1)
    }
}
