package com.example.expensereader.util

import android.content.Context
import androidx.core.content.edit
import com.example.expensereader.db.AppDatabase
import com.example.expensereader.db.LatestExpenseRow
import com.example.expensereader.model.BudgetDayTotal
import com.example.expensereader.model.SkippedExpense
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object BudgetManager {

    private const val PREFS = "budget_prefs"

    private const val KEY_DAILY_LIMIT = "daily_limit"       // per-day applied limit
    private const val KEY_WEEKLY_LIMIT = "weekly_limit"     // original weekly amount
    private const val KEY_MONTHLY_LIMIT = "monthly_limit"   // original monthly amount
    private const val KEY_LIMIT_ENABLED = "limit_enabled"
    private const val KEY_MODE = "budget_mode"
    private const val KEY_BUDGET_START_MILLIS = "budget_start_millis"
    private const val KEY_INITIAL_DAILY_LIMIT = "initial_daily_limit"


    const val MODE_DAILY = "DAILY"
    const val MODE_WEEKLY = "WEEKLY"
    const val MODE_MONTHLY = "MONTHLY"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_LIMIT_ENABLED, true)

    fun setEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit { putBoolean(KEY_LIMIT_ENABLED, enabled) }
    }

    fun setMode(ctx: Context, mode: String) {
        prefs(ctx).edit { putString(KEY_MODE, mode) }
    }

    fun getMode(ctx: Context): String =
        prefs(ctx).getString(KEY_MODE, MODE_DAILY) ?: MODE_DAILY

    fun getDailyLimit(ctx: Context): Double =
        prefs(ctx).getFloat(KEY_DAILY_LIMIT, 0f).toDouble()

    /**
     * changeMode=false is used when weekly/monthly selected
     * because we store per-day but mode must stay WEEKLY/MONTHLY
     */
    fun setDailyLimit(ctx: Context, amount: Double, changeMode: Boolean = true) {
        prefs(ctx).edit { putFloat(KEY_DAILY_LIMIT, amount.toFloat()) }
        if (changeMode) setMode(ctx, MODE_DAILY)
    }

    fun getWeeklyLimit(ctx: Context): Double =
        prefs(ctx).getFloat(KEY_WEEKLY_LIMIT, 0f).toDouble()

    fun setWeeklyLimit(ctx: Context, amount: Double) {
        prefs(ctx).edit { putFloat(KEY_WEEKLY_LIMIT, amount.toFloat()) }
        setMode(ctx, MODE_WEEKLY)
    }

    fun setInitialDailyLimit(ctx: Context, amount: Double) {
        prefs(ctx).edit { putFloat(KEY_INITIAL_DAILY_LIMIT, amount.toFloat()) }
    }

    fun getInitialDailyLimit(ctx: Context): Double {
        return prefs(ctx).getFloat(KEY_INITIAL_DAILY_LIMIT, 0f).toDouble()
    }


    fun getMonthlyLimit(ctx: Context): Double =
        prefs(ctx).getFloat(KEY_MONTHLY_LIMIT, 0f).toDouble()

    fun setMonthlyLimit(ctx: Context, amount: Double) {
        prefs(ctx).edit { putFloat(KEY_MONTHLY_LIMIT, amount.toFloat()) }
        setMode(ctx, MODE_MONTHLY)
    }

    // ✅ Budget start helpers (single copy)
    fun setBudgetStartNow(ctx: Context) {
        prefs(ctx).edit { putLong(KEY_BUDGET_START_MILLIS, System.currentTimeMillis()) }
    }

    fun clearBudgetStart(ctx: Context) {
        prefs(ctx).edit { remove(KEY_BUDGET_START_MILLIS) }
    }

    fun getBudgetStartMillis(ctx: Context): Long {
        return prefs(ctx).getLong(KEY_BUDGET_START_MILLIS, 0L)
    }

   


    // ---------- Date helpers ----------
    private fun startOfTodayMillis(): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /**
     * ✅ FIXED: stable Monday-based week start (works correctly even on Sunday)
     */
    private fun startOfWeekMillis(): Long {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val dow = cal.get(Calendar.DAY_OF_WEEK) // SUN=1..SAT=7
        val diff = if (dow == Calendar.SUNDAY) 6 else (dow - Calendar.MONDAY) // Mon=0..Sun=6
        cal.add(Calendar.DAY_OF_MONTH, -diff)
        return cal.timeInMillis
    }

    private fun startOfMonthMillis(): Long =
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun startOfDayMillis(daysAgo: Int): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, -daysAgo)
        }.timeInMillis

    private fun startOfDayMillisFromMillis(millis: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun dayKey(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    // ---------- DB reads (EXCLUDING SKIPPED) ----------

    /**
     * ✅ Today spent excluding skipped expenses
     */
    suspend fun getTodaySpend(ctx: Context): Double = withContext(Dispatchers.IO) {
        autoSkipIfSingleTxnExceedsDaily(ctx)
        val start = maxOf(startOfTodayMillis(), getBudgetStartMillis(ctx))
        AppDatabase.getInstance(ctx).expenseDao().getTodayTotalAllExcludingSkipped(start)
    }


    /**
     * ✅ Week spent excluding skipped expenses
     */
    suspend fun getWeekSpend(ctx: Context): Double = withContext(Dispatchers.IO) {
        autoSkipIfSingleTxnExceedsDaily(ctx)

        val start = maxOf(startOfWeekMillis(), getBudgetStartMillis(ctx))
        AppDatabase.getInstance(ctx).expenseDao()
            .getTotalFromExcludingSkipped(start)
    }


    /**
     * ✅ Month spent excluding skipped expenses
     */
    suspend fun getMonthSpend(ctx: Context): Double = withContext(Dispatchers.IO) {
        autoSkipIfSingleTxnExceedsDaily(ctx)

        val start = maxOf(startOfMonthMillis(), getBudgetStartMillis(ctx))
        AppDatabase.getInstance(ctx).expenseDao()
            .getTotalFromExcludingSkipped(start)
    }

    suspend fun skipAllAbove(ctx: Context, minAmount: Double): Int = withContext(Dispatchers.IO) {
        val start = getBudgetStartMillis(ctx).let { if (it > 0) it else 0L }
        val inserted = AppDatabase.getInstance(ctx).expenseDao()
            .skipAllExpensesAboveAmount(start, minAmount)
        inserted.toInt()
    }

    suspend fun skipAllTodayAbove(ctx: Context, amount: Double): Unit = withContext(Dispatchers.IO) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        AppDatabase.getInstance(ctx)
            .expenseDao()
            .skipAllTodayAboveAmount(cal.timeInMillis, amount)
    }

    // ✅ last 7 days totals for chart
    // ✅ last 5 days totals for budget chart
    suspend fun getLast7DaysTotals(ctx: Context): List<BudgetDayTotal> =
        withContext(Dispatchers.IO) {

            val dao = AppDatabase.getInstance(ctx).expenseDao()

            // ✅ Always last 5 days including today (4 days ago -> today)
            val startDayMillis = startOfDayMillis(4)

            val raw = dao.getBudgetDailyTotalsFrom(startDayMillis)
            val mapByDay = raw.associateBy { dayKey(it.dayStartMillis) }

            val result = ArrayList<BudgetDayTotal>(5)

            val oneDay = 24L * 60L * 60L * 1000L
            val todayStart = startOfTodayMillis()

            for (i in 0 until 5) {
                val dayStart = startDayMillis + (i * oneDay)
                val key = dayKey(dayStart)
                val row = mapByDay[key]

                val total =
                    if (dayStart > todayStart) 0.0
                    else row?.totalAmount ?: 0.0

                result.add(BudgetDayTotal(dayStart, total))
            }

            result
        }

    // ---------- Skip Feature ----------

    /**
     * ✅ Mark one expense as skipped (exclude from budget calculations)
     */
    suspend fun skipExpense(ctx: Context, expenseId: Long) = withContext(Dispatchers.IO) {
        AppDatabase.getInstance(ctx).skippedExpenseDao()
            .insert(SkippedExpense(expenseId = expenseId))
    }

    /**
     * ✅ Latest expense today (not skipped) for showing Skip suggestion
     */
    suspend fun latestExpenseToday(ctx: Context): LatestExpenseRow? = withContext(Dispatchers.IO) {
        val start = maxOf(startOfTodayMillis(), getBudgetStartMillis(ctx))
        AppDatabase.getInstance(ctx).expenseDao()
            .getLatestExpenseTodayExcludingSkipped(start)
    }

    /**
     * ✅ REQUIRED: name expected by BudgetCardBinder
     * ✅ FIX: must respect budgetStartMillis
     */
    suspend fun getLatestExpenseTodayForSkip(ctx: Context): LatestExpenseRow? = withContext(Dispatchers.IO) {
        val start = maxOf(startOfTodayMillis(), getBudgetStartMillis(ctx))
        AppDatabase.getInstance(ctx).expenseDao()
            .getLatestExpenseTodayForSkip(start)
    }

    // ✅ My Budget (original user entered)
    fun getMyBudget(ctx: Context): Double {
        return when (getMode(ctx)) {
            MODE_WEEKLY -> getWeeklyLimit(ctx)
            MODE_MONTHLY -> getMonthlyLimit(ctx)
            else -> getDailyLimit(ctx)
        }
    }

    // ✅ Overall limit for the second section (weekly/monthly only)
    fun getOverallLimit(ctx: Context): Double {
        return when (getMode(ctx)) {
            MODE_WEEKLY -> getWeeklyLimit(ctx)
            MODE_MONTHLY -> getMonthlyLimit(ctx)
            else -> 0.0
        }
    }

    suspend fun getOverallSpent(ctx: Context): Double {
        return when (getMode(ctx)) {
            MODE_WEEKLY -> getWeekSpend(ctx)
            MODE_MONTHLY -> getMonthSpend(ctx)
            else -> 0.0
        }
    }

    // ✅ Daily progress uses per-day limit + today spend (always)
    suspend fun progressPct(ctx: Context): Int {
        val limit = getDailyLimit(ctx)
        if (!isEnabled(ctx) || limit <= 0) return 0
        val spent = getTodaySpend(ctx)
        return ((spent / limit) * 100).toInt().coerceIn(0, 100)
    }

    suspend fun dailySuggestion(ctx: Context): String {
        val limit = getDailyLimit(ctx)
        if (!isEnabled(ctx) || limit <= 0) return "Set a budget limit to control spending."

        val spent = getTodaySpend(ctx)
        val remaining = limit - spent

        return when {
            remaining >= limit * 0.5 ->
                "Good! You have ₹${remaining.toInt()} left for today."
            remaining in 1.0..(limit * 0.5) ->
                "Careful. Only ₹${remaining.toInt()} left today."
            remaining <= 0 ->
                "Limit crossed by ₹${abs(remaining).toInt()}. Reduce spending today."
            else ->
                "Track your spending wisely today."
        }
    }

    // ✅ Totals from budget start day to today (fills missing days with 0)
    // ✅ Totals from budget start day to today (fills missing days with 0)
    suspend fun getTotalsFromBudgetStartToToday(ctx: Context): List<BudgetDayTotal> =
        withContext(Dispatchers.IO) {

            val dao = AppDatabase.getInstance(ctx).expenseDao()

            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            // ✅ exact start time (when user pressed Save)
            val exactStartMillis = BudgetManager.getBudgetStartMillis(ctx).let {
                if (it > 0L) it else System.currentTimeMillis()
            }

            // ✅ labels should start from that day's 00:00 (for x-axis dates)
            val labelStartDay = startOfDayMillisFromMillis(exactStartMillis)

            // ✅ DB must start from exact time (so first day shows only after budget set)
            val raw = dao.getBudgetDailyTotalsFrom(exactStartMillis)
            val mapByDay = raw.associateBy { dayKey(it.dayStartMillis) }

            val oneDay = 24L * 60L * 60L * 1000L
            val daysBetween = ((todayStart - labelStartDay) / oneDay).toInt() + 1

            val result = ArrayList<BudgetDayTotal>(daysBetween)

            for (i in 0 until daysBetween) {
                val dayStart = labelStartDay + (i * oneDay)
                val key = dayKey(dayStart)
                val row = mapByDay[key]
                val total = row?.totalAmount ?: 0.0
                result.add(BudgetDayTotal(dayStart, total))
            }

            result
        }

    suspend fun getMaxExpenseLast7DaysForSkip(ctx: Context): LatestExpenseRow? = withContext(Dispatchers.IO) {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, -6)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        AppDatabase.getInstance(ctx).expenseDao()
            .getMaxExpenseSinceExcludingSkipped(cal.timeInMillis)
    }

    /**
 * ✅ Biggest expense today (not skipped) - used for Skip suggestion
 */
    suspend fun getMaxExpenseTodayForSkip(ctx: Context): LatestExpenseRow? = withContext(Dispatchers.IO) {
        val start = maxOf(startOfTodayMillis(), getBudgetStartMillis(ctx))
        val e = AppDatabase.getInstance(ctx).expenseDao()
            .getMaxExpenseTodayEntityForSkip(start)

        if (e != null) LatestExpenseRow(id = e.id, amount = e.amount) else null
    }

    /**
 * ✅ Auto-skip if any single transaction exceeds daily budget
 */
    suspend fun autoSkipIfSingleTxnExceedsDaily(ctx: Context) {
        if (!isEnabled(ctx)) return

        // ✅ fixed base per-day (set when user presses Save)
        val baseFixedPerDay = getInitialDailyLimit(ctx)

        // ✅ fallback if base not saved properly (prevents threshold becoming 0)
        val base = if (baseFixedPerDay > 0) baseFixedPerDay else getDailyLimit(ctx)

        if (base <= 0) return

        // ✅ ONLY triple time more (400 → 1200, so 500 will NOT skip)
        val threshold = base * 3.0

        // ✅ check only today's expenses (respect budget start too)
        val start = maxOf(startOfTodayMillis(), getBudgetStartMillis(ctx))

        val db = AppDatabase.getInstance(ctx)

        // ✅ biggest txn today that is NOT already skipped
        val maxTxn = db.expenseDao().getMaxExpenseTodayEntityForSkip(start) ?: return

        // ✅ STRICT CONDITION: skip only when >= threshold
        if (maxTxn.amount < threshold) return

        db.skippedExpenseDao().insert(SkippedExpense(expenseId = maxTxn.id))
    }

    



}
