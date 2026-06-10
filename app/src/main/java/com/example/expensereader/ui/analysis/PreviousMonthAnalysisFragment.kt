package com.example.expensereader.ui.analysis

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
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

class PreviousMonthAnalysisFragment : Fragment(R.layout.fragment_previous_month_analysis) {

    // --- selectors ---
    private lateinit var spWeek: Spinner
    private lateinit var spMode: Spinner
    private lateinit var tvTitle: TextView

    private lateinit var boxCategory: View
    private lateinit var spCategory: Spinner
    private lateinit var barCategory: BarChart

    // --- category + pie ---
    private lateinit var pie: PieChart
    private lateinit var rvCategories: RecyclerView
    private lateinit var categoryAdapter: CategorySummaryAdapter

    // --- frequency (category->merchant bars) ---
    private lateinit var rvFrequency: RecyclerView
    private val frequencyAdapter = CategoryMerchantBarsAdapter()

    // --- weekly view (only bar) ---
    private lateinit var barWeekly: BarChart

    // --- monthly view (bar + weekend/weekday split) ---
    private lateinit var barMonthly: BarChart
    private lateinit var tvWeekendPct: TextView
    private lateinit var tvWeekdayPct: TextView
    private lateinit var vWeekendBar: View
    private lateinit var vWeekdayBar: View
    private lateinit var tvWeekendInfo: TextView
    private lateinit var tvWeekdayInfo: TextView

    // containers (show/hide)
    private lateinit var boxFrequency: View
    private lateinit var boxWeekly: View
    private lateinit var boxMonthly: View

    // from your MonthlyFragment
    private lateinit var rvWeekSplit: RecyclerView
    private lateinit var weekSplitAdapter: WeekSplitAdapter
    private lateinit var tvWeekSplitHeading: TextView

    // --- week ranges ---
    private data class WeekRange(val weekNo: Int, val start: LocalDate, val end: LocalDate)
    private var weekRanges: List<WeekRange> = emptyList()

    // ✅ week spinner options: -1 = All, 1..N = weeks
    private var weekOptions: List<Int> = emptyList()

    private var suppress = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        spWeek = view.findViewById(R.id.spWeek)
        spMode = view.findViewById(R.id.spMode)
        tvTitle = view.findViewById(R.id.tvPrevMonthTitle)

        // ✅ These IDs MUST exist in XML (we add them below)
        pie = view.findViewById(R.id.piePrevMonth)
        rvCategories = view.findViewById(R.id.rvPrevMonthCategories)

        rvFrequency = view.findViewById(R.id.rvPrevMonthFrequency)

        barWeekly = view.findViewById(R.id.barPrevWeekly)
        barMonthly = view.findViewById(R.id.barPrevMonthly)

        tvWeekendPct = view.findViewById(R.id.tvWeekendPct)
        tvWeekdayPct = view.findViewById(R.id.tvWeekdayPct)
        vWeekendBar = view.findViewById(R.id.vWeekendBar)
        vWeekdayBar = view.findViewById(R.id.vWeekdayBar)
        tvWeekendInfo = view.findViewById(R.id.tvWeekendInfo)
        tvWeekdayInfo = view.findViewById(R.id.tvWeekdayInfo)

        boxCategory = view.findViewById(R.id.boxCategory)
        boxFrequency = view.findViewById(R.id.boxFrequency)
        boxWeekly = view.findViewById(R.id.boxWeekly)
        boxMonthly = view.findViewById(R.id.boxMonthly)

        tvWeekSplitHeading = view.findViewById(R.id.tvWeekSplitHeading)
        rvWeekSplit = view.findViewById(R.id.rvWeekSplit)

        spCategory = view.findViewById(R.id.spCategory)
        

        // category list
        categoryAdapter = CategorySummaryAdapter()
        rvCategories.layoutManager = GridLayoutManager(requireContext(), 2)
        rvCategories.adapter = categoryAdapter
        rvCategories.isNestedScrollingEnabled = false

        // frequency list
        rvFrequency.layoutManager = LinearLayoutManager(requireContext())
        rvFrequency.adapter = frequencyAdapter
        rvFrequency.isNestedScrollingEnabled = false

        // week split list
        weekSplitAdapter = WeekSplitAdapter()
        rvWeekSplit.layoutManager = GridLayoutManager(requireContext(), 1)
        rvWeekSplit.adapter = weekSplitAdapter
        rvWeekSplit.isNestedScrollingEnabled = false

