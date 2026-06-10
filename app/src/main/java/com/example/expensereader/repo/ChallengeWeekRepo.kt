package com.example.expensereader.repo

import android.content.Context
import android.content.SharedPreferences
import com.example.expensereader.R
import com.example.expensereader.model.WeeklyChallenge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChallengeWeekRepo {

    data class WeekProgress(
        val currentOrder: Int = 1,      // 1->3days,2->5days,3->7days
        val target: Int = 0,
        val progress: Int = 0,
        val status: String = "ACTIVE",  // ACTIVE / ACCEPTED / COMPLETED
        val startDayMillis: Long = 0L   // ✅ NEW: start day of ACCEPT (midnight)
    )

    private val PREF = "weekly_challenge_local"
    private val K_INDEX = "index"                 // 0..49
    private val K_STATUS = "status"
    private val K_PROGRESS = "progress"
    private val K_TARGET = "target"
    private val K_ORDER = "order"
    private val K_START_DAY = "startDayMillis"    // ✅ NEW

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // ✅ helper used for startDayMillis
    private fun startOfDayMillis(t: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = t
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // ---- CSV loader ----
    private suspend fun loadChallenges(ctx: Context): List<WeeklyChallenge> = withContext(Dispatchers.IO) {
        val list = mutableListOf<WeeklyChallenge>()
        ctx.resources.openRawResource(R.raw.weekly_challenges).bufferedReader().useLines { lines ->
            lines.drop(1) // header
                .filter { it.isNotBlank() }
                .forEach { line ->
                    // CSV is simple: id,title,desc (no commas inside)
                    val parts = line.split(",", limit = 3)
                    if (parts.size >= 3) {
                        val id = parts[0].trim().toIntOrNull() ?: return@forEach
                        val title = parts[1].trim()
                        val desc = parts[2].trim()
                        list.add(
                            WeeklyChallenge(
                                id = id,
                                title = title,
                                description = desc,
                                emoji = "🏆",
                                rewardPoints = 50,
                                durationDays = 7
                            )
                        )
                    }
                }
        }
        list
    }

    // ---- public API used by HomeFragment ----
    suspend fun getThisWeekChallenge(ctx: Context? = null): Pair<WeeklyChallenge?, WeekProgress> {
        if (ctx == null) return Pair(null, WeekProgress())

        val p = prefs(ctx)
        val status = p.getString(K_STATUS, "ACTIVE") ?: "ACTIVE"
        val prog = p.getInt(K_PROGRESS, 0)
        val target = p.getInt(K_TARGET, 0)
        val order = p.getInt(K_ORDER, 1).coerceIn(1, 3)
        val startDay = p.getLong(K_START_DAY, 0L)

        val challenges = loadChallenges(ctx)
        if (challenges.isEmpty()) {
            return Pair(
                null,
                WeekProgress(
                    status = status,
                    progress = prog,
                    target = target,
                    currentOrder = order,
                    startDayMillis = startDay
                )
            )
        }

        val idx = p.getInt(K_INDEX, 0).coerceIn(0, challenges.size - 1)
        val ch = challenges[idx]

        return Pair(
            ch,
            WeekProgress(
                currentOrder = order,
                target = target,
                progress = prog,
                status = status,
                startDayMillis = startDay
            )
        )
    }

    fun acceptThisWeek(ctx: Context, target: Int, title: String) {
        val startDay = startOfDayMillis(System.currentTimeMillis())
        prefs(ctx).edit()
            .putString(K_STATUS, "ACCEPTED")
            .putInt(K_PROGRESS, 0)
            .putInt(K_TARGET, target)
            .putLong(K_START_DAY, startDay)   // ✅ NEW: used for tracking window
            .apply()
    }

    suspend fun updateProgress(ctx: Context, progress: Int, target: Int) {
        val p = prefs(ctx)
        val status = p.getString(K_STATUS, "ACTIVE") ?: "ACTIVE"
        if (!status.equals("ACCEPTED", ignoreCase = true)) return

        val newProgress = progress.coerceAtLeast(0)
        val newTarget = target.coerceAtLeast(1)
        val completed = newProgress >= newTarget

        p.edit()
            .putInt(K_PROGRESS, newProgress)
            .putInt(K_TARGET, newTarget)
            .putString(K_STATUS, if (completed) "COMPLETED" else "ACCEPTED")
            .apply()
    }

    suspend fun skipAndAdvanceThisWeek(ctx: Context) {
        val p = prefs(ctx)

        val challenges = loadChallenges(ctx)
        if (challenges.isEmpty()) return

        val current = p.getInt(K_INDEX, 0)
        val next = (current + 1) % challenges.size   // ✅ LOOP FOREVER

        // rotate order 1->2->3->1 (your 3/5/7 logic)
        val order = p.getInt(K_ORDER, 1).coerceIn(1, 3)
        val nextOrder = if (order == 3) 1 else (order + 1)

        p.edit()
            .putInt(K_INDEX, next)
            .putInt(K_ORDER, nextOrder)
            .putString(K_STATUS, "ACTIVE")
            .putInt(K_PROGRESS, 0)
            .putInt(K_TARGET, 0)
            .putLong(K_START_DAY, 0L) // ✅ reset start day when new challenge becomes ACTIVE
            .apply()
    }
}
