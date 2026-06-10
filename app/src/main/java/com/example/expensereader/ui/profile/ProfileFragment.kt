package com.example.expensereader.ui.profile

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.expensereader.R
import androidx.navigation.fragment.findNavController
import com.example.expensereader.db.AppDatabase
import com.example.expensereader.repo.ChallengeWeekRepo
import com.example.expensereader.util.BudgetManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private lateinit var tvPoints: TextView
    private lateinit var tvStreak: TextView

    private lateinit var btnToggleLevels: ImageView
    private lateinit var layoutLevels: View
    private var levelsExpanded = false

    private lateinit var btnToggleHistory: ImageView
    private lateinit var layoutHistory: View
    private var historyExpanded = false

    private lateinit var btnTogglePointsHistory: ImageView
    private lateinit var btnToggleStreakHistory: ImageView
    private var pointsExpanded = false
    private var streakExpanded = false

    private lateinit var rvPointsHistory: RecyclerView
    private lateinit var rvStreakHistory: RecyclerView

    private lateinit var pointsAdapter: RewardHistoryRowAdapter
    private lateinit var streakAdapter: RewardHistoryRowAdapter

    // ✅ How-to-earn toggles (MAKE THEM CLASS MEMBERS)
    private lateinit var btnToggleHowToEarn: ImageView
    private lateinit var layoutHowToEarn: View

    private lateinit var btnToggleHowPoints: ImageView
    private lateinit var btnViewPrevMonth: com.google.android.material.button.MaterialButton
    private lateinit var layoutHowPoints: View

    private lateinit var btnToggleHowStreak: ImageView
    private lateinit var layoutHowStreak: View

    private val weekRepo = ChallengeWeekRepo()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvPoints = view.findViewById(R.id.tvPointsValue)
        tvStreak = view.findViewById(R.id.tvStreakValue)

        btnToggleLevels = view.findViewById(R.id.btnToggleLevels)
        layoutLevels = view.findViewById(R.id.layoutLevels)

        btnToggleHistory = view.findViewById(R.id.btnToggleHistory)
        layoutHistory = view.findViewById(R.id.layoutHistory)

        btnTogglePointsHistory = view.findViewById(R.id.btnTogglePointsHistory)
        btnToggleStreakHistory = view.findViewById(R.id.btnToggleStreakHistory)

        rvPointsHistory = view.findViewById(R.id.rvPointsHistory)
        rvStreakHistory = view.findViewById(R.id.rvStreakHistory)

        pointsAdapter = RewardHistoryRowAdapter()
        streakAdapter = RewardHistoryRowAdapter()

        rvPointsHistory.layoutManager = LinearLayoutManager(requireContext())
        rvStreakHistory.layoutManager = LinearLayoutManager(requireContext())
        rvPointsHistory.adapter = pointsAdapter
        rvStreakHistory.adapter = streakAdapter
        rvPointsHistory.isNestedScrollingEnabled = false
        rvStreakHistory.isNestedScrollingEnabled = false

        // ✅ init how-to-earn views (IMPORTANT)
        btnToggleHowToEarn = view.findViewById(R.id.btnToggleHowToEarn)
        layoutHowToEarn = view.findViewById(R.id.layoutHowToEarn)

        btnToggleHowPoints = view.findViewById(R.id.btnToggleHowPoints)
        layoutHowPoints = view.findViewById(R.id.layoutHowPoints)

        btnToggleHowStreak = view.findViewById(R.id.btnToggleHowStreak)
        layoutHowStreak = view.findViewById(R.id.layoutHowStreak)

        // ✅ NEW: Previous Month Summary "View" button
        // (Make sure this id exists in XML: @+id/btnViewPrevMonth)
        btnViewPrevMonth = view.findViewById(R.id.btnViewPrevMonth)
        btnViewPrevMonth.setOnClickListener {
            // ✅ Navigation graph action (recommended)
            // Make sure you added: action_profileFragment_to_previousMonthAnalysisFragment
            findNavController().navigate(R.id.action_profileFragment_to_previousMonthAnalysisFragment)
        }

        setupLevelsToggle()
        setupHistoryToggles()
        setupHowToEarnToggles() // ✅ NOW USES CLASS MEMBERS

        RewardHistoryStore.cleanupLegacyTotalsAndDuplicates(requireContext())

        renderHistoryLists()

        // overall totals since start
        tvPoints.text = RewardHistoryStore.totalPoints(requireContext()).toString()
        tvStreak.text = RewardHistoryStore.totalStreak(requireContext()).toString()

        bindGamification()
    }

    private fun setupLevelsToggle() {
        levelsExpanded = false
        layoutLevels.visibility = View.GONE

        fun updateArrow() {
            btnToggleLevels.setImageResource(
                if (levelsExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
            )
        }
        updateArrow()

        btnToggleLevels.setOnClickListener {
            levelsExpanded = !levelsExpanded
            layoutLevels.visibility = if (levelsExpanded) View.VISIBLE else View.GONE
            updateArrow()
        }
    }

    private fun setupHistoryToggles() {
        historyExpanded = false
        pointsExpanded = false
        streakExpanded = false

        layoutHistory.visibility = View.GONE
        rvPointsHistory.visibility = View.GONE
        rvStreakHistory.visibility = View.GONE

        fun setArrow(img: ImageView, expanded: Boolean) {
            img.setImageResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more)
        }

        setArrow(btnToggleHistory, historyExpanded)
        setArrow(btnTogglePointsHistory, pointsExpanded)
        setArrow(btnToggleStreakHistory, streakExpanded)

        btnToggleHistory.setOnClickListener {
            historyExpanded = !historyExpanded
            layoutHistory.visibility = if (historyExpanded) View.VISIBLE else View.GONE
            setArrow(btnToggleHistory, historyExpanded)
        }

        btnTogglePointsHistory.setOnClickListener {
            pointsExpanded = !pointsExpanded
            rvPointsHistory.visibility = if (pointsExpanded) View.VISIBLE else View.GONE
            setArrow(btnTogglePointsHistory, pointsExpanded)
        }

        btnToggleStreakHistory.setOnClickListener {
            streakExpanded = !streakExpanded
            rvStreakHistory.visibility = if (streakExpanded) View.VISIBLE else View.GONE
            setArrow(btnToggleStreakHistory, streakExpanded)
        }
    }

    private fun setupHowToEarnToggles() {
        var mainExpanded = false
        var pointsExpandedLocal = false
        var streakExpandedLocal = false

        fun setArrow(img: ImageView, expanded: Boolean) {
            img.setImageResource(
                if (expanded) R.drawable.ic_expand_less
                else R.drawable.ic_expand_more
            )
        }

        // start collapsed
        layoutHowToEarn.visibility = View.GONE
        layoutHowPoints.visibility = View.GONE
        layoutHowStreak.visibility = View.GONE

        setArrow(btnToggleHowToEarn, mainExpanded)
        setArrow(btnToggleHowPoints, pointsExpandedLocal)
        setArrow(btnToggleHowStreak, streakExpandedLocal)

        btnToggleHowToEarn.setOnClickListener {
            mainExpanded = !mainExpanded
            layoutHowToEarn.visibility = if (mainExpanded) View.VISIBLE else View.GONE
            setArrow(btnToggleHowToEarn, mainExpanded)
        }

        btnToggleHowPoints.setOnClickListener {
            pointsExpandedLocal = !pointsExpandedLocal
            layoutHowPoints.visibility = if (pointsExpandedLocal) View.VISIBLE else View.GONE
            setArrow(btnToggleHowPoints, pointsExpandedLocal)
        }

        btnToggleHowStreak.setOnClickListener {
            streakExpandedLocal = !streakExpandedLocal
            layoutHowStreak.visibility = if (streakExpandedLocal) View.VISIBLE else View.GONE
            setArrow(btnToggleHowStreak, streakExpandedLocal)
        }
    }

    private fun renderHistoryLists() {
        val ctx = requireContext()
        pointsAdapter.submit(RewardHistoryStore.pointsRows(ctx))
        streakAdapter.submit(RewardHistoryStore.streakRows(ctx))
    }

    private fun bindGamification() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {

                val ctx = requireContext()
                val dao = AppDatabase.getInstance(ctx).expenseDao()

                val (windowStart, windowEnd) = last7DaysRange()

                dao.observeDailyTotalsBetween(windowStart, windowEnd)
                    .collectLatest {
                        try {
                            val now = System.currentTimeMillis()
                            val todayStart = startOfDayMillis(now)
                            val todayEnd = todayStart + 86400000L - 1L
                            val todayKey = RewardHistoryStore.todayKey()

                            val dailyLimit = BudgetManager.getDailyLimit(ctx)

                            // -------------------------
                            // ✅ DAILY POINTS (TODAY ONLY)
                            // -------------------------
                            val todaySpend = dao.getTotalDebitBetween(todayStart, todayEnd)
                            val dailyBudgetPoints = if (todaySpend <= dailyLimit) 10 else 0

                            val tzOffsetToday =
                                java.util.TimeZone.getDefault().getOffset(todayStart).toLong()
                            val savingTodayRows =
                                dao.getSavingCountsByDay(todayStart, todayEnd, tzOffsetToday)
                            val dailySavingPoints = if (savingTodayRows.isNotEmpty()) 20 else 0

                            // -------------------------
                            // ✅ WEEKLY / MONTHLY (ONLY AT PERIOD END + ONCE)
                            // -------------------------
                            val isWeekEnd = RewardHistoryStore.isWeekEndToday()
                            val isMonthEnd = RewardHistoryStore.isMonthEndToday()

                            var weeklyChallengePoints = 0
                            var weeklyChallengeStreak = 0
                            var weeklyChallengeSource = "Weekly Challenge"

                            var weeklyBudgetStreak = 0
                            var weeklyBudgetSource = "Weekly Budget Met"

                            var savingStreak = 0
                            var savingStreakSource = "Saving Streak"

                            var monthlyBudgetStreak = 0
                            var monthlyBudgetSource = "Monthly Budget Met"

                            if (isWeekEnd && RewardHistoryStore.shouldAwardWeekSummary(ctx)) {

                                // weekly challenge
                                val (_, progress) = weekRepo.getThisWeekChallenge()
                                val weeklyCompleted =
                                    progress.status.equals("COMPLETED", ignoreCase = true)

                                weeklyChallengePoints = if (weeklyCompleted) 30 else 0
                                weeklyChallengeStreak = if (weeklyCompleted) 3 else -2
                                weeklyChallengeSource =
                                    if (weeklyCompleted) "Weekly Challenge"
                                    else "Weekly Challenge (Not Completed)"

                                // weekly budget
                                val (weekStart, weekEnd) = thisWeekRange()
                                val weekTotal = dao.getTotalDebitBetween(weekStart, weekEnd)

                                val weeklyBudget =
                                    BudgetManager.getWeeklyLimit(ctx).takeIf { it > 0 }
                                        ?: (dailyLimit * 7.0)

                                weeklyBudgetStreak = if (weekTotal <= weeklyBudget) 3 else -2
                                weeklyBudgetSource =
                                    if (weeklyBudgetStreak >= 0) "Weekly Budget Met"
                                    else "Weekly Budget Overspent"

                                // saving streak for this week
                                val tzOffsetWeek =
                                    java.util.TimeZone.getDefault().getOffset(weekStart).toLong()
                                val savingWeekRows =
                                    dao.getSavingCountsByDay(weekStart, weekEnd, tzOffsetWeek)
                                val savedDaysThisWeek = savingWeekRows.size

                                savingStreak = when {
                                    savedDaysThisWeek >= 7 -> 3
                                    savedDaysThisWeek >= 5 -> 2
                                    savedDaysThisWeek >= 3 -> 1
                                    else -> -2
                                }
                                savingStreakSource =
                                    if (savingStreak >= 0) "Saving Streak"
                                    else "Saving Streak (Not Saved 4 days)"

                                RewardHistoryStore.markWeekSummaryAwarded(ctx)
                            }

                            if (isMonthEnd && RewardHistoryStore.shouldAwardMonthSummary(ctx)) {
                                val (monthStart, monthEnd) = thisMonthRange()
                                val monthTotal = dao.getTotalDebitBetween(monthStart, monthEnd)

                                val monthlyBudget =
                                    BudgetManager.getMonthlyLimit(ctx).takeIf { it > 0 }
                                        ?: (dailyLimit * 30.0)

                                monthlyBudgetStreak = if (monthTotal <= monthlyBudget) 5 else -3
                                monthlyBudgetSource =
                                    if (monthlyBudgetStreak >= 0) "Monthly Budget Met"
                                    else "Monthly Budget Overspent"

                                RewardHistoryStore.markMonthSummaryAwarded(ctx)
                            }

                            // -------------------------
                            // ✅ BONUS (OVERALL): 50 points -> 1 streak
                            // -------------------------
                            val earnedPointsToday =
                                weeklyChallengePoints + dailyBudgetPoints + dailySavingPoints

                            val pointsBeforeToday =
                                RewardHistoryStore.totalPointsExcludingDay(ctx, todayKey)

                            val pointsAfterEarnToday = pointsBeforeToday + earnedPointsToday
                            val bonusStreak = pointsAfterEarnToday / 50

                            RewardHistoryStore.upsertTodayBreakdown(
                                ctx = ctx,
                                day = todayKey,
                                weeklyChallengePoints = weeklyChallengePoints,
                                dailyBudgetPoints = dailyBudgetPoints,
                                dailySavingPoints = dailySavingPoints,
                                weeklyChallengeStreak = weeklyChallengeStreak,
                                savingStreak = savingStreak,
                                weeklyBudgetStreak = weeklyBudgetStreak,
                                monthlyBudgetStreak = monthlyBudgetStreak,
                                bonusStreak = bonusStreak,
                                weeklyChallengeSource = weeklyChallengeSource,
                                savingStreakSource = savingStreakSource,
                                weeklyBudgetSource = weeklyBudgetSource,
                                monthlyBudgetSource = monthlyBudgetSource
                            )

                            renderHistoryLists()

                            tvPoints.text = RewardHistoryStore.totalPoints(ctx).toString()
                            tvStreak.text = RewardHistoryStore.totalStreak(ctx).toString()

                            updateLevelTimeline(requireView(), RewardHistoryStore.totalStreak(ctx))

                        } catch (e: Exception) {
                            Log.e("PROFILE_GAMIFY", "Failed to compute points/streak", e)
                            tvPoints.text = "0"
                            tvStreak.text = "0"
                        }
                    }
            }
        }
    }

    private fun updateLevelTimeline(root: View, streak: Int) {
        val thresholds = listOf(25, 50, 100, 200, 400, 800, 1600, 3200)
        val currentIndex = thresholds.indexOfFirst { streak < it }
            .let { if (it == -1) thresholds.lastIndex else it }

        val dotIds = listOf(
            R.id.lv1Dot, R.id.lv2Dot, R.id.lv3Dot, R.id.lv4Dot,
            R.id.lv5Dot, R.id.lv6Dot, R.id.lv7Dot, R.id.lv8Dot
        )

        val lineBelowIds = listOf(
            R.id.lv1LineBelow,
            R.id.lv2LineBelow,
            R.id.lv3LineBelow,
            R.id.lv4LineBelow,
            R.id.lv5LineBelow,
            R.id.lv6LineBelow,
            R.id.lv7LineBelow
        )

        dotIds.forEachIndexed { i, id ->
            val dot = root.findViewById<View>(id) ?: return@forEachIndexed
            when {
                i < currentIndex -> dot.setBackgroundResource(R.drawable.dot_blue_outline)
                i == currentIndex -> dot.setBackgroundResource(R.drawable.dot_blue_filled)
                else -> dot.setBackgroundResource(R.drawable.dot_gray_outline)
            }
        }

        val blue = ContextCompat.getColor(requireContext(), R.color.cobalt_blue)
        val gray = android.graphics.Color.parseColor("#B0B0B0")

        lineBelowIds.forEachIndexed { i, id ->
            val line = root.findViewById<View>(id) ?: return@forEachIndexed
            line.setBackgroundColor(if (i < currentIndex) blue else gray)
        }
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

    private fun last7DaysRange(): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val todayStart = startOfDayMillis(now)
        val windowStart = todayStart - (6L * 86400000L)
        val windowEnd = todayStart + (86400000L - 1L)
        return Pair(windowStart, windowEnd)
    }

    private fun thisWeekRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        val end = start + (7L * 86400000L) - 1L
        return Pair(start, end)
    }

    private fun thisMonthRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        val end = cal.timeInMillis - 1L
        return Pair(start, end)
    }
}