        setupPie()
        setupBar(barCategory)
        setupBar(barWeekly)
        setupBar(barMonthly)

        setupSpinnersAndLoad()
    }

    // ------------------- setup -------------------

    private fun setupSpinnersAndLoad() {
        val zone = ZoneId.systemDefault()
        val now = LocalDate.now(zone)
        val prevYm = YearMonth.from(now).minusMonths(1)

        val monthTitleFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
        tvTitle.text = "Previous Month • ${prevYm.atDay(1).format(monthTitleFmt)}"

        // Build week ranges for previous month (same logic: end on Sunday)
        weekRanges = buildWeekRangesForMonth(prevYm.year, prevYm.monthValue)

        // mode spinner
        val modes = listOf("Category", "Frequency", "Weekly", "Monthly")
        spMode.adapter = ArrayAdapter(
            requireContext(),
            R.layout.item_spinner_black,
            modes
        ).also { it.setDropDownViewResource(R.layout.item_spinner_black_dropdown) }

        spMode.onItemSelectedListener = SimpleItemSelectedListener {
            if (suppress) return@SimpleItemSelectedListener
            applyModeVisibilityAndWeekRules()
            loadAllSectionsForSelection(prevYm.year, prevYm.monthValue)
        }

        spWeek.onItemSelectedListener = SimpleItemSelectedListener {
            if (suppress) return@SimpleItemSelectedListener

            // ✅ Weekly must NOT allow "All"
            val mode = spMode.selectedItem?.toString() ?: "Frequency"
            val sel = getSelectedWeekOrAll()
            if (mode == "Weekly" && sel == -1) {
                suppress = true
                spWeek.setSelection(0) // force Week 1
                suppress = false
                return@SimpleItemSelectedListener
            }

            loadAllSectionsForSelection(prevYm.year, prevYm.monthValue)
        }

        // default: Category + All allowed (as you already had)
        suppress = true
        spMode.setSelection(0)
        rebuildWeekSpinner(disallowAll = false)
        suppress = false

        applyModeVisibilityAndWeekRules()
        loadAllSectionsForSelection(prevYm.year, prevYm.monthValue)
    }

    private fun applyModeVisibilityAndWeekRules() {
        val mode = spMode.selectedItem?.toString() ?: "Frequency"

        boxCategory.visibility = if (mode == "Category") View.VISIBLE else View.GONE
        boxFrequency.visibility = if (mode == "Frequency") View.VISIBLE else View.GONE
        boxWeekly.visibility = if (mode == "Weekly") View.VISIBLE else View.GONE
        boxMonthly.visibility = if (mode == "Monthly") View.VISIBLE else View.GONE

        // ✅ Weekly: remove "All" from week spinner
        val disallowAll = (mode == "Weekly")
        val previousSelection = getSelectedWeekOrAll()

        suppress = true
        rebuildWeekSpinner(disallowAll = disallowAll)

        if (previousSelection != null) {
            val idx = weekOptions.indexOf(previousSelection).takeIf { it >= 0 }
            if (idx != null) {
                spWeek.setSelection(idx)
            } else {
                spWeek.setSelection(0)
            }
        } else {
            spWeek.setSelection(0)
        }
        suppress = false
    }

    private fun rebuildWeekSpinner(disallowAll: Boolean) {
        weekOptions = if (disallowAll) {
            weekRanges.map { it.weekNo }
        } else {
            listOf(-1) + weekRanges.map { it.weekNo }
        }

        val nameFmt = DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())
        val weekNames = weekOptions.map { w ->
            if (w == -1) {
                "All"
            } else {
                val r = weekRanges.firstOrNull { it.weekNo == w }
                if (r != null) {
                    "Week ${r.weekNo} (${r.start.format(nameFmt)} - ${r.end.format(nameFmt)})"
                } else {
                    "Week $w"
                }
            }
        }

        spWeek.adapter = ArrayAdapter(
            requireContext(),
            R.layout.item_spinner_black,
            weekNames
        ).also { it.setDropDownViewResource(R.layout.item_spinner_black_dropdown) }

        spWeek.setSelection(0)
    }

    private fun getSelectedWeekOrAll(): Int? {
        val pos = spWeek.selectedItemPosition
        return weekOptions.getOrNull(pos)
    }

    // ------------------- core loading -------------------

    private fun loadAllSectionsForSelection(year: Int, month: Int) {
        val dao = AppDatabase.getInstance(requireContext()).expenseDao()

        val mode = spMode.selectedItem?.toString() ?: "Frequency"
        val selectedWeek = getSelectedWeekOrAll() ?: -1

        // ✅ safety: weekly cannot be All
        if (mode == "Weekly" && selectedWeek == -1) return

        val (fromMillis, endMillis) = getRangeForPrevMonthSelection(year, month, selectedWeek)

        viewLifecycleOwner.lifecycleScope.launch {

            // 1) Categories + Pie (only spent)
            val categoryRows: List<CategorySummaryRow> = withContext(Dispatchers.IO) {
                dao.getCategorySummaryBetween(fromMillis, endMillis)
            }

            val total = categoryRows.sumOf { it.totalAmount }.takeIf { it > 0 } ?: 1.0
            val spentOnly = categoryRows.filter { it.totalAmount > 0.0 }

            val list: List<CategorySummary> = spentOnly.map { r ->
                CategorySummary(
                    category = r.category,
                    totalAmount = r.totalAmount,
                    percent = ((r.totalAmount / total) * 100).toFloat(),
                    txnCount = r.txnCount
                )
            }

            updatePie(pie, list)
            categoryAdapter.submitList(list)

            // 2) Frequency section (category -> top merchants bars)
            val blocks = withContext(Dispatchers.IO) {
                val cats = dao.getCategoriesBetween(fromMillis, endMillis)
                cats.map { cat ->
                    val top = dao.getTopMerchantsForCategoryBetween(
                        fromMillis = fromMillis,
                        toMillis = endMillis,
                        category = cat,
                        limit = 10
                    )
                    CategoryMerchantBarsAdapter.CategoryBlock(category = cat, rows = top)
                }
            }
            frequencyAdapter.submit(blocks)

            // 3) Weekly mode (only bar)
            val dayRows: List<ChartDayTotal> = withContext(Dispatchers.IO) {
                dao.getDayWiseTotalsBetween(fromMillis, endMillis)
            }
            showDailyBarChartWithZeros(barWeekly, fromMillis, endMillis, dayRows)

            // 4) Monthly mode (bar + weekend/weekdays)
            if (selectedWeek == -1) {
                showWeeklyTotalsBarChartForMonth(barMonthly, year, month, dayRows)
            } else {
                showDailyBarChartWithZeros(barMonthly, fromMillis, endMillis, dayRows)
            }

            // weekend vs weekday split
            updateWeekendWeekdaySplit(fromMillis, endMillis)

            // week split list (show only when All month selected)
            if (selectedWeek == -1) {
                tvWeekSplitHeading.visibility = View.VISIBLE
                rvWeekSplit.visibility = View.VISIBLE
                updateWeekSplitListForMonth(year, month, fromMillis, endMillis)
            } else {
                tvWeekSplitHeading.visibility = View.GONE
                rvWeekSplit.visibility = View.GONE
            }
        }

        if (mode == "Category") {
            loadCategoryAnalysis(fromMillis, endMillis)
        }
    }

    // ------------------- ranges -------------------

    private fun getRangeForPrevMonthSelection(year: Int, month: Int, selectedWeek: Int): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val ym = YearMonth.of(year, month)

        return if (selectedWeek == -1) {
            val start = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = ym.atEndOfMonth().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            start to end
        } else {
            val r = weekRanges.firstOrNull { it.weekNo == selectedWeek } ?: weekRanges.first()
            val start = r.start.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = r.end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            start to end
        }
    }

    private fun loadCategoryAnalysis(fromMillis: Long, endMillis: Long) {
        val dao = AppDatabase.getInstance(requireContext()).expenseDao()

        viewLifecycleOwner.lifecycleScope.launch {

            val categories = withContext(Dispatchers.IO) {
                dao.getCategoriesBetween(fromMillis, endMillis)
            }

            if (categories.isEmpty()) {
                barCategory.clear()
                return@launch
            }

            spCategory.adapter = ArrayAdapter(
                requireContext(),
                R.layout.item_spinner_black,
                categories
            )

            spCategory.onItemSelectedListener = SimpleItemSelectedListener {
                val selectedCategory = spCategory.selectedItem.toString()

                viewLifecycleOwner.lifecycleScope.launch {
                    val rows = withContext(Dispatchers.IO) {
                        dao.getDayWiseTotalsForCategoryBetween(
                            fromMillis,
                            endMillis,
                            selectedCategory
                        )
                    }

                    showDailyBarChartWithZeros(
                        barCategory,
                        fromMillis,
                        endMillis,
                        rows
                    )
                }
            }

            spCategory.setSelection(0)
        }
    }

    private fun buildWeekRangesForMonth(year: Int, month: Int): List<WeekRange> {
        val ym = YearMonth.of(year, month)
        val startOfMonth = ym.atDay(1)
        val endOfMonth = ym.atEndOfMonth()

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
        return ranges
    }

    // ------------------- pie -------------------

    private fun setupPie() {
        pie.setUsePercentValues(true)
        pie.setDrawEntryLabels(false)
        pie.legend.isEnabled = false
        pie.description.isEnabled = false
        pie.isDrawHoleEnabled = true
        pie.holeRadius = 52f
        pie.transparentCircleRadius = 56f
        pie.setExtraOffsets(18f, 12f, 18f, 12f)
    }

    private fun updatePie(pie: PieChart, list: List<CategorySummary>) {
        val thresholdPercent = 3f

        val big = list.filter { it.percent >= thresholdPercent && it.totalAmount > 0.0 }
        val small = list.filter { it.percent < thresholdPercent && it.totalAmount > 0.0 }

        val othersAmount = small.sumOf { it.totalAmount }
        val entries = mutableListOf<PieEntry>()

        big.forEach { entries.add(PieEntry(it.totalAmount.toFloat(), it.category)) }
        if (othersAmount > 0.0) entries.add(PieEntry(othersAmount.toFloat(), "Other categories"))

        if (entries.isEmpty()) {
            pie.clear()
            pie.setNoDataText("No spend in this period")
            pie.invalidate()
            return
        }

        val dataSet = PieDataSet(entries, "").apply {
            colors = entries.map { e ->
                when {
                    e.label.equals("Other categories", ignoreCase = true) ->
                        androidx.core.content.ContextCompat.getColor(requireContext(), R.color.other_categories_color)
                    else -> CategoryColorMap.colorFor(e.label)
                }
            }
            xValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
            yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
            valueLinePart1OffsetPercentage = 85f
            valueLinePart1Length = 0.30f
            valueLinePart2Length = 0.35f
            valueLineWidth = 1f
            valueLineColor = Color.DKGRAY
            sliceSpace = 2f
            selectionShift = 6f
        }

        val data = PieData(dataSet).apply {
            setValueTextSize(10f)
            setValueTextColor(Color.BLACK)
            setValueFormatter(object : ValueFormatter() {
                override fun getPieLabel(value: Float, pieEntry: PieEntry?): String {
                    val name = pieEntry?.label ?: ""
                    return "$name " + String.format(Locale.US, "%.0f%%", value)
                }
            })
        }

        pie.data = data
        pie.invalidate()
    }

    // ------------------- bar charts -------------------

    private fun setupBar(chart: BarChart) {
        chart.description.isEnabled = false
        chart.setDrawGridBackground(false)
        chart.setDrawBarShadow(false)
        chart.setPinchZoom(false)
        chart.setScaleEnabled(false)
        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = false

        val df = DecimalFormat("#,##0.##")
        chart.axisLeft.apply {
            axisMinimum = 0f
            setDrawGridLines(true)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return "₹" + df.format(value.toDouble())
                }
            }
        }

        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            granularity = 1f
            labelRotationAngle = -45f
            setAvoidFirstLastClipping(true)
        }
    }

    private fun showDailyBarChartWithZeros(
        chart: BarChart,
        fromMillis: Long,
        endMillis: Long,
        rows: List<ChartDayTotal>
    ) {
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
            chart.clear()
            chart.invalidate()
            return
        }

        val dataSet = BarDataSet(entries, "Spend").apply {
            valueTextSize = 10f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value == 0f) "" else value.toInt().toString()
                }
            }
        }

        chart.data = BarData(dataSet).apply { barWidth = 0.7f }

        chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val i = value.toInt()
                return if (i in labels.indices) labels[i] else ""
            }
        }

        chart.xAxis.apply {
            granularity = 1f
            isGranularityEnabled = true
            labelCount = labels.size
            setAvoidFirstLastClipping(false)
        }

        chart.setFitBars(true)
        chart.xAxis.axisMinimum = -0.5f
        chart.xAxis.axisMaximum = entries.size - 0.5f
        chart.setExtraOffsets(8f, 0f, 8f, 10f)

        chart.invalidate()
        chart.animateY(600)
    }

    private fun showWeeklyTotalsBarChartForMonth(
        chart: BarChart,
        year: Int,
        month: Int,
        dayRows: List<ChartDayTotal>
    ) {
        val zone = ZoneId.systemDefault()

        val dayMap = HashMap<Long, Double>()
        for (r in dayRows) {
            val d = Instant.ofEpochMilli(r.dayStart).atZone(zone).toLocalDate()
            val key = d.atStartOfDay(zone).toInstant().toEpochMilli()
            dayMap[key] = (dayMap[key] ?: 0.0) + r.total
        }

        val ym = YearMonth.of(year, month)
        val startOfMonth = ym.atDay(1)
        val endOfMonth = ym.atEndOfMonth()

        val weekTotals = mutableListOf<Double>()
        val weekLabels = mutableListOf<String>()

        var start = startOfMonth
        var weekNo = 1

        while (!start.isAfter(endOfMonth)) {
            val dow = start.dayOfWeek.value
            val daysToSunday = (7 - dow).toLong()
            var end = start.plusDays(daysToSunday)
            if (end.isAfter(endOfMonth)) end = endOfMonth

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

        val entries = weekTotals.mapIndexed { i, total -> BarEntry(i.toFloat(), total.toFloat()) }
        if (entries.isEmpty()) {
            chart.clear()
            chart.invalidate()
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

        chart.data = BarData(dataSet).apply { barWidth = 0.6f }

        chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val i = value.toInt()
                return if (i in weekLabels.indices) weekLabels[i] else ""
            }
        }

        chart.xAxis.apply {
            granularity = 1f
            isGranularityEnabled = true
            labelRotationAngle = 0f
            setDrawGridLines(false)
        }

        chart.setFitBars(true)
        chart.xAxis.axisMinimum = -0.5f
        chart.xAxis.axisMaximum = entries.size - 0.5f

        chart.invalidate()
        chart.animateY(600)
    }

    // ------------------- weekend/weekday split -------------------

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

            tvWeekendPct.text = String.format(Locale.US, "%.0f%%", weekendPct)
            tvWeekdayPct.text = String.format(Locale.US, "%.0f%%", weekdayPct)

            tvWeekendInfo.text = "₹" + String.format(Locale.US, "%,.0f", weekendAmt) + " • $weekendTxn txns"
            tvWeekdayInfo.text = "₹" + String.format(Locale.US, "%,.0f", weekdayAmt) + " • $weekdayTxn txns"

            val wWeight = (weekendPct / 100.0).toFloat().coerceIn(0.02f, 0.98f)
            val dWeight = 1f - wWeight

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

            data class WeekRangeLocal(val weekNo: Int, val start: LocalDate, val end: LocalDate)

            val ranges = mutableListOf<WeekRangeLocal>()
            var start = startOfMonth
            var weekNo = 1

            while (!start.isAfter(endOfMonth)) {
                val dow = start.dayOfWeek.value
                val daysToSunday = (7 - dow).toLong()
                var end = start.plusDays(daysToSunday)
                if (end.isAfter(endOfMonth)) end = endOfMonth
                ranges.add(WeekRangeLocal(weekNo, start, end))
                weekNo++
                start = end.plusDays(1)
            }

            val result = ranges.map { r ->
                WeekSplitItem(
                    weekNo = r.weekNo,
                    weekendAmt = 0.0,
                    weekendTxn = 0,
                    weekdayAmt = 0.0,
                    weekdayTxn = 0
                )
            }.toMutableList()

            fun findWeekIndex(d: LocalDate): Int {
                return ranges.indexOfFirst { !d.isBefore(it.start) && !d.isAfter(it.end) }
            }

            for (t in txns) {
                val d = Instant.ofEpochMilli(t.date).atZone(zone).toLocalDate()
                val idx = findWeekIndex(d)
                if (idx == -1) continue

                val isWeekend = (d.dayOfWeek.value == 6 || d.dayOfWeek.value == 7)
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

            val finalList = result.filter { (it.weekendAmt + it.weekdayAmt) > 0.0 }
            weekSplitAdapter.submitList(finalList)
        }
    }
}
