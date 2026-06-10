package com.example.expensereader.ui.budget

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.LifecycleCoroutineScope
import com.example.expensereader.R
import com.example.expensereader.model.BudgetDayTotal
import com.example.expensereader.util.BudgetManager
import com.google.android.material.button.MaterialButton
import com.github.mikephil.charting.components.XAxis
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.max
import com.example.expensereader.db.LatestExpenseRow
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter



class BudgetCardBinder(
    private val root: View,
    private val scope: LifecycleCoroutineScope
) {
    private val tvTitle: TextView = root.findViewById(R.id.tvBudgetTitle)
    private val tvBadge: TextView = root.findViewById(R.id.tvBudgetBadge)

    private val tvMyBudget: TextView = root.findViewById(R.id.tvMyBudget)
    private val tvPerDayBudget: TextView = root.findViewById(R.id.tvPerDayBudget)

    private val tvLeft: TextView = root.findViewById(R.id.tvBudgetLeft)
    private val tvSpent: TextView = root.findViewById(R.id.tvBudgetSpent)
    private val tvDailySuggestion: TextView = root.findViewById(R.id.tvDailySuggestion)

    private val progressDaily: ProgressBar = root.findViewById(R.id.budgetProgressDaily)

    // overall section
    private val overallContainer: View = root.findViewById(R.id.overallContainer)
    private val tvOverallTitle: TextView = root.findViewById(R.id.tvOverallTitle)
    private val tvOverallLeft: TextView = root.findViewById(R.id.tvOverallLeft)
    private val tvOverallSpent: TextView = root.findViewById(R.id.tvOverallSpent)
    private val progressOverall: ProgressBar = root.findViewById(R.id.budgetProgressOverall)

    private val btnSet: MaterialButton = root.findViewById(R.id.btnSetDailyLimit)
    private val btnDisable: MaterialButton = root.findViewById(R.id.btnDisableLimit)

    // ✅ Chart toggle + chart
    private val ivChartToggle: ImageView = root.findViewById(R.id.ivChartToggle)
    private val chartContainer: View = root.findViewById(R.id.chartContainer)
    private val barChart: BarChart = root.findViewById(R.id.budgetLineChart)

   


    // ✅ Skip UI (below chart)
    
    private var chartExpanded = false
    private var chartInited = false

    private var lastDailyProgress = 0
    private var lastOverallProgress = 0
    private var lastDailyTint: Int? = null
    private var lastOverallTint: Int? = null

    fun bind() {
        btnSet.setOnClickListener { showSetLimitDialog() }
        btnDisable.setOnClickListener {
            val ctx = root.context
            BudgetManager.setEnabled(ctx, false)
            BudgetManager.clearBudgetStart(ctx)
            refresh()
        }

        // ✅ Toggle chart
        ivChartToggle.setOnClickListener { toggleChart() }

        if (!chartInited) {
            setupBarChart()
            chartInited = true
        }

        
        refresh()
    }

    fun refresh() {
        scope.launch {
            val ctx = root.context
            val enabled = BudgetManager.isEnabled(ctx)
            val mode: String = BudgetManager.getMode(ctx)

            val blue = ContextCompat.getColor(ctx, R.color.cobalt_blue)
            val red = ContextCompat.getColor(ctx, android.R.color.holo_red_dark)

            // ✅ helper: show/hide chart properly
            fun setChartVisible(show: Boolean) {
                ivChartToggle.visibility = if (show) View.VISIBLE else View.GONE
                if (!show) {
                    chartExpanded = false
                    chartContainer.visibility = View.GONE
                    ivChartToggle.setImageResource(R.drawable.ic_expand_more)
                }
            }

            // ---------------- OFF STATE ----------------
            if (!enabled) {
                setBadgeUi(text = "OFF", bgColor = blue)

                tvMyBudget.text = "My Budget: -"
                tvPerDayBudget.text = "Per day limit: -"

                tvLeft.text = "Left: -"
                tvSpent.text = "Spent: -"
                tvDailySuggestion.text = "Budget limit disabled."

                overallContainer.visibility = View.GONE

                animateProgress(progressDaily, from = lastDailyProgress, to = 0) { lastDailyProgress = 0 }
                animateTint(progressDaily, from = lastDailyTint ?: blue, to = blue) { lastDailyTint = blue }

                // ✅ hide chart completely
                setChartVisible(false)
                return@launch
            }

            // ✅ For weekly/monthly recompute per-day
            if (mode == BudgetManager.MODE_WEEKLY || mode == BudgetManager.MODE_MONTHLY) {
                recomputePerDayForWeeklyMonthly(ctx, mode)

                val after = BudgetManager.getDailyLimit(ctx)
                if (after <= 0.0) {
                    val overallLimit = BudgetManager.getOverallLimit(ctx)
                    val days =
                        if (mode == BudgetManager.MODE_WEEKLY) remainingDaysInThisWeekIncludingToday()
                        else remainingDaysInThisMonthIncludingToday()

                    val perDayFallback = if (days > 0) overallLimit / days else 0.0
                    BudgetManager.setDailyLimit(ctx, perDayFallback, false)
                }
            }

            // -------- Daily Section (ALWAYS) --------
            val myBudget = BudgetManager.getMyBudget(ctx)

            val perDay: Double =
                if (mode == BudgetManager.MODE_WEEKLY || mode == BudgetManager.MODE_MONTHLY) {
                    val overallLimit = BudgetManager.getOverallLimit(ctx)
                    val overallSpent = BudgetManager.getOverallSpent(ctx)
                    val remainingOverall = (overallLimit - overallSpent).coerceAtLeast(0.0)

                    val days =
                        if (mode == BudgetManager.MODE_WEEKLY) remainingDaysInThisWeekIncludingToday()
                        else remainingDaysInThisMonthIncludingToday()

                    val dynamic = if (days > 0) remainingOverall / days else 0.0
                    BudgetManager.setDailyLimit(ctx, dynamic, false)
                    dynamic
                } else {
                    BudgetManager.getDailyLimit(ctx)
                }

            val todaySpent = if (perDay > 0) BudgetManager.getTodaySpend(ctx) else 0.0

            tvMyBudget.text = "My Budget: ₹${myBudget.toInt()}"
            tvPerDayBudget.text = "Per day limit: ₹${perDay.toInt()}"

            // ✅ Chart should show only when budget is actually set
            // (daily perDay >0 OR weekly/monthly original limit >0)
            val hasBudget =
                (perDay > 0.0) ||
                (BudgetManager.getWeeklyLimit(ctx) > 0.0) ||
                (BudgetManager.getMonthlyLimit(ctx) > 0.0)

            setChartVisible(hasBudget)

            // ✅ If budget not set -> stop here (no empty chart / no zeros)
            if (!hasBudget) {
                setBadgeUi(text = "SET", bgColor = blue)
                tvLeft.text = "Left: ₹0"
                tvSpent.text = "Spent: ₹0"
                tvDailySuggestion.text = "Set a budget limit to control spending."
                overallContainer.visibility = View.GONE

                animateProgress(progressDaily, from = lastDailyProgress, to = 0) { lastDailyProgress = 0 }
                animateTint(progressDaily, from = lastDailyTint ?: blue, to = blue) { lastDailyTint = blue }
                return@launch
            }

            // ✅ Skip logic (kept, not changing)
            val basePerDay = BudgetManager.getInitialDailyLimit(ctx)
            val threshold = basePerDay * 3
            val maxTxnToday = BudgetManager.getMaxExpenseTodayForSkip(ctx)

            val shouldShowSkip =
                basePerDay > 0 &&
                (
                    todaySpent >= threshold ||
                        (maxTxnToday != null && maxTxnToday.amount >= threshold)
                    )

            // -------- Daily Remaining --------
            val dailyRemaining = perDay - todaySpent
            val dailyOver = todaySpent > perDay

            setBadgeUi(text = if (dailyOver) "OVER" else "SAFE", bgColor = if (dailyOver) red else blue)

            tvLeft.text =
                if (dailyOver) "Over: ₹${abs(dailyRemaining).toInt()}"
                else "Left: ₹${dailyRemaining.toInt()}"
            tvSpent.text = "Spent: ₹${todaySpent.toInt()}"

            tvDailySuggestion.text = BudgetManager.dailySuggestion(ctx)

            val dailyPct = BudgetManager.progressPct(ctx).coerceIn(0, 100)
            animateProgress(progressDaily, from = lastDailyProgress, to = dailyPct) { lastDailyProgress = dailyPct }
            animateTint(progressDaily, from = lastDailyTint ?: blue, to = if (dailyOver) red else blue) {
                lastDailyTint = if (dailyOver) red else blue
            }

            // -------- Overall Section (ONLY for Weekly/Monthly) --------
            if (mode == BudgetManager.MODE_WEEKLY || mode == BudgetManager.MODE_MONTHLY) {
                overallContainer.visibility = View.VISIBLE

                val overallLimit = BudgetManager.getOverallLimit(ctx)
                val overallSpent = BudgetManager.getOverallSpent(ctx)
                val overallRemaining = overallLimit - overallSpent
                val overallOver = overallSpent > overallLimit

                tvOverallTitle.text = if (mode == BudgetManager.MODE_WEEKLY) "Weekly Budget" else "Monthly Budget"

                tvOverallLeft.text = if (overallOver) {
                    "Over: ₹${abs(overallRemaining).toInt()}"
                } else {
                    "Left: ₹${overallRemaining.toInt()}"
                }
                tvOverallSpent.text = "Spent: ₹${overallSpent.toInt()}"

                val overallPct =
                    if (overallLimit > 0) ((overallSpent / overallLimit) * 100).toInt().coerceIn(0, 100) else 0
                animateProgress(progressOverall, from = lastOverallProgress, to = overallPct) { lastOverallProgress = overallPct }
                animateTint(progressOverall, from = lastOverallTint ?: blue, to = if (overallOver) red else blue) {
                    lastOverallTint = if (overallOver) red else blue
                }
            } else {
                overallContainer.visibility = View.GONE
            }

            // ✅ Update chart only if expanded AND budget exists
            if (chartExpanded) {
                val limitForColor = BudgetManager.getDailyLimit(ctx).toFloat()
                updateBarChartFromBudgetStart(ctx, limitForColor)
            }
        }
    }


    // ✅ Weekly/Monthly per-day auto update (based on remaining overall budget)
    private suspend fun recomputePerDayForWeeklyMonthly(ctx: Context, mode: String) {
        if (mode != BudgetManager.MODE_WEEKLY && mode != BudgetManager.MODE_MONTHLY) return

        val overallLimit = BudgetManager.getOverallLimit(ctx)
        val overallSpent = BudgetManager.getOverallSpent(ctx)
        val remainingOverall = (overallLimit - overallSpent).coerceAtLeast(0.0)

        val daysRemaining =
            if (mode == BudgetManager.MODE_WEEKLY) remainingDaysInThisWeekIncludingToday()
            else remainingDaysInThisMonthIncludingToday()

        val newPerDay = if (daysRemaining > 0) remainingOverall / daysRemaining else 0.0
        BudgetManager.setDailyLimit(ctx, newPerDay, false)
    }

    private fun todayKey(): String {
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return df.format(Date())
    }


    // -------------------- Chart --------------------

    private fun toggleChart() {
        chartExpanded = !chartExpanded
        chartContainer.visibility = if (chartExpanded) View.VISIBLE else View.GONE

        ivChartToggle.setImageResource(
            if (chartExpanded) R.drawable.ic_expand_less
            else R.drawable.ic_expand_more
        )

        if (chartExpanded) refresh()
    }

    private fun setupBarChart() {
        barChart.description.isEnabled = false
        barChart.setTouchEnabled(true)
        barChart.isDragEnabled = true
        barChart.setScaleEnabled(false)
        barChart.setPinchZoom(false)

        barChart.axisRight.isEnabled = false
        barChart.legend.isEnabled = false

        barChart.setFitBars(false)

        // ✅ space between Y axis and first bar
        barChart.setExtraOffsets(18f, 14f, 10f, 12f)

        barChart.axisLeft.apply {
            textColor = Color.DKGRAY
            setDrawGridLines(true)
            axisMinimum = 0f
            setDrawAxisLine(false)
            setSpaceTop(20f)
        }

        barChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)

            // ✅ IMPORTANT
            granularity = 1f
            setGranularityEnabled(true)

            textColor = Color.DKGRAY

        

            setAvoidFirstLastClipping(false)
            setCenterAxisLabels(false)
        }
    }



    private suspend fun updateBarChartFromBudgetStart(ctx: Context, perDayLimit: Float) {
        val rows: List<BudgetDayTotal> = BudgetManager.getTotalsFromBudgetStartToToday(ctx)

        val labels = ArrayList<String>()
        val df = SimpleDateFormat("dd MMM", Locale.getDefault())

        val entries = ArrayList<BarEntry>(rows.size)
        val colors = ArrayList<Int>(rows.size)

        val greenColor = ContextCompat.getColor(ctx, R.color.cobalt_blue)
        val redColor = ContextCompat.getColor(ctx, android.R.color.holo_red_dark)

        rows.forEachIndexed { idx, r ->
            labels.add(df.format(Date(r.dayStartMillis)))
            val amount = r.totalAmount.toFloat()
            entries.add(BarEntry(idx.toFloat(), amount))

            val crossed = perDayLimit > 0f && amount > perDayLimit
            colors.add(if (crossed) redColor else greenColor)
        }

        barChart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val i = value.toInt()
                return if (i in labels.indices) labels[i] else ""
            }
        }

        // ✅ IMPORTANT: spacing so first bar is not stuck to Y axis
        barChart.xAxis.axisMinimum = -0.5f
        barChart.xAxis.axisMaximum = (rows.size - 1).toFloat() + 0.5f
        barChart.xAxis.setLabelCount(minOf(5, rows.size), false)

        val set = BarDataSet(entries, "Daily Spend").apply {
            setDrawValues(true)
            valueTextSize = 10f
            valueTextColor = Color.DKGRAY

            valueFormatter = object : ValueFormatter() {
                override fun getBarLabel(barEntry: BarEntry?): String {
                    val v = barEntry?.y ?: 0f
                    return if (v == 0f) "" else "₹${v.toInt()}"
                }
            }

            this.colors = colors
        }

        val data = BarData(set).apply {
            barWidth = 0.5f
        }

        barChart.data = data

        // ✅ Limit line
        val leftAxis = barChart.axisLeft
        leftAxis.removeAllLimitLines()
        if (perDayLimit > 0f) {
            val ll = LimitLine(perDayLimit, "₹${perDayLimit.toInt()} / day")
            ll.lineWidth = 1.5f
            ll.textSize = 10f
            ll.lineColor = Color.RED
            ll.textColor = Color.RED
            leftAxis.addLimitLine(ll)
        }

        // ✅ Top padding avoid clipping
        val maxValue = rows.maxOfOrNull { it.totalAmount }?.toFloat() ?: 0f
        val top = kotlin.math.max(maxValue, perDayLimit)
        leftAxis.axisMaximum = top + (top * 0.25f) + 10f

        barChart.axisLeft.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String = "₹${value.toInt()}"
        }

        // ✅ Show only last 5 days on screen, but keep full data (scrollable)
        barChart.setVisibleXRangeMaximum(5f)
        if (rows.isNotEmpty()) {
            barChart.moveViewToX((rows.size - 5).toFloat().coerceAtLeast(0f))
        }

        barChart.invalidate()
    }

    private fun setChartVisible(show: Boolean) {
        ivChartToggle.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) {
            chartExpanded = false
            chartContainer.visibility = View.GONE
            ivChartToggle.setImageResource(R.drawable.ic_expand_more)
        }
    }


    // -------------------- Anim helpers --------------------

    private fun animateProgress(pb: ProgressBar, from: Int, to: Int, onDone: () -> Unit) {
        ObjectAnimator.ofInt(pb, "progress", from, to).apply {
            duration = 450
            start()
        }
        onDone()
    }

    private fun animateTint(pb: ProgressBar, from: Int, to: Int, onDone: () -> Unit) {
        ValueAnimator.ofObject(ArgbEvaluator(), from, to).apply {
            duration = 450
            addUpdateListener { anim ->
                val c = anim.animatedValue as Int
                pb.progressTintList = ColorStateList.valueOf(c)
                pb.progressBackgroundTintList = ColorStateList.valueOf(adjustAlpha(c, 0.18f))
            }
            start()
        }
        onDone()
    }

    private fun setBadgeUi(text: String, bgColor: Int) {
        tvBadge.text = text
        ViewCompat.setBackgroundTintList(tvBadge, ColorStateList.valueOf(bgColor))
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val a = (Color.alpha(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    // -------------------- Dialog --------------------
    // ✅ Kept exactly as your existing dialog + helpers

    private fun showSetLimitDialog() {
        fun findActivity(v: View): android.app.Activity? {
            var c = v.context
            while (c is android.content.ContextWrapper) {
                if (c is android.app.Activity) return c
                c = c.baseContext
            }
            return null
        }

        val act = findActivity(root) ?: return
        if (act.isFinishing) return

        val cobalt = ContextCompat.getColor(act, R.color.cobalt_blue)

        val container = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 10)
            setBackgroundColor(Color.WHITE)
        }

        val rg = RadioGroup(act).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(0, 10, 0, 10)
            setBackgroundColor(Color.WHITE)
        }

        fun radio(text: String): RadioButton =
            RadioButton(act).apply {
                id = View.generateViewId()
                this.text = text
                setTextColor(Color.BLACK)
                setBackgroundColor(Color.WHITE)
                setButtonDrawable(R.drawable.rb_blue_selector)
                setPadding(0, 14, 0, 14)
            }

        val rbWeekly = radio("Weekly (auto divide per day)")
        val rbMonthly = radio("Monthly (auto divide per day)")
        val rbDaily = radio("Each day (set daily limit manually)")

        rg.addView(rbWeekly)
        rg.addView(rbMonthly)
        rg.addView(rbDaily)
        rg.check(rbDaily.id)

        val input = EditText(act).apply {
            hint = "Enter amount (e.g., 2000)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.BLACK)
            setHintTextColor(Color.GRAY)
            background = ContextCompat.getDrawable(act, R.drawable.bg_input_white_blue)
            textSize = 16f
            minHeight = 48.dp(act)
            setPadding(30, 25, 30, 25)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 14.dp(act) }
        }

        val note = TextView(act).apply {
            text = "Tip: Weekly/Monthly will be divided for remaining days (including today)."
            setTextColor(Color.DKGRAY)
            textSize = 12f
            setPadding(0, 16, 0, 0)
            setBackgroundColor(Color.WHITE)
        }

        container.addView(rg)
        container.addView(input)
        container.addView(note)

        val titleTv = TextView(act).apply {
            text = "Set Budget Limit"
            setTextColor(cobalt)
            textSize = 18f
            setPadding(50, 40, 50, 10)
            setBackgroundColor(Color.WHITE)
        }

        val dialog = AlertDialog.Builder(act, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setCustomTitle(titleTv)
            .setView(container)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .show()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.WHITE))
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(cobalt)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(cobalt)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            val amount = input.text?.toString()?.trim().orEmpty().toDoubleOrNull()
            if (amount == null || amount <= 0) {
                input.error = "Enter valid amount"
                return@setOnClickListener
            }

            // ✅ store initial per-day for SKIP logic (fixed base)
            var initialPerDayForSkip = amount

            when (rg.checkedRadioButtonId) {
                rbWeekly.id -> {
                    val days = remainingDaysInThisWeekIncludingToday()
                    val perDay = amount / max(1, days)
                    BudgetManager.setWeeklyLimit(act, amount)
                    BudgetManager.setDailyLimit(act, perDay, false)
                    initialPerDayForSkip = perDay
                }

                rbMonthly.id -> {
                    val days = remainingDaysInThisMonthIncludingToday()
                    val perDay = amount / max(1, days)
                    BudgetManager.setMonthlyLimit(act, amount)
                    BudgetManager.setDailyLimit(act, perDay, false)
                    initialPerDayForSkip = perDay
                }

                else -> {
                    BudgetManager.setDailyLimit(act, amount, true)
                    initialPerDayForSkip = amount
                }
            }

            // ✅ save "first per-day" only for skip calculations
            BudgetManager.setInitialDailyLimit(act, initialPerDayForSkip)

            BudgetManager.setEnabled(act, true)
            BudgetManager.setBudgetStartNow(act)

            dialog.dismiss()
            refresh()
        }
    }

    private fun Int.dp(ctx: Context): Int =
        (this * ctx.resources.displayMetrics.density).toInt()

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