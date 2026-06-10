package com.example.expensereader.ui.analysis

import android.graphics.Color
import android.os.Bundle
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
import com.example.expensereader.db.MonthSummaryRow
import com.example.expensereader.ui.category.SimpleItemSelectedListener
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.max
import android.widget.ImageView
import androidx.viewpager2.widget.ViewPager2

class SemesterFragment : Fragment(R.layout.fragment_semester) {

    private lateinit var spYear: Spinner
    private lateinit var spSem: Spinner

    private lateinit var barSemester: BarChart
    private lateinit var pieSemester: PieChart

    private lateinit var tvSelectedSem: TextView
    private lateinit var rvMonths: RecyclerView
    private lateinit var monthAdapter: SemesterMonthAdapter
    private lateinit var rvSemesterSummary: RecyclerView
    private var otherCategoriesColor: Int = Color.LTGRAY
    private lateinit var semesterSummaryAdapter: WeeklySummaryAdapter


    private lateinit var years: List<Int>

    // ✅ Suggestions
    private lateinit var vpSuggestions: ViewPager2
    private lateinit var tabDots: TabLayout
    private lateinit var suggestionAdapter: SuggestionPagerAdapter
    private var dotsMediator: TabLayoutMediator? = null

    // Semester options
    private val semOptions = listOf("Sem 1 (Jan-Jun)", "Sem 2 (Jul-Dec)")

    private var firstYear: Int = 0
    private var suppress = false

    private val monthShort = listOf(
        "Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        otherCategoriesColor = androidx.core.content.ContextCompat.getColor(
            requireContext(),
            R.color.other_categories_color
        )


        spYear = view.findViewById(R.id.spYear)
        spSem = view.findViewById(R.id.spSem)

        tvSelectedSem = view.findViewById(R.id.tvSelectedSem)

        barSemester = view.findViewById(R.id.barSemester)
        pieSemester = view.findViewById(R.id.pieSemester)

        rvMonths = view.findViewById(R.id.rvMonths)
        monthAdapter = SemesterMonthAdapter()

        rvSemesterSummary = view.findViewById(R.id.rvSemesterSummary)
        semesterSummaryAdapter = WeeklySummaryAdapter()

        rvSemesterSummary.layoutManager = GridLayoutManager(requireContext(), 2)
        rvSemesterSummary.adapter = semesterSummaryAdapter
        rvSemesterSummary.isNestedScrollingEnabled = false


        rvMonths.layoutManager = GridLayoutManager(requireContext(), 2)
        rvMonths.adapter = monthAdapter
        rvMonths.isNestedScrollingEnabled = false

        // ✅ Suggestions init
        vpSuggestions = view.findViewById(R.id.vpSuggestions)
        tabDots = view.findViewById(R.id.tabDots)

        suggestionAdapter = SuggestionPagerAdapter()
        vpSuggestions.adapter = suggestionAdapter
        vpSuggestions.offscreenPageLimit = 1

        // IMPORTANT: do not attach dots here with empty list.
        // We will attach dots inside updateSemesterSuggestions() after submitList.

