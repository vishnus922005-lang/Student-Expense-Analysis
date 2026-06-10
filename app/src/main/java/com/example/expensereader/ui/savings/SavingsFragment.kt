package com.example.expensereader.ui.savings

import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.expensereader.R
import com.example.expensereader.db.AppDatabase
import com.example.expensereader.util.SmartSavingPrefs
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

class SavingsFragment : Fragment(R.layout.fragment_savings) {

    private var pendingRefresh = false

    // ✅ Insights expand/collapse
    private var insightsExpanded = false
    private var insightsLoadedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        parentFragmentManager.setFragmentResultListener(
            "SAVING_STARTED_TRIGGER",
            this
        ) { _, _ ->
            pendingRefresh = true
            if (view != null) updateSavingStatusUI()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ Savings tab button: visible only BEFORE started
        view.findViewById<MaterialButton>(R.id.btnViewStartSaving)?.setOnClickListener {
            findNavController().navigate(R.id.studentExpenseFragment)
        }
        SavingSchemesSection(this).setup(view)

        // ✅ Insights toggle (ONLY exists in savings tab layout)
        setupInsightsToggle(view)

        updateSavingStatusUI()
    }

    override fun onResume() {
        super.onResume()
        if (pendingRefresh) pendingRefresh = false

        updateSavingStatusUI()

        // refresh insights if open
        if (insightsExpanded) {
            insightsLoadedOnce = false
            loadSavingInsightsIfStarted()
        }
    }

