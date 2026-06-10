package com.example.expensereader.ui.analysis

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.expensereader.R
import com.example.expensereader.db.AppDatabase
import com.example.expensereader.model.DayTotal
import com.example.expensereader.ui.category.SimpleItemSelectedListener
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.example.expensereader.model.CategorySummary
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class AnalysisFragment : Fragment(R.layout.fragment_analysis) {

    private lateinit var spYear: Spinner
    private lateinit var spMonth: Spinner
    private lateinit var pie: PieChart

    // ✅ Line chart
    private lateinit var lineChart: LineChart

    private lateinit var adapter: CategorySummaryAdapter
    private lateinit var years: List<Int>

    private var otherCategoriesColor: Int = Color.LTGRAY


    private val months = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    // ✅ Must match DB category strings used in your app
    private val allCategories = listOf(
        "Food", "Shopping", "Travel", "Groceries", "Bills & Utilities",
        "Entertainment", "Rent/Hostel", "Education", "Health Medicine & Personal Care",
        "Savings", "Friends & Family", "Others"
    )

    private var firstYear: Int = 0
    private var firstMonth: Int = 1

    private var monthNumbersShown: List<Int> = (1..12).toList()
    private var suppress = false

    private var currentStartMillis: Long = 0L
    private var currentEndMillis: Long = 0L
    private lateinit var budgetHeaderBinder: AnalysisBudgetHeaderBinder


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        otherCategoriesColor = ContextCompat.getColor(requireContext(), R.color.other_categories_color)


        budgetHeaderBinder = AnalysisBudgetHeaderBinder(view, viewLifecycleOwner.lifecycleScope)
        budgetHeaderBinder.bind()


        spYear = view.findViewById(R.id.spYear)
        spMonth = view.findViewById(R.id.spMonth)
        pie = view.findViewById(R.id.categoryPieChart)

        // ✅ line chart view
        lineChart = view.findViewById(R.id.last10DaysLineChart)
        setupLineChartUI(lineChart)

        val rv = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.categorySummaryRecycler)
        adapter = CategorySummaryAdapter()

        rv.layoutManager = GridLayoutManager(requireContext(), 2)
        rv.isNestedScrollingEnabled = false
        rv.adapter = adapter

        // ✅ NEW: Analysis options list (Frequency/Weekly/Monthly)
        setupAnalysisOptionsList(view)

        setupYearMonthSpinners()

        // ✅ load last 10 days immediately
        loadLast10DaysLine()
    }

    override fun onResume() {
        super.onResume()
        if (::budgetHeaderBinder.isInitialized) budgetHeaderBinder.refresh()
    }


    // ------------------ ✅ NEW LIST SETUP ------------------

    private fun setupAnalysisOptionsList(view: View) {
        val rvOptions = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvAnalysisOptions)

        val options = listOf(
            AnalysisOption("frequency", "Frequency Analysis", android.R.drawable.ic_menu_sort_by_size),
            AnalysisOption("weekly", "Weekly Analysis", android.R.drawable.ic_menu_week),
            AnalysisOption("monthly", "Monthly Analysis", android.R.drawable.ic_menu_month),
            AnalysisOption("semester", "Semester Analysis", android.R.drawable.ic_menu_agenda)
        )


        rvOptions.layoutManager = LinearLayoutManager(requireContext())
        rvOptions.isNestedScrollingEnabled = false
        rvOptions.adapter = AnalysisOptionAdapter(options) { item ->
            when (item.id) {
                "frequency" -> findNavController().navigate(R.id.action_analysisFragment_to_frequencyFragment)
                "weekly" -> findNavController().navigate(R.id.action_analysisFragment_to_weeklyFragment)
                "monthly" -> findNavController().navigate(R.id.action_analysisFragment_to_monthlyFragment)
                "semester" -> findNavController().navigate(R.id.action_analysisFragment_to_semesterFragment)
            }
        }
    }

    // ------------------ YOUR EXISTING CODE (UNCHANGED) ------------------

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
            )
            yearAdapter.setDropDownViewResource(R.layout.item_spinner_black_dropdown)
            spYear.adapter = yearAdapter

            spYear.onItemSelectedListener = SimpleItemSelectedListener {
                if (suppress) return@SimpleItemSelectedListener

                val yearPos = spYear.selectedItemPosition
                if (yearPos !in years.indices) return@SimpleItemSelectedListener
                val y = years[yearPos]

                suppress = true
                rebuildMonthSpinnerForYear(y, preferMonth = currentMonth)
                suppress = false

                val monthPos = spMonth.selectedItemPosition
                if (monthPos !in monthNumbersShown.indices) return@SimpleItemSelectedListener
                val m = monthNumbersShown[monthPos]

                loadForMonth(y, m)
            }

            spMonth.onItemSelectedListener = SimpleItemSelectedListener {
                if (suppress) return@SimpleItemSelectedListener

                val yearPos = spYear.selectedItemPosition
                val monthPos = spMonth.selectedItemPosition
                if (yearPos !in years.indices) return@SimpleItemSelectedListener
                if (monthPos !in monthNumbersShown.indices) return@SimpleItemSelectedListener

                val y = years[yearPos]
                val m = monthNumbersShown[monthPos]
                loadForMonth(y, m)
            }

            suppress = true
            val defaultYearIndex = years.indexOf(currentYear).let { if (it >= 0) it else 0 }
            spYear.setSelection(defaultYearIndex)

            val selectedYearPos = spYear.selectedItemPosition
            val selectedYear = if (selectedYearPos in years.indices) years[selectedYearPos] else currentYear
            rebuildMonthSpinnerForYear(selectedYear, preferMonth = currentMonth)
            suppress = false

            val initYearPos = spYear.selectedItemPosition
            val initMonthPos = spMonth.selectedItemPosition
            if (initYearPos in years.indices && initMonthPos in monthNumbersShown.indices) {
                loadForMonth(years[initYearPos], monthNumbersShown[initMonthPos])
            }
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
        )
        monthAdapter.setDropDownViewResource(R.layout.item_spinner_black_dropdown)
        spMonth.adapter = monthAdapter

        val wanted = if (preferMonth in startM..endM) preferMonth else 0
        val idx = monthNumbersShown.indexOf(wanted).takeIf { it >= 0 } ?: 0
        spMonth.setSelection(idx)
    }

    private fun loadForMonth(year: Int, month: Int) {
        val zone = ZoneId.systemDefault()
        val dao = AppDatabase.getInstance(requireContext()).expenseDao()

        viewLifecycleOwner.lifecycleScope.launch {

            val (fromMillis, endMillis) = withContext(Dispatchers.IO) {
                when {
                    year == -1 && month == 0 -> {
                        val start = dao.getFirstExpenseDate() ?: System.currentTimeMillis()
                        val end = System.currentTimeMillis()
                        start to end
                    }
                    year != -1 && month == 0 -> {
                        val start = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
                        val end = LocalDate.of(year, 12, 31).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                        start to end
                    }
                    year == -1 && month != 0 -> {
                        val startYear = firstYear
                        val endYear = LocalDate.now().year

                        val start = LocalDate.of(startYear, month, 1).atStartOfDay(zone).toInstant().toEpochMilli()
                        val end = LocalDate.of(endYear, month, 1)
                            .withDayOfMonth(YearMonth.of(endYear, month).lengthOfMonth())
                            .plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                        start to end
                    }
                    else -> {
                        val ym = YearMonth.of(year, month)
                        val start = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
                        val end = ym.atEndOfMonth().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                        start to end
                    }
                }
            }

            currentStartMillis = fromMillis
            currentEndMillis = endMillis

            val list12: List<CategorySummary> = withContext(Dispatchers.IO) {
                val rows = dao.getCategorySummaryBetween(fromMillis, endMillis)
                val map = rows.associateBy { it.category.trim().lowercase(Locale.ROOT) }

                val temp: List<CategorySummary> = allCategories.map { cat ->
                    val r = map[cat.trim().lowercase(Locale.ROOT)]
                    CategorySummary(
                        category = cat,
                        totalAmount = r?.totalAmount ?: 0.0,
                        txnCount = r?.txnCount ?: 0,
                        percent = 0f
                    )
                }

                val totalSum = temp.sumOf { it.totalAmount }.takeIf { it > 0.0 } ?: 1.0
                temp.map { row ->
                    row.copy(percent = ((row.totalAmount / totalSum) * 100.0).toFloat())
                }
            }

            adapter.submitList(list12)
            updatePie(pie, list12)

            // ✅ keep line graph updated
            loadLast10DaysLine()
        }
    }

    // ------------------ ✅ LINE GRAPH FUNCTIONS ------------------

    private fun setupLineChartUI(chart: LineChart) {
        chart.description.isEnabled = false
        chart.legend.isEnabled = false

        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(false)

        // ✅ tap highlight
        chart.isHighlightPerTapEnabled = true
        chart.isHighlightPerDragEnabled = false

        chart.axisRight.isEnabled = false

        chart.axisLeft.apply {
            textColor = Color.DKGRAY
            axisMinimum = 0f
            setDrawGridLines(true)
        }

        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            granularity = 1f
            textColor = Color.DKGRAY
            setLabelCount(5, true)
        }

        chart.invalidate()
    }

    private fun loadLast10DaysLine() {
        val dao = AppDatabase.getInstance(requireContext()).expenseDao()
        val zone = ZoneId.systemDefault()

        viewLifecycleOwner.lifecycleScope.launch {

            val today = LocalDate.now()
            val startDay = today.minusDays(9)

            val fromMillis = startDay.atStartOfDay(zone).toInstant().toEpochMilli()
            val toMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

            val rows: List<DayTotal> = withContext(Dispatchers.IO) {
                dao.getDailyTotalsBetween(fromMillis, toMillis)
            }

            val map = rows.associate { it.day to it.totalAmount }

            val df = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val labelDf = DateTimeFormatter.ofPattern("dd MMM")

            val labels = mutableListOf<String>()
            val entries = mutableListOf<Entry>()

            for (i in 0..9) {
                val d = startDay.plusDays(i.toLong())
                val key = d.format(df)
                val amount = map[key] ?: 0.0

                labels.add(d.format(labelDf))
                entries.add(Entry(i.toFloat(), amount.toFloat()))
            }

            renderLast10DaysChart(lineChart, entries, labels)
        }
    }

    private fun renderLast10DaysChart(
        chart: LineChart,
        entries: List<Entry>,
        labels: List<String>
    ) {
        val dataSet = LineDataSet(entries, "").apply {
            lineWidth = 2f
            setDrawCircles(true)
            circleRadius = 3f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER

            isHighlightEnabled = true
            highLightColor = Color.DKGRAY
            highlightLineWidth = 1f
        }
        

        chart.data = LineData(dataSet)

        // ✅ X Axis labels
        chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val idx = value.toInt()
                return if (idx in labels.indices) labels[idx] else ""
            }
        }

        // ✅ Y Axis – Rupees formatter
        chart.axisLeft.apply {
            axisMinimum = 0f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return "₹" + value.toInt()
                }
            }
        }

        chart.axisRight.isEnabled = false

        val marker = Last10DaysMarkerView(requireContext(), labels)
        marker.chartView = chart
        chart.marker = marker

        chart.invalidate()
    }


    // ------------------ PIE CHART (YOUR EXISTING CODE) ------------------

    private fun updatePie(pie: PieChart, list: List<CategorySummary>) {
        val thresholdPercent = 3f

        val big = list.filter { it.percent >= thresholdPercent && it.totalAmount > 0.0 }
        val small = list.filter { it.percent < thresholdPercent && it.totalAmount > 0.0 }

        val othersAmount = small.sumOf { it.totalAmount }
        val entries = mutableListOf<PieEntry>()

        big.forEach {
            entries.add(PieEntry(it.totalAmount.toFloat(), it.category))
        }

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
}
