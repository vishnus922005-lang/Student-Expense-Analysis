package com.example.expensereader.ui.analysis

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.expensereader.R
import com.example.expensereader.db.AppDatabase
import com.example.expensereader.db.CategorySummaryRow
import com.example.expensereader.model.CategorySummary
import com.example.expensereader.model.ChartDayTotal
import com.example.expensereader.ui.category.SimpleItemSelectedListener
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import androidx.viewpager2.widget.ViewPager2
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
import kotlin.math.min
import android.view.LayoutInflater
import android.widget.ImageView



class WeeklyFragment : Fragment(R.layout.fragment_weekly) {

    private lateinit var spYear: Spinner
    private lateinit var spMonth: Spinner
    private lateinit var spWeek: Spinner

    private lateinit var pieWeekly: PieChart
    private lateinit var rvWeeklyCategory: RecyclerView
    private lateinit var categoryAdapter: CategorySummaryAdapter
    private lateinit var tvSelectedWeek: TextView
    private lateinit var rvWeeklySummary: RecyclerView
    private lateinit var summaryAdapter: WeeklySummaryAdapter
    private lateinit var vpSuggestions: ViewPager2
    private lateinit var tabDots: TabLayout
    private lateinit var suggestionAdapter: SuggestionPagerAdapter
    private var dotsMediator: TabLayoutMediator? = null
    private var otherCategoriesColor: Int = Color.LTGRAY




    private lateinit var barChartDaily: BarChart

    private lateinit var years: List<Int>

    private val months = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    private var firstYear: Int = 0
    private var firstMonth: Int = 1

    private var monthNumbersShown: List<Int> = (1..12).toList()

    // ✅ Week list will be dynamic (Week1..WeekN). No "All".
    private var weekNumbersShown: List<Int> = emptyList()

    // ✅ Store actual calendar week ranges
    private data class WeekRange(val weekNo: Int, val start: LocalDate, val end: LocalDate)
    private var weekRanges: List<WeekRange> = emptyList()

    private var suppress = false

    private val TAG = "WeeklyFragment"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        otherCategoriesColor = androidx.core.content.ContextCompat.getColor(
            requireContext(),
            R.color.other_categories_color
        )


        spYear = view.findViewById(R.id.spYear)
        spMonth = view.findViewById(R.id.spMonth)
        spWeek = view.findViewById(R.id.spWeek)

        barChartDaily = view.findViewById(R.id.barChartDaily)
        pieWeekly = view.findViewById(R.id.pieWeekly)
        rvWeeklyCategory = view.findViewById(R.id.rvWeeklyCategory)
        tvSelectedWeek = view.findViewById(R.id.tvSelectedWeek)

        categoryAdapter = CategorySummaryAdapter()
        rvWeeklyCategory.layoutManager = GridLayoutManager(requireContext(), 2)
        rvWeeklyCategory.adapter = categoryAdapter
        rvWeeklyCategory.isNestedScrollingEnabled = false

        rvWeeklySummary = view.findViewById(R.id.rvWeeklySummary)
        summaryAdapter = WeeklySummaryAdapter()
        rvWeeklySummary.layoutManager = GridLayoutManager(requireContext(), 2)
        rvWeeklySummary.adapter = summaryAdapter
        rvWeeklySummary.isNestedScrollingEnabled = false
        vpSuggestions = view.findViewById(R.id.vpSuggestions)
        tabDots = view.findViewById(R.id.tabDots)

        suggestionAdapter = SuggestionPagerAdapter()
        vpSuggestions.adapter = suggestionAdapter

        // ✅ show ONE card at a time, with smooth swipe
        vpSuggestions.offscreenPageLimit = 1

        // ✅ dots
        dotsMediator?.detach()
        dotsMediator = TabLayoutMediator(tabDots, vpSuggestions) { _, _ -> }
        dotsMediator?.attach()