    private fun updateSavingStatusUI() {
        val root = view ?: return

        val card = root.findViewById<View>(R.id.cardStartSaving)
        val title = root.findViewById<TextView>(R.id.tvHomeTitle)
        val tv = root.findViewById<TextView>(R.id.tvSavingStatus)
        val tracker = root.findViewById<View>(R.id.weeklyTrackerContainer)
        val btn = root.findViewById<MaterialButton>(R.id.btnViewStartSaving)

        if (tv == null || tracker == null || title == null) {
            Log.e("SAVINGS_UI", "Missing tvSavingStatus / weeklyTrackerContainer / tvHomeTitle")
            return
        }

        card?.visibility = View.VISIBLE

        val started = SmartSavingPrefs.isStarted(requireContext())

        // ✅ Title update
        title.text = if (started) "Daily Saving" else "Start Saving"

        // ✅ Button rule
        if (!started) {
            btn?.visibility = View.VISIBLE
            btn?.text = "View"

            tracker.visibility = View.GONE
            tv.text = "You have not started saving yet."

            // hide flags
            listOf(
                R.id.day0Flag, R.id.day1Flag, R.id.day2Flag, R.id.day3Flag,
                R.id.day4Flag, R.id.day5Flag, R.id.day6Flag
            ).forEach { id -> root.findViewById<ImageView>(id)?.visibility = View.GONE }

            // ✅ collapse insights when not started (ONLY in savings tab)
            collapseInsights(root)

            return
        } else {
            btn?.visibility = View.GONE
        }

        // 🟢 STARTED
        tracker.visibility = View.VISIBLE
        tv.text = "Checking weekly trend..."

        val dayIds = listOf(R.id.day0, R.id.day1, R.id.day2, R.id.day3, R.id.day4, R.id.day5, R.id.day6)
        val flagIds = listOf(R.id.day0Flag, R.id.day1Flag, R.id.day2Flag, R.id.day3Flag, R.id.day4Flag, R.id.day5Flag, R.id.day6Flag)
        val lineIds = listOf(R.id.line0, R.id.line1, R.id.line2, R.id.line3, R.id.line4, R.id.line5)

        val days = dayIds.map { root.findViewById<ImageView>(it) }
        val flags = flagIds.map { root.findViewById<ImageView>(it) }
        val lines = lineIds.map { root.findViewById<View>(it) }

        if (days.any { it == null } || flags.any { it == null } || lines.any { it == null }) {
            Log.e("SAVINGS_UI", "Tracker views missing in Savings layout")
            tv.text = "Tracker layout error"
            tracker.visibility = View.GONE
            return
        }

        val daysNN = days.filterNotNull()
        val flagsNN = flags.filterNotNull()
        val linesNN = lines.filterNotNull()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val dao = AppDatabase.getInstance(requireContext()).expenseDao()

                val weekStart = startOfWeekMillis()
                val weekEnd = weekStart + (7L * 86400000L) - 1L
                val tzOffset = TimeZone.getDefault().getOffset(weekStart).toLong()

                val rows = dao.getSavingCountsByDay(weekStart, weekEnd, tzOffset)
                val doneDays = rows.map { it.dayStart }.toSet()

                val todayIndex =
                    ((System.currentTimeMillis() - weekStart) / 86400000L)
                        .toInt().coerceIn(0, 6)

                val blue = ContextCompat.getColor(requireContext(), R.color.cobalt_blue)
                val gray = ContextCompat.getColor(requireContext(), R.color.text_muted)

                // reset
                linesNN.forEach { it.setBackgroundColor(gray) }
                flagsNN.forEach { it.visibility = View.GONE }

                // circles + flags
                for (i in 0..6) {
                    val dayStart = weekStart + (i * 86400000L)
                    val isDone = doneDays.contains(dayStart)

                     val iconRes = when {
                        isDone -> R.drawable.ic_day_done
                        i < todayIndex -> R.drawable.ic_day_missed          // ✅ RED for not done
                        i == todayIndex -> R.drawable.ic_day_today_pending
                        else -> R.drawable.ic_day_future
                    }

                    daysNN[i].setImageResource(iconRes)
                    flagsNN[i].visibility = if (isDone) View.VISIBLE else View.GONE
                }

                // trend lines
                for (i in 0..5) {
                    val leftDayStart = weekStart + (i * 86400000L)
                    if (doneDays.contains(leftDayStart)) linesNN[i].setBackgroundColor(blue)
                }

                tv.text = if (doneDays.isEmpty()) {
                    "No saving SMS found this week"
                } else {
                    "Weekly trend: ${doneDays.size} / 7 days"
                }

                // keep title correct
                title.text = "Daily Saving"

                // refresh insights if open
                if (insightsExpanded) {
                    insightsLoadedOnce = false
                    loadSavingInsightsIfStarted()
                }

            } catch (e: Exception) {
                tv.text = "Unable to load weekly saving status"
                Log.e("SAVINGS_UI", "updateSavingStatusUI failed", e)
            }
        }
    }

    // ---------------------------
    // ✅ Insights UI handling (ONLY Savings tab layout contains these views)
    // ---------------------------

    private fun setupInsightsToggle(root: View) {
        val header = root.findViewById<View>(R.id.insightsHeader)
        val arrow = root.findViewById<ImageView>(R.id.ivInsightsArrow)
        val container = root.findViewById<View>(R.id.insightsContainer)

        // If this is not savings-tab layout, these IDs won't exist -> safe
        if (header == null || arrow == null || container == null) return

        container.visibility = if (insightsExpanded) View.VISIBLE else View.GONE
        arrow.setImageResource(if (insightsExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more)

        header.setOnClickListener {
            insightsExpanded = !insightsExpanded
            container.visibility = if (insightsExpanded) View.VISIBLE else View.GONE
            arrow.setImageResource(if (insightsExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more)

            if (insightsExpanded) {
                insightsLoadedOnce = false
                loadSavingInsightsIfStarted()
            }
        }
    }

    private fun collapseInsights(root: View) {
        val arrow = root.findViewById<ImageView>(R.id.ivInsightsArrow)
        val container = root.findViewById<View>(R.id.insightsContainer)

        // If not present, do nothing
        if (arrow == null || container == null) return

        insightsExpanded = false
        container.visibility = View.GONE
        arrow.setImageResource(R.drawable.ic_expand_more)
        insightsLoadedOnce = false
    }

    private fun loadSavingInsightsIfStarted() {
        val root = view ?: return
        val ctx = requireContext()

        val started = SmartSavingPrefs.isStarted(ctx)
        if (!started) return

        if (insightsLoadedOnce) return
        insightsLoadedOnce = true

        val tvTotal = root.findViewById<TextView>(R.id.tvTotalSaved)
        val tvWeek = root.findViewById<TextView>(R.id.tvWeekSaved)
        val tvMonth = root.findViewById<TextView>(R.id.tvMonthSaved)

        // ✅ month saved days (after month saved)
        val tvMonthDays = root.findViewById<TextView>(R.id.tvMonthSavedDays)

        // ✅ NEW: overall saved days
        val tvOverallSavedDays = root.findViewById<TextView>(R.id.tvOverallSavedDays)

        val tvNotSaved = root.findViewById<TextView>(R.id.tvNotSavedDays)
        val tvPct = root.findViewById<TextView>(R.id.tvAvgPct)
        val pb = root.findViewById<ProgressBar>(R.id.pbAvgPct)

        if (tvTotal == null || tvWeek == null || tvMonth == null ||
            tvMonthDays == null || tvOverallSavedDays == null ||
            tvNotSaved == null || tvPct == null || pb == null
        ) return

        tvTotal.text = "Loading..."
        tvWeek.text = "Loading..."
        tvMonth.text = "Loading..."
        tvMonthDays.text = "..."
        tvOverallSavedDays.text = "..."
        tvNotSaved.text = "..."
        tvPct.text = "..."

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val dao = AppDatabase.getInstance(ctx).expenseDao()

                val weekStart = startOfWeekMillis()
                val weekEnd = weekStart + (7L * 86400000L) - 1L

                val monthStart = startOfMonthMillis()
                val now = System.currentTimeMillis()

                val totalSaved = dao.getTotalSavedAmountOrZero()
                val weekSaved = dao.getSavedAmountBetweenOrZero(weekStart, weekEnd)
                val monthSaved = dao.getSavedAmountBetweenOrZero(monthStart, now)

                val tzOffset = TimeZone.getDefault().getOffset(now).toLong()
                val todayStart = startOfDayMillis(now)
                val todayEndInclusive = todayStart + 86399999L

                // ✅ month saved days
                val monthSavedDays = dao.getDistinctSavingDaysCountBetween(
                    monthStart,
                    todayEndInclusive,
                    tzOffset
                ).coerceAtLeast(0)

                // ✅ overall range from first saving sms day -> today
                val firstSavingMillis = dao.getFirstSavingMillisOrNull()

                var pct = 0
                var notSavedDays = 0
                var overallSavedDays = 0

                if (firstSavingMillis != null) {
                    val startDayMillis = startOfDayMillis(firstSavingMillis)
                    val endDayMillis = todayStart
                    val endInclusive = endDayMillis + 86399999L

                    val totalDaysInt = (((endDayMillis - startDayMillis) / 86400000L) + 1L)
                        .toInt()
                        .coerceAtLeast(1)

                    overallSavedDays = dao.getDistinctSavingDaysCountBetween(
                        startDayMillis,
                        endInclusive,
                        tzOffset
                    ).coerceAtLeast(0)

                    pct = ((overallSavedDays * 100f) / totalDaysInt.toFloat())
                        .toInt()
                        .coerceIn(0, 100)

                    notSavedDays = (totalDaysInt - overallSavedDays).coerceAtLeast(0)
                }

                tvTotal.text = "₹" + String.format("%,.0f", totalSaved)
                tvWeek.text = "₹" + String.format("%,.0f", weekSaved)
                tvMonth.text = "₹" + String.format("%,.0f", monthSaved)

                tvMonthDays.text = monthSavedDays.toString()
                tvOverallSavedDays.text = overallSavedDays.toString()

                tvNotSaved.text = notSavedDays.toString()
                tvPct.text = "$pct%"

                pb.progress = pct

                val colorRes = when {
                    pct >= 80 -> R.color.cobalt_blue
                    pct >= 50 -> android.R.color.holo_orange_dark
                    else -> android.R.color.holo_red_dark
                }
                val color = ContextCompat.getColor(ctx, colorRes)
                tvPct.setTextColor(color)
                pb.progressTintList = ColorStateList.valueOf(color)

            } catch (e: Exception) {
                Log.e("SAVINGS_UI", "loadSavingInsightsIfStarted failed", e)
                tvTotal.text = "—"
                tvWeek.text = "—"
                tvMonth.text = "—"
                tvMonthDays.text = "—"
                tvOverallSavedDays.text = "—"
                tvNotSaved.text = "—"
                tvPct.text = "—"
            }
        }
    }



    private fun startOfMonthMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    

    private fun startOfWeekMillis(): Long {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val day = cal.get(Calendar.DAY_OF_WEEK)
        val diff = (day + 5) % 7
        cal.add(Calendar.DAY_OF_MONTH, -diff)
        return cal.timeInMillis
    }

    private fun startOfDayMillis(timeMillis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timeMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

}
