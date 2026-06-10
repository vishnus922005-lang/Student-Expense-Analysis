package com.example.expensereader.ui.profile

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.expensereader.R
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object RewardHistoryStore {

    private const val PREF = "reward_history"
    private const val KEY_ROWS = "rows"
    private const val MAX_ROWS = 5000

    private const val KEY_WEEK_SUMMARY_AWARDED = "week_summary_awarded"
    private const val KEY_MONTH_SUMMARY_AWARDED = "month_summary_awarded"

    enum class RowType { POINTS, STREAK }

    data class HistoryRow(
        val day: String,
        val source: String,
        val value: Int,
        val type: RowType
    )

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun todayKey(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun weekKeyNow(): String {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val week = cal.get(Calendar.WEEK_OF_YEAR)
        return "%04d-W%02d".format(year, week)
    }

    private fun monthKeyNow(): String {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        return "%04d-%02d".format(year, month)
    }

    fun isWeekEndToday(): Boolean {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
    }

    fun isMonthEndToday(): Boolean {
        val cal = Calendar.getInstance()
        val last = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return cal.get(Calendar.DAY_OF_MONTH) == last
    }

    fun shouldAwardWeekSummary(ctx: Context): Boolean {
        val key = weekKeyNow()
        return prefs(ctx).getString(KEY_WEEK_SUMMARY_AWARDED, null) != key
    }

    fun markWeekSummaryAwarded(ctx: Context) {
        prefs(ctx).edit().putString(KEY_WEEK_SUMMARY_AWARDED, weekKeyNow()).apply()
    }

    fun shouldAwardMonthSummary(ctx: Context): Boolean {
        val key = monthKeyNow()
        return prefs(ctx).getString(KEY_MONTH_SUMMARY_AWARDED, null) != key
    }

    fun markMonthSummaryAwarded(ctx: Context) {
        prefs(ctx).edit().putString(KEY_MONTH_SUMMARY_AWARDED, monthKeyNow()).apply()
    }

    fun cleanupLegacyTotalsAndDuplicates(ctx: Context) {
        val p = prefs(ctx)
        val old = p.getString(KEY_ROWS, "[]") ?: "[]"
        val arr = JSONArray(old)

        val map = LinkedHashMap<String, JSONObject>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val day = o.optString("day")
            val type = o.optString("type")
            val source = o.optString("source")
            val value = o.optInt("value", 0)

            if (source.contains("total", ignoreCase = true)) continue
            if (source.contains("Bonus (per 50 pts)", ignoreCase = true)) continue

            val key = "$day|$type|$source"
            map[key] = JSONObject().apply {
                put("day", day)
                put("type", type)
                put("source", source)
                put("value", value)
            }
        }

        val out = JSONArray()
        map.values.forEach { out.put(it) }

        p.edit().putString(KEY_ROWS, out.toString()).apply()
    }

    // ✅ totals excluding a given day (to avoid double conversion when rebuilding today)
    fun totalPointsExcludingDay(ctx: Context, day: String): Int =
        load(ctx).filter { it.type == RowType.POINTS && it.day != day }.sumOf { it.value }

    fun upsertTodayBreakdown(
        ctx: Context,
        day: String = todayKey(),

        weeklyChallengePoints: Int,
        dailyBudgetPoints: Int,
        dailySavingPoints: Int,

        weeklyChallengeStreak: Int,
        savingStreak: Int,
        weeklyBudgetStreak: Int,
        monthlyBudgetStreak: Int,
        bonusStreak: Int,

        weeklyChallengeSource: String = "Weekly Challenge",
        savingStreakSource: String = "Saving Streak",
        weeklyBudgetSource: String = "Weekly Budget Met",
        monthlyBudgetSource: String = "Monthly Budget Met"
    ) {
        val p = prefs(ctx)
        val old = p.getString(KEY_ROWS, "[]") ?: "[]"
        val arr = JSONArray(old)

        val kept = LinkedHashMap<String, JSONObject>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val oDay = o.optString("day")
            val oType = o.optString("type")
            val oSource = o.optString("source")

            if (oSource.contains("total", ignoreCase = true)) continue
            if (oSource.contains("Bonus (per 50 pts)", ignoreCase = true)) continue
            if (oDay == day) continue

            val key = "$oDay|$oType|$oSource"
            kept[key] = o
        }

        data class K(val type: RowType, val source: String)
        val todayAgg = LinkedHashMap<K, Int>()

        fun add(type: RowType, source: String, value: Int) {
            if (value == 0) return
            val k = K(type, source)
            todayAgg[k] = (todayAgg[k] ?: 0) + value
        }

        // ✅ Today earned points
        add(RowType.POINTS, "Weekly Challenge", weeklyChallengePoints)
        add(RowType.POINTS, "Green Day (Daily Budget)", dailyBudgetPoints)
        add(RowType.POINTS, "Saving Logged Today", dailySavingPoints)

        // ✅ Conversion: 50 points -> 1 streak  (deduct from points)
        if (bonusStreak > 0) {
            add(RowType.POINTS, "Converted to Streak (50 pts → 1)", -(bonusStreak * 50))
        }

        // ✅ Streak rows (can be negative too)
        add(RowType.STREAK, weeklyChallengeSource, weeklyChallengeStreak)
        add(RowType.STREAK, savingStreakSource, savingStreak)
        add(RowType.STREAK, weeklyBudgetSource, weeklyBudgetStreak)
        add(RowType.STREAK, monthlyBudgetSource, monthlyBudgetStreak)

        if (bonusStreak > 0) {
            add(RowType.STREAK, "Bonus (50 pts → 1 streak)", bonusStreak)
        }

        val out = JSONArray()

        // today first
        todayAgg.forEach { (k, value) ->
            out.put(
                JSONObject().apply {
                    put("day", day)
                    put("source", k.source)
                    put("value", value)
                    put("type", k.type.name)
                }
            )
        }

        // older rows after
        kept.values.forEach { out.put(it) }

        val limited = JSONArray()
        val limit = minOf(MAX_ROWS, out.length())
        for (i in 0 until limit) limited.put(out.getJSONObject(i))

        p.edit().putString(KEY_ROWS, limited.toString()).apply()
    }

    fun load(ctx: Context): List<HistoryRow> {
        val json = prefs(ctx).getString(KEY_ROWS, "[]") ?: "[]"
        val arr = JSONArray(json)

        val list = ArrayList<HistoryRow>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val typeStr = o.optString("type", RowType.POINTS.name)
            val type = runCatching { RowType.valueOf(typeStr) }.getOrElse { RowType.POINTS }
            list.add(
                HistoryRow(
                    day = o.optString("day"),
                    source = o.optString("source"),
                    value = o.optInt("value", 0),
                    type = type
                )
            )
        }
        return list
    }

    fun pointsRows(ctx: Context) = load(ctx).filter { it.type == RowType.POINTS }
    fun streakRows(ctx: Context) = load(ctx).filter { it.type == RowType.STREAK }

    fun totalPoints(ctx: Context): Int = pointsRows(ctx).sumOf { it.value }
    fun totalStreak(ctx: Context): Int = streakRows(ctx).sumOf { it.value }
}

class RewardHistoryRowAdapter : RecyclerView.Adapter<RewardHistoryRowAdapter.VH>() {

    private val items = mutableListOf<RewardHistoryStore.HistoryRow>()

    fun submit(rows: List<RewardHistoryStore.HistoryRow>) {
        items.clear()
        items.addAll(rows)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reward_history_row, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        holder.tvDate.text = r.day
        holder.tvSource.text = r.source

        val prefix = if (r.value > 0) "+" else ""
        holder.tvValue.text = prefix + r.value.toString()

        // ✅ both points and streak: + green, - red
        val ctx = holder.itemView.context
        val colorRes = when {
            r.value > 0 -> android.R.color.holo_green_dark
            r.value < 0 -> android.R.color.holo_red_dark
            else -> android.R.color.darker_gray
        }
        holder.tvValue.setTextColor(ContextCompat.getColor(ctx, colorRes))
    }

    override fun getItemCount(): Int = items.size

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val tvSource: TextView = itemView.findViewById(R.id.tvSource)
        val tvValue: TextView = itemView.findViewById(R.id.tvValue)
    }
}
