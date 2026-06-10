package com.example.expensereader.ui.analysis

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.expensereader.R
import com.example.expensereader.db.AppDatabase
import com.example.expensereader.model.ChartDayTotal
import com.example.expensereader.ui.category.SimpleItemSelectedListener
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import android.widget.LinearLayout


class MonthlyFragment : Fragment(R.layout.fragment_monthly) {

    private lateinit var spYear: Spinner
    private lateinit var spMonth: Spinner

    private lateinit var tvSelectedMonth: TextView
    private lateinit var barChartDaily: BarChart

    private lateinit var rvMonthlySummary: RecyclerView
    private lateinit var summaryAdapter: WeeklySummaryAdapter

    private lateinit var tvWeekendPct: TextView
    private lateinit var tvWeekdayPct: TextView
    private lateinit var vWeekendBar: View
    private lateinit var vWeekdayBar: View
    private lateinit var tvWeekendInfo: TextView
    private lateinit var tvWeekdayInfo: TextView

    private lateinit var rvWeekSplit: RecyclerView
    private lateinit var weekSplitAdapter: WeekSplitAdapter
    private lateinit var tvWeekSplitHeading: TextView


    private lateinit var vpSuggestions: ViewPager2
    private lateinit var tabDots: TabLayout
    private lateinit var suggestionAdapter: SuggestionPagerAdapter
    private var dotsMediator: TabLayoutMediator? = null

    private lateinit var years: List<Int>

    private val months = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    private var firstYear: Int = 0
    private var firstMonth: Int = 1

    private var monthNumbersShown: List<Int> = (1..12).toList()
    private var suppress = false

    private val TAG = "MonthlyFragment"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        spYear = view.findViewById(R.id.spYear)
        spMonth = view.findViewById(R.id.spMonth)

        tvSelectedMonth = view.findViewById(R.id.tvSelectedMonth)
        barChartDaily = view.findViewById(R.id.barChartDaily)

        // ✅ week split list UI (new)
        tvWeekSplitHeading = view.findViewById(R.id.tvWeekSplitHeading)
        rvWeekSplit = view.findViewById(R.id.rvWeekSplit)
        weekSplitAdapter = WeekSplitAdapter()
        rvWeekSplit.layoutManager = GridLayoutManager(requireContext(), 1)
        rvWeekSplit.adapter = weekSplitAdapter
        rvWeekSplit.isNestedScrollingEnabled = false

        // ✅ summary
        rvMonthlySummary = view.findViewById(R.id.rvMonthlySummary)
        summaryAdapter = WeeklySummaryAdapter()
        rvMonthlySummary.layoutManager = GridLayoutManager(requireContext(), 2)
        rvMonthlySummary.adapter = summaryAdapter
        rvMonthlySummary.isNestedScrollingEnabled = false

        // ✅ suggestions
        vpSuggestions = view.findViewById(R.id.vpSuggestions)
        tabDots = view.findViewById(R.id.tabDots)

        suggestionAdapter = SuggestionPagerAdapter()
        vpSuggestions.adapter = suggestionAdapter
        vpSuggestions.offscreenPageLimit = 1

        dotsMediator?.detach()
        dotsMediator = TabLayoutMediator(tabDots, vpSuggestions) { _, _ -> }
        dotsMediator?.attach()