        setupBarChart()
        setupPieChart()
        setupYearAndSemSpinners()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // ✅ avoid callback leak
        try {
            vpSuggestions.unregisterOnPageChangeCallback(pageCallback)
        } catch (_: Exception) {}
        dotsMediator?.detach()
        dotsMediator = null
    }

    private fun setupBarChart() {
        barSemester.description.isEnabled = false
        barSemester.setDrawGridBackground(false)
        barSemester.setDrawBarShadow(false)
        barSemester.setPinchZoom(false)
        barSemester.setScaleEnabled(false)
        barSemester.axisRight.isEnabled = false

        val df = DecimalFormat("#,##0.##")

        barSemester.axisLeft.apply {
            axisMinimum = 0f
            setDrawGridLines(true)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return "₹" + df.format(value.toDouble())
                }
            }
        }

        barSemester.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            granularity = 1f
            labelRotationAngle = -35f
            setAvoidFirstLastClipping(true)
        }

        barSemester.legend.isEnabled = false
    }

    private fun setupPieChart() {
        pieSemester.setUsePercentValues(true)
        pieSemester.setDrawEntryLabels(false)
        pieSemester.legend.isEnabled = false
        pieSemester.description.isEnabled = false

        pieSemester.isDrawHoleEnabled = true
        pieSemester.holeRadius = 52f
        pieSemester.transparentCircleRadius = 56f
        pieSemester.setExtraOffsets(18f, 12f, 18f, 12f)
    }

    private fun setupYearAndSemSpinners() {
        val now = LocalDate.now()
        val currentYear = now.year
        val zone = ZoneId.systemDefault()

        val dao = AppDatabase.getInstance(requireContext()).expenseDao()

        viewLifecycleOwner.lifecycleScope.launch {

            val firstMillis = withContext(Dispatchers.IO) { dao.getFirstExpenseDate() }

            firstYear = if (firstMillis != null) {
                Instant.ofEpochMilli(firstMillis).atZone(zone).toLocalDate().year
            } else {
                currentYear
            }

            years = listOf(-1) + (firstYear..currentYear).toList()

            spYear.adapter = ArrayAdapter(
                requireContext(),
                R.layout.item_spinner_black,
                years.map { if (it == -1) "All" else it.toString() }
            ).also { it.setDropDownViewResource(R.layout.item_spinner_black_dropdown) }

            spSem.adapter = ArrayAdapter(
                requireContext(),
                R.layout.item_spinner_black,
                semOptions
            ).also { it.setDropDownViewResource(R.layout.item_spinner_black_dropdown) }

            spYear.onItemSelectedListener = SimpleItemSelectedListener {
                if (suppress) return@SimpleItemSelectedListener
                val y = years.getOrNull(spYear.selectedItemPosition) ?: currentYear
                val semIndex = spSem.selectedItemPosition.coerceIn(0, 1)
                loadSemester(y, semIndex)
            }

            spSem.onItemSelectedListener = SimpleItemSelectedListener {
                if (suppress) return@SimpleItemSelectedListener
                val y = years.getOrNull(spYear.selectedItemPosition) ?: currentYear
                val semIndex = spSem.selectedItemPosition.coerceIn(0, 1)
                loadSemester(y, semIndex)
            }

            suppress = true
            spYear.setSelection(years.indexOf(currentYear).takeIf { it >= 0 } ?: 0)
            spSem.setSelection(if (now.monthValue <= 6) 0 else 1)
            suppress = false

            val initY = years.getOrNull(spYear.selectedItemPosition) ?: currentYear
            val initSem = spSem.selectedItemPosition.coerceIn(0, 1)
            loadSemester(initY, initSem)
        }
    }

    private fun loadSemester(year: Int, semIndex: Int) {
        val zone = ZoneId.systemDefault()
        val dao = AppDatabase.getInstance(requireContext()).expenseDao()

        viewLifecycleOwner.lifecycleScope.launch {

            val (fromMillis, toMillis, monthsList) = withContext(Dispatchers.IO) {

                val now = LocalDate.now()
                val currentYear = now.year
                val currentMonth = now.monthValue

                val (startMonth, endMonth) = if (semIndex == 0) 1 to 6 else 7 to 12

                // ✅ default = full 6 months
                var months = (startMonth..endMonth).toList()

                // ✅ if selected year is current year -> show only upto current month in this semester
                if (year == currentYear) {
                    val cap = currentMonth.coerceIn(startMonth, endMonth)
                    months = (startMonth..cap).toList()
                }

                if (year == -1) {
                    // ✅ All Years: from firstYear semester start -> till today
                    val from = LocalDate.of(firstYear, startMonth, 1)
                        .atStartOfDay(zone).toInstant().toEpochMilli()

                    val end = System.currentTimeMillis()

                    Triple(from, end, months)
                } else {
                    val from = LocalDate.of(year, startMonth, 1)
                        .atStartOfDay(zone).toInstant().toEpochMilli()

                    // ✅ cap end month if current year
                    val endMonthToUse = if (year == currentYear) {
                        currentMonth.coerceIn(startMonth, endMonth)
                    } else endMonth

                    val end = LocalDate.of(year, endMonthToUse, 1)
                        .withDayOfMonth(LocalDate.of(year, endMonthToUse, 1).lengthOfMonth())
                        .plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

                    Triple(from, end, months)
                }
            }

            tvSelectedSem.text = if (year == -1) {
                if (semIndex == 0) "All Years • Sem 1 (Jan-Jun)" else "All Years • Sem 2 (Jul-Dec)"
            } else {
                if (semIndex == 0) "$year • Sem 1 (Jan-Jun)" else "$year • Sem 2 (Jul-Dec)"
            }

            val monthRows: List<MonthSummaryRow> = withContext(Dispatchers.IO) {
                dao.getMonthSummaryBetween(fromMillis, toMillis)
            }

            val map = monthRows.associateBy { it.monthNo }

            val finalRows = monthsList.map { m ->
                map[m] ?: MonthSummaryRow(
                    monthNo = m,
                    totalAmount = 0.0,
                    txnCount = 0
                )
            }

            showSemesterBar(finalRows, monthsList)
            updateSemesterPieLikeWeekly(pieSemester, finalRows)
            monthAdapter.submitList(finalRows)

            updateSemesterSummary(fromMillis, toMillis, finalRows)
            updateSemesterSuggestions(finalRows, year, semIndex)

        }
    }


    private fun showSemesterBar(rows: List<MonthSummaryRow>, monthsList: List<Int>) {
        val labels = monthsList.map { m -> monthShort[m - 1] }

        val entries = rows.mapIndexed { idx, r ->
            BarEntry(idx.toFloat(), r.totalAmount.toFloat())
        }

        if (entries.isEmpty()) {
            barSemester.clear()
            barSemester.invalidate()
            return
        }

        val dataSet = BarDataSet(entries, "Monthly Spend").apply {
            valueTextSize = 10f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value == 0f) "" else value.toInt().toString()
                }
            }
        }

        barSemester.data = BarData(dataSet).apply { barWidth = 0.7f }

        barSemester.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val i = value.toInt()
                return if (i in labels.indices) labels[i] else ""
            }
        }

        barSemester.xAxis.apply {
            granularity = 1f
            isGranularityEnabled = true
            labelCount = labels.size
        }

        barSemester.setFitBars(true)
        barSemester.axisLeft.axisMinimum = 0f
        barSemester.invalidate()
        barSemester.animateY(700)
    }

    private fun updateSemesterPieLikeWeekly(pie: PieChart, rows: List<MonthSummaryRow>) {
        val total = rows.sumOf { it.totalAmount }.takeIf { it > 0 } ?: 0.0

        if (total <= 0.0) {
            pie.clear()
            pie.setNoDataText("No data for selected semester")
            pie.invalidate()
            return
        }

        // ✅ like Weekly: group small slices into "Other months"
        val thresholdPercent = 3f

        data class MonthSlice(val label: String, val amount: Double, val percent: Float)

        val slices = rows
            .filter { it.totalAmount > 0.0 }
            .map { row ->
                // ✅ safe month label (avoid crash if monthNo is wrong)
                val idx = (row.monthNo - 1).coerceIn(0, 11)
                val label = monthShort[idx]

                val percent = ((row.totalAmount / total) * 100.0).toFloat()
                MonthSlice(label, row.totalAmount, percent)
            }

        val big = slices.filter { it.percent >= thresholdPercent }
        val small = slices.filter { it.percent < thresholdPercent }

        val otherAmount = small.sumOf { it.amount }

        val entries = mutableListOf<PieEntry>()
        big.forEach { entries.add(PieEntry(it.amount.toFloat(), it.label)) }
        if (otherAmount > 0.0) entries.add(PieEntry(otherAmount.toFloat(), "Other months"))

        if (entries.isEmpty()) {
            pie.clear()
            pie.setNoDataText("No data for selected semester")
            pie.invalidate()
            return
        }

        pie.setUsePercentValues(true)

        val dataSet = PieDataSet(entries, "")

        // ✅ fixed colors for each month + "Other months"
        dataSet.colors = entries.map { e ->
            when {
                // ✅ FIX: match label "Other months"
                e.label.equals("Other months", ignoreCase = true) ->
                    otherCategoriesColor

                e.label.equals("Others", ignoreCase = true) ->
                    CategoryColorMap.colorFor("Others")

                else ->
                    CategoryColorMap.colorFor(e.label)
            }
        }

        // ✅ same label style as Weekly
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

    // -------------------- ✅ SUGGESTIONS + DOTS --------------------

    private fun updateSemesterSuggestions(rows: List<MonthSummaryRow>, year: Int, semIndex: Int) {
        val totalSpent = rows.sumOf { it.totalAmount }
        val totalTxns = rows.sumOf { it.txnCount }
        val avgPerMonth = totalSpent / max(1, rows.size).toDouble()

        val maxRow = rows.maxByOrNull { it.totalAmount }
        val minRow = rows.filter { it.totalAmount > 0.0 }.minByOrNull { it.totalAmount }

        val highestText = if (maxRow != null && maxRow.totalAmount > 0.0) {
            "Highest month: ${monthShort[maxRow.monthNo - 1]} • ₹" +
                    String.format(Locale.US, "%,.0f", maxRow.totalAmount)
        } else "Highest month: —"

        val lowestText = if (minRow != null && minRow.totalAmount > 0.0) {
            "Lowest month: ${monthShort[minRow.monthNo - 1]} • ₹" +
                    String.format(Locale.US, "%,.0f", minRow.totalAmount)
        } else "Lowest month: —"

        val label = if (year == -1) {
            if (semIndex == 0) "All Years • Sem 1" else "All Years • Sem 2"
        } else {
            if (semIndex == 0) "$year • Sem 1" else "$year • Sem 2"
        }

        val suggestionList = listOf(
            SuggestionItem("$label total: ₹" + String.format(Locale.US, "%,.0f", totalSpent)),
            SuggestionItem("Transactions: $totalTxns • Avg/month: ₹" + String.format(Locale.US, "%,.0f", avgPerMonth)),
            SuggestionItem(highestText),
            SuggestionItem(lowestText),
            SuggestionItem("Tip: Set a monthly limit for these 6 months and track weekly.")
        )

        suggestionAdapter.submitList(suggestionList)

        // ✅ IMPORTANT: attach dot views AFTER submitList
        attachDots(suggestionList.size)
    }

    // -------------------- ✅ DOTS (same style as your WeeklyFragment) --------------------

    private fun attachDots(count: Int) {
        dotsMediator?.detach()

        dotsMediator = TabLayoutMediator(tabDots, vpSuggestions) { tab, _ ->
            val dotView = layoutInflater.inflate(R.layout.item_dot, tabDots, false)
            tab.customView = dotView
        }
        dotsMediator?.attach()

        updateDotsSelected(0)

        // Update dots on swipe
        try { vpSuggestions.unregisterOnPageChangeCallback(pageCallback) } catch (_: Exception) {}
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

    private suspend fun updateSemesterSummary(
        fromMillis: Long,
        toMillis: Long,
        rows: List<MonthSummaryRow>
    ) {

        if (rows.isEmpty()) {
            semesterSummaryAdapter.submitList(emptyList())
            return
        }

        // 1️⃣ Total spent in semester
        val totalSpent = rows.sumOf { it.totalAmount }

        // 2️⃣ Monthly average (only months shown)
        val monthCount = rows.size.coerceAtLeast(1)
        val monthlyAvg = totalSpent / monthCount.toDouble()

        // 3️⃣ Highest & lowest month
        val maxRow = rows.maxByOrNull { it.totalAmount }
        val minRow = rows.filter { it.totalAmount > 0 }.minByOrNull { it.totalAmount }

        val highestMonthText = if (maxRow != null) {
            "${monthShort[maxRow.monthNo - 1]} • ${money(maxRow.totalAmount)}"
        } else "—"

        val lowestMonthText = if (minRow != null) {
            "${monthShort[minRow.monthNo - 1]} • ${money(minRow.totalAmount)}"
        } else "—"

        // 4️⃣ Highest merchant (USING YOUR EXISTING DAO)
        val topMerchantName = withContext(Dispatchers.IO) {
            try {
                AppDatabase.getInstance(requireContext())
                    .expenseDao()
                    .getTopMerchantBetween(fromMillis, toMillis)
                    .merchant
            } catch (e: Exception) {
                null
            }
        } ?: "—"

        // 5️⃣ Summary cards (same adapter as Weekly)
        val items = listOf(
            SummaryItem("Total spent", money(totalSpent)),
            SummaryItem("Monthly average", money(monthlyAvg)),
            SummaryItem("Highest month", highestMonthText),
            SummaryItem("Lowest month", lowestMonthText),
            SummaryItem("Highest merchant", topMerchantName)
        )

        semesterSummaryAdapter.submitList(items)
    }

    private fun money(v: Double): String {
        return "₹" + String.format(Locale.US, "%,.2f", v)
    }


}
