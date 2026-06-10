package com.example.expensereader.ui.analysis

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.expensereader.R
import com.example.expensereader.db.AppDatabase
import com.example.expensereader.ui.category.SimpleItemSelectedListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class FrequencyFragment : Fragment(R.layout.fragment_frequency) {

    private lateinit var spYear: Spinner
    private lateinit var spMonth: Spinner

    private lateinit var rvCategoryCharts: RecyclerView

    // ✅ CHANGED ONLY THIS: use your nested bar adapter (category -> merchants bars)
    private val categoryChartAdapter = CategoryMerchantBarsAdapter()

    private lateinit var years: List<Int>

    private val months = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    private var firstYear: Int = 0
    private var firstMonth: Int = 1

    private var monthNumbersShown: List<Int> = (1..12).toList()
    private var suppress = false

    private val TAG = "FrequencyFragment"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        spYear = view.findViewById(R.id.spYear)
        spMonth = view.findViewById(R.id.spMonth)

        rvCategoryCharts = view.findViewById(R.id.rvCategoryCharts)
        rvCategoryCharts.layoutManager = LinearLayoutManager(requireContext())
        rvCategoryCharts.adapter = categoryChartAdapter

        setupYearMonthSpinners()
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

            Log.d(TAG, "Frequency range: from=$fromMillis to=$endMillis (year=$year month=$month)")

            // ✅ SAME LOGIC: category list + top 10 merchants per category
            // ✅ ONLY TYPE CHANGED: CategoryMerchantBarsAdapter.CategoryBlock
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

            categoryChartAdapter.submit(blocks)
            Log.d(TAG, "Loaded category charts: categories=${blocks.size}")
        }
    }
}