        setupBarChart()
        setupYearMonthSpinners()
    }


    private fun setupBarChart() {
        barChartDaily.description.isEnabled = false
        barChartDaily.setDrawGridBackground(false)
        barChartDaily.setDrawBarShadow(false)
        barChartDaily.setPinchZoom(false)
        barChartDaily.setScaleEnabled(false)

        barChartDaily.axisRight.isEnabled = false

        val df = DecimalFormat("#,##0.##")

        barChartDaily.axisLeft.apply {
            axisMinimum = 0f
            setDrawGridLines(true)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return "₹" + df.format(value.toDouble())
                }
            }
        }

        barChartDaily.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            granularity = 1f
            labelRotationAngle = -45f
            setAvoidFirstLastClipping(true)
        }

        barChartDaily.legend.isEnabled = false
    }

    private fun setupYearMonthSpinners() {
        val now = LocalDate.now()
        val currentYear = now.year
        val currentMonth = now.monthValue
        val zone = ZoneId.systemDefault()

        val dao = AppDatabase.getInstance(requireContext()).expenseDao()

        viewLifecycleOwner.lifecycleScope.launch {
            val firstMillis = withContext(Dispatchers.IO) { dao.getFirstExpenseDate() }

            if (firstMillis != null) {
                val d = Instant.ofEpochMilli(firstMillis).atZone(zone).toLocalDate()
                firstYear = d.year
                firstMonth = d.monthValue
            } else {
                firstYear = currentYear
                firstMonth = currentMonth
            }

            years = listOf(-1) + (firstYear..currentYear).toList()

            val yearAdapter = ArrayAdapter(
                requireContext(),
                R.layout.item_spinner_black,
                years.map { if (it == -1) "All" else it.toString() }
            ).also { it.setDropDownViewResource(R.layout.item_spinner_black_dropdown) }

            spYear.adapter = yearAdapter

            spYear.onItemSelectedListener = SimpleItemSelectedListener {
                if (suppress) return@SimpleItemSelectedListener

                val y = years.getOrNull(spYear.selectedItemPosition) ?: currentYear

                suppress = true
                rebuildMonthSpinnerForYear(y, preferMonth = currentMonth)
                suppress = false

                val m = monthNumbersShown.getOrNull(spMonth.selectedItemPosition) ?: 0
                loadForSelection(y, m)
            }

            spMonth.onItemSelectedListener = SimpleItemSelectedListener {
                if (suppress) return@SimpleItemSelectedListener

                val y = years.getOrNull(spYear.selectedItemPosition) ?: currentYear
                val m = monthNumbersShown.getOrNull(spMonth.selectedItemPosition) ?: 0
                loadForSelection(y, m)
            }

            // Initial
            suppress = true
            val defaultYearIndex = years.indexOf(currentYear).let { if (it >= 0) it else 0 }
            spYear.setSelection(defaultYearIndex)

            val selectedYear = years.getOrNull(spYear.selectedItemPosition) ?: currentYear
            rebuildMonthSpinnerForYear(selectedYear, preferMonth = currentMonth)
            suppress = false

            val initY = years.getOrNull(spYear.selectedItemPosition) ?: currentYear
            val initM = monthNumbersShown.getOrNull(spMonth.selectedItemPosition) ?: 0
            loadForSelection(initY, initM)
        }
    }

    private fun rebuildMonthSpinnerForYear(selectedYear: Int, preferMonth: Int) {
        val now = LocalDate.now()
        val currentYear = now.year
        val currentMonth = now.monthValue

        val startM: Int
        val endM: Int

        if (selectedYear == -1) {
            startM = 1
            endM = 12
        } else {
            startM = if (selectedYear == firstYear) firstMonth else 1
            endM = if (selectedYear == currentYear) currentMonth else 12
        }

        monthNumbersShown = listOf(0) + (startM..endM).toList()

        val monthNamesShown = monthNumbersShown.map { m ->
            if (m == 0) "All" else months[m - 1]
        }

        val monthAdapter = ArrayAdapter(
            requireContext(),
            R.layout.item_spinner_black,
            monthNamesShown
        ).also { it.setDropDownViewResource(R.layout.item_spinner_black_dropdown) }

        spMonth.adapter = monthAdapter

        val wanted = if (preferMonth in startM..endM) preferMonth else 0
        val idx = monthNumbersShown.indexOf(wanted).takeIf { it >= 0 } ?: 0
        spMonth.setSelection(idx)
    }

    private fun loadForSelection(year: Int, month: Int) {
        val zone = ZoneId.systemDefault()
        val dao = AppDatabase.getInstance(requireContext()).expenseDao()

        viewLifecycleOwner.lifecycleScope.launch {

            var label = ""

            val (fromMillis, endMillis) = withContext(Dispatchers.IO) {

                if (year == -1 && month == 0) {
                    val start = dao.getFirstExpenseDate() ?: System.currentTimeMillis()
                    val end = System.currentTimeMillis()
                    label = "All time"
                    return@withContext start to end
                }

                if (year != -1 && month == 0) {
                    val start = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
                    val end = LocalDate.of(year, 12, 31).plusDays(1)
                        .atStartOfDay(zone).toInstant().toEpochMilli() - 1
                    label = "Year $year"
                    return@withContext start to end
                }

                if (year == -1 && month != 0) {
                    val startYear = firstYear
                    val endYear = LocalDate.now().year

                    val start = LocalDate.of(startYear, month, 1)
                        .atStartOfDay(zone).toInstant().toEpochMilli()

                    val end = LocalDate.of(endYear, month, 1)
                        .withDayOfMonth(YearMonth.of(endYear, month).lengthOfMonth())
                        .plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

                    label = "All years • ${months[month - 1]}"
                    return@withContext start to end
                }

                // ✅ Specific year + specific month
                val ym = YearMonth.of(year, month)
                val startDate = ym.atDay(1)
                val endDate = ym.atEndOfMonth()

                val fmt = DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())
                label = startDate.format(fmt)

                val start = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                start to end
            }

            // ✅ title
            tvSelectedMonth.text = label

            // ✅ day-wise totals from DB
            val rows: List<ChartDayTotal> = withContext(Dispatchers.IO) {
                dao.getDayWiseTotalsBetween(fromMillis, endMillis)
            }

            // ✅ BAR CHART
            if (year != -1 && month != 0) {
                showWeeklyTotalsBarChartForMonth(year, month, rows)
            } else {
                showDailyBarChartWithZeros(fromMillis, endMillis, rows)
            }

            // ✅ Weekdays vs Weekend (FOR ALL WEEKS) — only show for specific month
            if (year != -1 && month != 0) {
                tvWeekSplitHeading.visibility = View.VISIBLE
                rvWeekSplit.visibility = View.VISIBLE
                updateWeekSplitListForMonth(year, month, fromMillis, endMillis)
            } else {
                tvWeekSplitHeading.visibility = View.GONE
                rvWeekSplit.visibility = View.GONE
            }

            // ✅ Suggestions
            val startDate = Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate()
            val endDate = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate()
            val daysCount =
                (java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1)
                    .toInt()
                    .coerceAtLeast(1)

            val totalSpent = rows.sumOf { it.total }
            val avgPerDay = totalSpent / daysCount.toDouble()

            val dfDay = DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())
            val maxRow = rows.maxByOrNull { it.total }
            val minRow = rows.filter { it.total > 0.0 }.minByOrNull { it.total }

            val maxText = if (maxRow != null) {
                val d = Instant.ofEpochMilli(maxRow.dayStart).atZone(zone).toLocalDate()
                "Highest day: ${d.format(dfDay)} • ₹" + String.format(Locale.US, "%,.0f", maxRow.total)
            } else "Highest day: —"

            val minText = if (minRow != null) {
                val d = Instant.ofEpochMilli(minRow.dayStart).atZone(zone).toLocalDate()
                "Lowest day: ${d.format(dfDay)} • ₹" + String.format(Locale.US, "%,.0f", minRow.total)
            } else "Lowest day: —"

            val suggestionList = listOf(
                SuggestionItem(
                    "You spent ₹" + String.format(Locale.US, "%,.0f", totalSpent) +
                            " in this period. Average ₹" + String.format(Locale.US, "%,.0f", avgPerDay) + "/day."
                ),
                SuggestionItem(maxText),
                SuggestionItem(minText),
                SuggestionItem("Try reducing small spends under ₹50 — they add up quickly."),
                SuggestionItem("Set a monthly limit and track daily to stay in control.")
            )

            suggestionAdapter.submitList(suggestionList)
            attachDots()

            // ✅ top merchant
            val topMerchantName: String? = withContext(Dispatchers.IO) {
                try {
                    dao.getTopMerchantBetween(fromMillis, endMillis).merchant
                } catch (e: Exception) {
                    null
                }
            }

            // ✅ summary
            updateMonthlySummary(
                fromMillis = fromMillis,
                endMillis = endMillis,
                dayRows = rows,
                topMerchantName = topMerchantName
            )

            Log.d(TAG, "Monthly range: from=$fromMillis to=$endMillis (year=$year month=$month)")
        }
    }


    // ------------------ ✅ DOTS ------------------

    private fun attachDots() {
        dotsMediator?.detach()

        dotsMediator = TabLayoutMediator(tabDots, vpSuggestions) { tab, _ ->
            val dotView = layoutInflater.inflate(R.layout.item_dot, tabDots, false)
            tab.customView = dotView
        }
        dotsMediator?.attach()

        updateDotsSelected(0)

        vpSuggestions.unregisterOnPageChangeCallback(pageCallback)
        vpSuggestions.registerOnPageChangeCallback(pageCallback)
    }

    private val pageCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            updateDotsSelected(position)
        }
    }

    private fun updateDotsSelected(selectedPos: Int) {
        for (i in 0 until tabDots.tabCount) {
            val tab = tabDots.getTabAt(i)
            val img = tab?.customView?.findViewById<ImageView>(R.id.imgDot)
            img?.setImageResource(
                if (i == selectedPos) R.drawable.dot_selected else R.drawable.dot_unselected
            )
        }
    }

    private fun showDailyBarChartWithZeros(fromMillis: Long, endMillis: Long, rows: List<ChartDayTotal>) {
        val zone = ZoneId.systemDefault()

        val startDate = Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate()
        val endDate = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate()

        val map = HashMap<Long, Double>()
        for (r in rows) {
            val localDate = Instant.ofEpochMilli(r.dayStart).atZone(zone).toLocalDate()
            val key = localDate.atStartOfDay(zone).toInstant().toEpochMilli()
            map[key] = (map[key] ?: 0.0) + r.total
        }

        val dayFmt = SimpleDateFormat("dd MMM", Locale.getDefault())
        val labels = mutableListOf<String>()
        val entries = mutableListOf<BarEntry>()

        var d = startDate
        var index = 0

        while (!d.isAfter(endDate)) {
            val key = d.atStartOfDay(zone).toInstant().toEpochMilli()
            val total = (map[key] ?: 0.0).toFloat()

            labels.add(dayFmt.format(Date(key)))
            entries.add(BarEntry(index.toFloat(), total))

            index++
            d = d.plusDays(1)
        }

        if (entries.isEmpty()) {
            barChartDaily.clear()
            barChartDaily.invalidate()
            return
        }

        val dataSet = BarDataSet(entries, "Daily Spend").apply {
            valueTextSize = 10f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value == 0f) "" else value.toInt().toString()
                }
            }
        }

        val data = BarData(dataSet).apply { barWidth = 0.7f }
        barChartDaily.data = data

        barChartDaily.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val i = value.toInt()
                return if (i in labels.indices) labels[i] else ""
            }
        }

        barChartDaily.xAxis.apply {
            setCenterAxisLabels(false)
            granularity = 1f
            isGranularityEnabled = true
            labelCount = labels.size
            setAvoidFirstLastClipping(false)
        }

        barChartDaily.setFitBars(true)
        barChartDaily.xAxis.axisMinimum = -0.5f
        barChartDaily.xAxis.axisMaximum = entries.size - 0.5f

        barChartDaily.setExtraOffsets(8f, 0f, 8f, 10f)

        barChartDaily.invalidate()
        barChartDaily.animateY(600)
    }

    private fun money(v: Double): String = "₹" + String.format(Locale.US, "%,.2f", v)

    private fun showWeeklyTotalsBarChartForMonth(
        year: Int,
        month: Int,
        dayRows: List<ChartDayTotal>
    ) {
        val zone = ZoneId.systemDefault()

        // Map: dayStartMillis -> total
        val dayMap = HashMap<Long, Double>()
        for (r in dayRows) {
            val d = Instant.ofEpochMilli(r.dayStart).atZone(zone).toLocalDate()
            val key = d.atStartOfDay(zone).toInstant().toEpochMilli()
            dayMap[key] = (dayMap[key] ?: 0.0) + r.total
        }

        val ym = YearMonth.of(year, month)
        val startOfMonth = ym.atDay(1)
        val endOfMonth = ym.atEndOfMonth()

        // Build Week1..WeekN within that month (Mon..Sun style based on your WeeklyFragment logic: end on Sunday)
        val weekTotals = mutableListOf<Double>()
        val weekLabels = mutableListOf<String>()

        var start = startOfMonth
        var weekNo = 1

        while (!start.isAfter(endOfMonth)) {
            val dow = start.dayOfWeek.value // Mon=1 .. Sun=7
            val daysToSunday = (7 - dow).toLong()
            var end = start.plusDays(daysToSunday)
            if (end.isAfter(endOfMonth)) end = endOfMonth

            // Sum all days in this week range
            var sum = 0.0
            var d = start
            while (!d.isAfter(end)) {
                val key = d.atStartOfDay(zone).toInstant().toEpochMilli()
                sum += (dayMap[key] ?: 0.0)
                d = d.plusDays(1)
            }

            weekTotals.add(sum)
            weekLabels.add("W$weekNo")

            weekNo++
            start = end.plusDays(1)
        }

        // Create bar entries
        val entries = weekTotals.mapIndexed { i, total ->
            BarEntry(i.toFloat(), total.toFloat())
        }

        if (entries.isEmpty()) {
            barChartDaily.clear()
            barChartDaily.invalidate()
            return
        }

        val dataSet = BarDataSet(entries, "Weekly Total").apply {
            valueTextSize = 10f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value == 0f) "" else value.toInt().toString()
                }
            }
        }

        barChartDaily.data = BarData(dataSet).apply { barWidth = 0.6f }

        // X labels: W1..W5
        barChartDaily.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val i = value.toInt()
                return if (i in weekLabels.indices) weekLabels[i] else ""
            }
        }

        barChartDaily.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            isGranularityEnabled = true
            labelRotationAngle = 0f
            setDrawGridLines(false)
            setAvoidFirstLastClipping(true)
        }

        barChartDaily.axisLeft.axisMinimum = 0f
        barChartDaily.axisRight.isEnabled = false

        barChartDaily.setFitBars(true)
        barChartDaily.xAxis.axisMinimum = -0.5f
        barChartDaily.xAxis.axisMaximum = entries.size - 0.5f

        barChartDaily.legend.isEnabled = false
        barChartDaily.description.isEnabled = false

        barChartDaily.invalidate()
        barChartDaily.animateY(600)
    }


    private fun updateMonthlySummary(
        fromMillis: Long,
        endMillis: Long,
        dayRows: List<ChartDayTotal>,
        topMerchantName: String?
    ) {
        val zone = ZoneId.systemDefault()

        val startDate = Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate()
        val endDate = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate()
        val daysCount =
            (java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1)
                .toInt().coerceAtLeast(1)

        // ✅ Total from DB rows (days that have txns)
        val total = dayRows.sumOf { it.total }

        // ✅ Daily average based on full range days
        val dailyAvg = total / daysCount.toDouble()

        // ✅ Group by "week of month" (Week 1..Week N based on your same Sunday-ending logic)
        // Build week ranges from startDate..endDate
        data class WeekRange(val weekNo: Int, val start: LocalDate, val end: LocalDate)

        val ranges = mutableListOf<WeekRange>()
        var cursor = startDate
        var weekNo = 1

        while (!cursor.isAfter(endDate)) {
            val dow = cursor.dayOfWeek.value // Mon=1..Sun=7
            val daysToSunday = (7 - dow).toLong()
            var weekEnd = cursor.plusDays(daysToSunday)
            if (weekEnd.isAfter(endDate)) weekEnd = endDate

            ranges.add(WeekRange(weekNo, cursor, weekEnd))

            weekNo++
            cursor = weekEnd.plusDays(1)
        }

        // Map day totals by LocalDate
        val dayMap: Map<LocalDate, Double> = dayRows.associate { r ->
            Instant.ofEpochMilli(r.dayStart).atZone(zone).toLocalDate() to r.total
        }

        // Sum totals per week range
        data class WeekTotal(val range: WeekRange, val total: Double)

        val weekTotals = ranges.map { wr ->
            var sum = 0.0
            var d = wr.start
            while (!d.isAfter(wr.end)) {
                sum += (dayMap[d] ?: 0.0)
                d = d.plusDays(1)
            }
            WeekTotal(wr, sum)
        }

        val maxWeek = weekTotals.maxByOrNull { it.total }
        val minWeek = weekTotals.filter { it.total > 0.0 }.minByOrNull { it.total }

        val fmt = DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())

        val highestWeekText = if (maxWeek != null) {
            "Week ${maxWeek.range.weekNo} • ${money(maxWeek.total)}"
        } else "—"

        val lowestWeekText = if (minWeek != null) {
            "Week ${minWeek.range.weekNo} • ${money(minWeek.total)}"
        } else "—"


        val merchantText = topMerchantName?.takeIf { it.isNotBlank() } ?: "—"

        val items = listOf(
            SummaryItem("Total spent", money(total)),
            SummaryItem("Daily average", money(dailyAvg)),
            SummaryItem("Highest week", highestWeekText),
            SummaryItem("Lowest week", lowestWeekText),
            SummaryItem("Highest merchant", merchantText),
        )

        summaryAdapter.submitList(items)
    }


    private fun updateWeekendWeekdaySplit(fromMillis: Long, endMillis: Long) {
        val dao = AppDatabase.getInstance(requireContext()).expenseDao()

        viewLifecycleOwner.lifecycleScope.launch {
            val rows = withContext(Dispatchers.IO) {
                dao.getWeekendWeekdaySummaryBetween(fromMillis, endMillis)
            }

            var weekendAmt = 0.0
            var weekdayAmt = 0.0
            var weekendTxn = 0
            var weekdayTxn = 0

            for (r in rows) {
                if (r.isWeekend == 1) {
                    weekendAmt = r.totalAmount
                    weekendTxn = r.txnCount
                } else {
                    weekdayAmt = r.totalAmount
                    weekdayTxn = r.txnCount
                }
            }

            val total = (weekendAmt + weekdayAmt).takeIf { it > 0 } ?: 1.0
            val weekendPct = (weekendAmt / total) * 100.0
            val weekdayPct = 100.0 - weekendPct

            // ✅ % at ends
            tvWeekendPct.text = String.format(Locale.US, "%.0f%%", weekendPct)
            tvWeekdayPct.text = String.format(Locale.US, "%.0f%%", weekdayPct)

            // ✅ Amount + Txn
            tvWeekendInfo.text =
                "₹" + String.format(Locale.US, "%,.0f", weekendAmt) + " • $weekendTxn txns"
            tvWeekdayInfo.text =
                "₹" + String.format(Locale.US, "%,.0f", weekdayAmt) + " • $weekdayTxn txns"

            // ✅ Bar weights (avoid 0 width)
            val wWeight = (weekendPct / 100.0).toFloat().coerceIn(0.02f, 0.98f)
            val dWeight = 1f - wWeight

            // ✅ FIX: use variables (no "Variable expected")
            val lpWeekend = vWeekendBar.layoutParams as LinearLayout.LayoutParams
            lpWeekend.weight = wWeight
            vWeekendBar.layoutParams = lpWeekend

            val lpWeekday = vWeekdayBar.layoutParams as LinearLayout.LayoutParams
            lpWeekday.weight = dWeight
            vWeekdayBar.layoutParams = lpWeekday

            vWeekendBar.requestLayout()
            vWeekdayBar.requestLayout()
        }
    }

    private fun updateWeekSplitListForMonth(year: Int, month: Int, fromMillis: Long, endMillis: Long) {
        val zone = ZoneId.systemDefault()
        val dao = AppDatabase.getInstance(requireContext()).expenseDao()

        viewLifecycleOwner.lifecycleScope.launch {
            val txns = withContext(Dispatchers.IO) {
                dao.getTxnsBetween(fromMillis, endMillis)
            }

            val ym = YearMonth.of(year, month)
            val startOfMonth = ym.atDay(1)
            val endOfMonth = ym.atEndOfMonth()

            data class WeekRange(val weekNo: Int, val start: LocalDate, val end: LocalDate)

            // ✅ build week ranges (same logic as WeeklyFragment: start -> Sunday)
            val ranges = mutableListOf<WeekRange>()
            var start = startOfMonth
            var weekNo = 1

            while (!start.isAfter(endOfMonth)) {
                val dow = start.dayOfWeek.value // Mon=1..Sun=7
                val daysToSunday = (7 - dow).toLong()
                var end = start.plusDays(daysToSunday)
                if (end.isAfter(endOfMonth)) end = endOfMonth

                ranges.add(WeekRange(weekNo, start, end))

                weekNo++
                start = end.plusDays(1)
            }

            // ✅ If current month/year selected: hide weeks not started yet
            val today = LocalDate.now(zone)
            val isCurrentMonth = (today.year == year && today.monthValue == month)

            val visibleRanges = if (isCurrentMonth) {
                ranges.filter { !it.start.isAfter(today) }   // keep only started weeks
            } else {
                ranges
            }

            // init result list (only visible weeks)
            val result = visibleRanges.map { r ->
                WeekSplitItem(
                    weekNo = r.weekNo,
                    weekendAmt = 0.0,
                    weekendTxn = 0,
                    weekdayAmt = 0.0,
                    weekdayTxn = 0
                )
            }.toMutableList()

            // helper: week index by date
            fun findVisibleWeekIndex(d: LocalDate): Int {
                return visibleRanges.indexOfFirst { !d.isBefore(it.start) && !d.isAfter(it.end) }
            }

            // accumulate per visible week
            for (t in txns) {
                val d = Instant.ofEpochMilli(t.date).atZone(zone).toLocalDate()
                val idx = findVisibleWeekIndex(d)
                if (idx == -1) continue

                val isWeekend = (d.dayOfWeek.value == 6 || d.dayOfWeek.value == 7) // Sat/Sun
                val old = result[idx]

                result[idx] = if (isWeekend) {
                    old.copy(
                        weekendAmt = old.weekendAmt + t.amount,
                        weekendTxn = old.weekendTxn + 1
                    )
                } else {
                    old.copy(
                        weekdayAmt = old.weekdayAmt + t.amount,
                        weekdayTxn = old.weekdayTxn + 1
                    )
                }
            }

            // ✅ OPTIONAL (recommended): hide weeks with 0 spend completely
            val finalList = result.filter { (it.weekendAmt + it.weekdayAmt) > 0.0 }

            weekSplitAdapter.submitList(finalList)
        }
    }

}