        setupBarChart()
        setupYearMonthWeekSpinners()
    }

    // ✅ Rupee formatting on Y axis + basic chart setup
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

    private fun setupYearMonthWeekSpinners() {
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

            // Year selected
            spYear.onItemSelectedListener = SimpleItemSelectedListener {
                if (suppress) return@SimpleItemSelectedListener

                val y = years.getOrNull(spYear.selectedItemPosition) ?: currentYear

                suppress = true
                rebuildMonthSpinnerForYear(y, preferMonth = currentMonth)

                val m = monthNumbersShown.getOrNull(spMonth.selectedItemPosition) ?: 0
                rebuildWeekSpinnerForYearMonth(y, m)
                selectDefaultWeek(y, m)

                suppress = false

                val w = weekNumbersShown.getOrNull(spWeek.selectedItemPosition) ?: -1
                loadForSelection(y, m, w)
            }

            // Month selected
            spMonth.onItemSelectedListener = SimpleItemSelectedListener {
                if (suppress) return@SimpleItemSelectedListener

                val y = years.getOrNull(spYear.selectedItemPosition) ?: currentYear
                val m = monthNumbersShown.getOrNull(spMonth.selectedItemPosition) ?: 0

                suppress = true
                rebuildWeekSpinnerForYearMonth(y, m)
                selectDefaultWeek(y, m)

                suppress = false

                val w = weekNumbersShown.getOrNull(spWeek.selectedItemPosition) ?: -1
                loadForSelection(y, m, w)
            }

            // Week selected
            spWeek.onItemSelectedListener = SimpleItemSelectedListener {
                if (suppress) return@SimpleItemSelectedListener

                val y = years.getOrNull(spYear.selectedItemPosition) ?: currentYear
                val m = monthNumbersShown.getOrNull(spMonth.selectedItemPosition) ?: 0
                val w = weekNumbersShown.getOrNull(spWeek.selectedItemPosition) ?: -1

                loadForSelection(y, m, w)
            }

            // Initial selections
            suppress = true
            val defaultYearIndex = years.indexOf(currentYear).let { if (it >= 0) it else 0 }
            spYear.setSelection(defaultYearIndex)

            val selectedYear = years.getOrNull(spYear.selectedItemPosition) ?: currentYear
            rebuildMonthSpinnerForYear(selectedYear, preferMonth = currentMonth)

            val selectedMonth = monthNumbersShown.getOrNull(spMonth.selectedItemPosition) ?: currentMonth
            rebuildWeekSpinnerForYearMonth(selectedYear, selectedMonth)
            selectDefaultWeek(selectedYear, selectedMonth)

            suppress = false

            val initY = years.getOrNull(spYear.selectedItemPosition) ?: currentYear
            val initM = monthNumbersShown.getOrNull(spMonth.selectedItemPosition) ?: 0
            val initW = weekNumbersShown.getOrNull(spWeek.selectedItemPosition) ?: -1
            loadForSelection(initY, initM, initW)
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

    private fun rebuildWeekSpinnerForYearMonth(year: Int, month: Int) {
        if (year == -1 || month == 0) {
            weekRanges = emptyList()
            weekNumbersShown = emptyList()

            val adapter = ArrayAdapter(
                requireContext(),
                R.layout.item_spinner_black,
                listOf("Select Month")
            ).also { it.setDropDownViewResource(R.layout.item_spinner_black_dropdown) }

            spWeek.adapter = adapter
            spWeek.isEnabled = false
            spWeek.alpha = 0.5f
            return
        }

        spWeek.isEnabled = true
        spWeek.alpha = 1f

        val ym = YearMonth.of(year, month)
        val startOfMonth = ym.atDay(1)
        val endOfMonth = ym.atEndOfMonth()

        val ranges = mutableListOf<WeekRange>()
        var start = startOfMonth
        var weekNo = 1

        while (!start.isAfter(endOfMonth)) {
            val dow = start.dayOfWeek.value
            val daysToSunday = (7 - dow).toLong()
            var end = start.plusDays(daysToSunday)
            if (end.isAfter(endOfMonth)) end = endOfMonth

            ranges.add(WeekRange(weekNo, start, end))

            weekNo++
            start = end.plusDays(1)
        }

        weekRanges = ranges
        weekNumbersShown = ranges.map { it.weekNo }

        val nameFmt = SimpleDateFormat("dd MMM", Locale.getDefault())
        val weekNames = ranges.map { r ->
            val s = nameFmt.format(Date(r.start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()))
            val e = nameFmt.format(Date(r.end.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()))
            "Week ${r.weekNo} ($s - $e)"
        }

        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.item_spinner_black,
            weekNames
        ).also { it.setDropDownViewResource(R.layout.item_spinner_black_dropdown) }

        spWeek.adapter = adapter
        spWeek.setSelection(0)
    }

    private fun loadForSelection(year: Int, month: Int, week: Int) {
        val zone = ZoneId.systemDefault()
        val dao = AppDatabase.getInstance(requireContext()).expenseDao()

        viewLifecycleOwner.lifecycleScope.launch {

            var weekLabel: String? = null

            val (fromMillis, endMillis) = withContext(Dispatchers.IO) {

                if (year == -1 && month == 0) {
                    val start = dao.getFirstExpenseDate() ?: System.currentTimeMillis()
                    val end = System.currentTimeMillis()
                    return@withContext start to end
                }

                if (year != -1 && month == 0) {
                    val start = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
                    val end = LocalDate.of(year, 12, 31).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                    return@withContext start to end
                }

                if (year == -1 && month != 0) {
                    val startYear = firstYear
                    val endYear = LocalDate.now().year

                    val start = LocalDate.of(startYear, month, 1).atStartOfDay(zone).toInstant().toEpochMilli()
                    val end = LocalDate.of(endYear, month, 1)
                        .withDayOfMonth(YearMonth.of(endYear, month).lengthOfMonth())
                        .plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

                    return@withContext start to end
                }

                val ym = YearMonth.of(year, month)

                val selectedRange = weekRanges.getOrNull((week - 1).coerceAtLeast(0))
                    ?: WeekRange(1, ym.atDay(1), ym.atDay(min(7, ym.lengthOfMonth())))

                val labelFmt = DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())
                weekLabel = "Week ${selectedRange.weekNo} (" +
                        "${selectedRange.start.format(labelFmt)} - " +
                        "${selectedRange.end.format(labelFmt)})"

                val start = selectedRange.start.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = selectedRange.end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                start to end
            }

            // ✅ update UI on main thread
            tvSelectedWeek.text = weekLabel ?: ""

            // ✅ Load weekly category summary (Row -> Summary)
            val categoryRows: List<CategorySummaryRow> = withContext(Dispatchers.IO) {
                dao.getCategorySummaryBetween(fromMillis, endMillis)
            }

            val total = categoryRows.sumOf { it.totalAmount }.takeIf { it > 0 } ?: 1.0

            val withPercent: List<CategorySummary> = categoryRows.map { r ->
                CategorySummary(
                    category = r.category,
                    totalAmount = r.totalAmount,
                    percent = ((r.totalAmount / total) * 100).toFloat(),
                    txnCount = r.txnCount
                )
            }

            // ✅ Pie + category grid (UNCHANGED)
            updatePie(pieWeekly, withPercent)
            categoryAdapter.submitList(withPercent)

            Log.d(TAG, "Weekly range: from=$fromMillis to=$endMillis (year=$year month=$month week=$week)")

            // ✅ Day-wise totals for bar chart (UNCHANGED)
            val rows: List<ChartDayTotal> = withContext(Dispatchers.IO) {
                dao.getDayWiseTotalsBetween(fromMillis, endMillis)
            }

            // ✅ Bar chart (UNCHANGED)
            showDailyBarChartWithZeros(fromMillis, endMillis, rows)

            // ------------------ ✅ SUGGESTION SLIDER (5 cards + dots) ------------------

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
            } else {
                "Highest day: —"
            }

            val minText = if (minRow != null) {
                val d = Instant.ofEpochMilli(minRow.dayStart).atZone(zone).toLocalDate()
                "Lowest day: ${d.format(dfDay)} • ₹" + String.format(Locale.US, "%,.0f", minRow.total)
            } else {
                "Lowest day: —"
            }

            val suggestionList = listOf(
                SuggestionItem(
                    "You spent ₹" + String.format(Locale.US, "%,.0f", totalSpent) +
                            " this week. Average ₹" + String.format(Locale.US, "%,.0f", avgPerDay) + "/day."
                ),
                SuggestionItem(maxText),
                SuggestionItem(minText),
                SuggestionItem("Try reducing small spends under ₹50 — they add up quickly."),
                SuggestionItem("Set a weekly limit and track daily to stay in control.")
            )

            // ✅ MUST: set data to ViewPager
            suggestionAdapter.submitList(suggestionList)

            // ✅ attach dots ONLY ONCE (no extra mediator below)
            attachDots(suggestionList.size)

            // ------------------ ✅ SUMMARY SECTION ------------------

            val topMerchantName: String? = withContext(Dispatchers.IO) {
                try {
                    dao.getTopMerchantBetween(fromMillis, endMillis).merchant
                } catch (e: Exception) {
                    null
                }
            }

            updateWeeklySummary(
                fromMillis = fromMillis,
                endMillis = endMillis,
                dayRows = rows,
                topMerchantName = topMerchantName
            )
        }
    }

        // ------------------ ✅ DOTS (TabLayout) ------------------

    private fun attachDots(count: Int) {
        // Remove old mediator if any
        dotsMediator?.detach()

        dotsMediator = TabLayoutMediator(tabDots, vpSuggestions) { tab, _ ->
            val dotView = layoutInflater.inflate(R.layout.item_dot, tabDots, false)
            tab.customView = dotView
        }
        dotsMediator?.attach()

        // initial selected
        updateDotsSelected(0)

        // Update dots on swipe
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


    private fun updatePie(pie: PieChart, list: List<CategorySummary>) {
        val thresholdPercent = 3f

        val big = list.filter { it.percent >= thresholdPercent && it.totalAmount > 0.0 }
        val small = list.filter { it.percent < thresholdPercent && it.totalAmount > 0.0 }

        val othersAmount = small.sumOf { it.totalAmount }
        val entries = mutableListOf<PieEntry>()

        big.forEach { entries.add(PieEntry(it.totalAmount.toFloat(), it.category)) }

        if (othersAmount > 0.0) {
            entries.add(PieEntry(othersAmount.toFloat(), "Other categories"))
        }

        if (entries.isEmpty()) {
            pie.clear()
            pie.setNoDataText("No data for selected period")
            pie.invalidate()
            return
        }

        pie.setUsePercentValues(true)
        val dataSet = PieDataSet(entries, "")

        dataSet.colors = entries.map { e ->
            when {
                e.label.equals("Other categories", ignoreCase = true) ->
                    otherCategoriesColor

                e.label.equals("Others", ignoreCase = true) ->
                    CategoryColorMap.colorFor("Others")

                else ->
                    CategoryColorMap.colorFor(e.label)
            }
        }

        dataSet.xValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
        dataSet.yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE

        dataSet.valueLinePart1OffsetPercentage = 85f
        dataSet.valueLinePart1Length = 0.30f
        dataSet.valueLinePart2Length = 0.35f
        dataSet.valueLineWidth = 1f
        dataSet.valueLineColor = Color.DKGRAY

        dataSet.sliceSpace = 2f
        dataSet.selectionShift = 6f

        val data = PieData(dataSet)
        data.setValueFormatter(object : ValueFormatter() {
            override fun getPieLabel(value: Float, pieEntry: PieEntry?): String {
                val name = pieEntry?.label ?: ""
                return "$name " + String.format(Locale.US, "%.0f%%", value)
            }
        })

        data.setValueTextSize(10f)
        data.setValueTextColor(Color.BLACK)

        pie.setDrawEntryLabels(false)
        pie.legend.isEnabled = false
        pie.description.isEnabled = false

        pie.isDrawHoleEnabled = true
        pie.holeRadius = 52f
        pie.transparentCircleRadius = 56f

        pie.setExtraOffsets(18f, 12f, 18f, 12f)

        pie.data = data
        pie.invalidate()
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

    private fun selectDefaultWeek(year: Int, month: Int) {
        if (weekRanges.isEmpty() || weekNumbersShown.isEmpty()) return

        val today = LocalDate.now()

        val index = if (today.year == year && today.monthValue == month) {
            weekRanges.indexOfFirst { !today.isBefore(it.start) && !today.isAfter(it.end) }
                .takeIf { it >= 0 } ?: 0
        } else 0

        spWeek.setSelection(index)
    }

    private fun money(v: Double): String = "₹" + String.format(Locale.US, "%,.2f", v)

    private fun updateWeeklySummary(
        fromMillis: Long,
        endMillis: Long,
        dayRows: List<ChartDayTotal>,
        topMerchantName: String?
    ) {
        val zone = ZoneId.systemDefault()

        val startDate = Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate()
        val endDate = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate()
        val daysCount = (java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1).toInt().coerceAtLeast(1)

        // total from DB rows (these are days that have txns)
        val total = dayRows.sumOf { it.total }

        val dailyAvg = total / daysCount.toDouble()

        val maxRow = dayRows.maxByOrNull { it.total }
        val minNonZeroRow = dayRows.filter { it.total > 0.0 }.minByOrNull { it.total }

        val dayFmt = java.time.format.DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())

        val highestDayText = if (maxRow != null) {
            val d = Instant.ofEpochMilli(maxRow.dayStart).atZone(zone).toLocalDate()
            "${d.format(dayFmt)} • ${money(maxRow.total)}"
        } else "—"

        val lowestDayText = if (minNonZeroRow != null) {
            val d = Instant.ofEpochMilli(minNonZeroRow.dayStart).atZone(zone).toLocalDate()
            "${d.format(dayFmt)} • ${money(minNonZeroRow.total)}"
        } else "—"

        val merchantText = topMerchantName?.takeIf { it.isNotBlank() } ?: "—"

        val items = listOf(
            SummaryItem("Total spent", money(total)),
            SummaryItem("Daily average", money(dailyAvg)),
            SummaryItem("Highest day", highestDayText),
            SummaryItem("Lowest day", lowestDayText),
            SummaryItem("Highest merchant", merchantText),
        )

        summaryAdapter.submitList(items)
    }


}
