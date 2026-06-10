package com.example.expensereader.ui.category

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.expensereader.R
import com.example.expensereader.db.AppDatabase
import com.example.expensereader.db.CategoryTotal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

class CategoryFragment : Fragment(R.layout.fragment_categories) {

    private lateinit var spYear: Spinner
    private lateinit var spMonth: Spinner
    private lateinit var tvHeaderTotal: TextView
    private lateinit var tvHeaderTxnCount: TextView
    private lateinit var rvCategories: RecyclerView

    private lateinit var categoryAdapter: CategoryCardAdapter
    private lateinit var years: List<Int>

    // Keep only 12 month names here (All handled in logic)
    private val months = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    // ✅ Your 12 categories (fixed casing + removed trailing spaces)
    private val allCategories = listOf(
        "Food", "Shopping", "Travel", "Groceries", "Bills & Utilities",
        "Entertainment", "Rent/Hostel", "Education", "Health Medicine & Personal Care",
        "Savings", "Friends & Family", "Others"
    )

    // Oldest data year/month from DB
    private var firstYear: Int = 0
    private var firstMonth: Int = 1

    // For mapping spinner position -> real month number (includes 0 = All when built)
    private var monthNumbersShown: List<Int> = (1..12).toList()

    // Prevent extra triggers when we rebuild month adapter
    private var suppress = false

    // keep selected range for opening MerchantList
    private var currentStartMillis: Long = 0L
    private var currentEndMillis: Long = 0L

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        spYear = view.findViewById(R.id.spYear)
        spMonth = view.findViewById(R.id.spMonth)
        tvHeaderTotal = view.findViewById(R.id.tvHeaderTotal)
        tvHeaderTxnCount = view.findViewById(R.id.tvHeaderTxnCount)
        rvCategories = view.findViewById(R.id.rvCategories)

        rvCategories.layoutManager = LinearLayoutManager(requireContext())

        categoryAdapter = CategoryCardAdapter { row ->
            val bundle = Bundle().apply {
                putString("category", row.name)
                putLong("startMillis", currentStartMillis)
                putLong("endMillis", currentEndMillis)
            }
            findNavController().navigate(
                R.id.action_categoryFragment_to_merchantListFragment,
                bundle
            )
        }

        rvCategories.adapter = categoryAdapter

        setupYearMonthSpinners()
    }

   

    private fun setupYearMonthSpinners() {
        val now = LocalDate.now()
        val currentYear = now.year
        val currentMonth = now.monthValue
        val zone = ZoneId.systemDefault()

        val dao = AppDatabase.getInstance(requireContext()).expenseDao()

        viewLifecycleOwner.lifecycleScope.launch {

            // Read first expense date from DB
            val firstMillis = withContext(Dispatchers.IO) { dao.getFirstExpenseDate() }

            if (firstMillis != null) {
                val d = Instant.ofEpochMilli(firstMillis).atZone(zone).toLocalDate()
                firstYear = d.year
                firstMonth = d.monthValue
            } else {
                firstYear = currentYear
                firstMonth = currentMonth
            }

            // Years: All(-1) + firstYear..currentYear
            years = listOf(-1) + (firstYear..currentYear).toList()

            val yearAdapter = ArrayAdapter(
                requireContext(),
                R.layout.item_spinner_black,
                years.map { if (it == -1) "All" else it.toString() }
            )
            yearAdapter.setDropDownViewResource(R.layout.item_spinner_black_dropdown)
            spYear.adapter = yearAdapter

            // YEAR listener (safe)
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

            // MONTH listener (safe)
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

            // Default: current year + current month
            suppress = true

            val defaultYearIndex = years.indexOf(currentYear).let { if (it >= 0) it else 0 }
            spYear.setSelection(defaultYearIndex)

            val selectedYearPos = spYear.selectedItemPosition
            val selectedYear = if (selectedYearPos in years.indices) years[selectedYearPos] else currentYear
            rebuildMonthSpinnerForYear(selectedYear, preferMonth = currentMonth)

            suppress = false

            // ✅ FIX: call the new function (not forceSpinnerTextBlack)
           

            // Initial load (safe)
            val initYearPos = spYear.selectedItemPosition
            val initMonthPos = spMonth.selectedItemPosition
            if (initYearPos in years.indices && initMonthPos in monthNumbersShown.indices) {
                loadForMonth(years[initYearPos], monthNumbersShown[initMonthPos])
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // reload current selection (so edits reflect immediately)
        val yPos = spYear.selectedItemPosition
        val mPos = spMonth.selectedItemPosition

        if (::spYear.isInitialized && ::spMonth.isInitialized &&
            yPos in years.indices && mPos in monthNumbersShown.indices
        ) {
            loadForMonth(years[yPos], monthNumbersShown[mPos])
        }
    }


    private fun rebuildMonthSpinnerForYear(selectedYear: Int, preferMonth: Int) {
        val now = LocalDate.now()
        val currentYear = now.year
        val currentMonth = now.monthValue

        val startM: Int
        val endM: Int

        if (selectedYear == -1) {
            // Year = All → allow all months
            startM = 1
            endM = 12
        } else {
            startM = if (selectedYear == firstYear) firstMonth else 1
            endM = if (selectedYear == currentYear) currentMonth else 12
        }

        // monthNumbersShown includes 0 for "All"
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

                if (year == -1 && month == 0) {
                    val start = dao.getFirstExpenseDate() ?: System.currentTimeMillis()
                    val end = System.currentTimeMillis()
                    Pair(start, end)
                } else if (year != -1 && month == 0) {
                    val start = LocalDate.of(year, 1, 1)
                        .atStartOfDay(zone).toInstant().toEpochMilli()
                    val end = LocalDate.of(year, 12, 31)
                        .plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                    Pair(start, end)
                } else if (year == -1 && month != 0) {
                    val startYear = firstYear
                    val endYear = LocalDate.now().year

                    val start = LocalDate.of(startYear, month, 1)
                        .atStartOfDay(zone).toInstant().toEpochMilli()

                    val end = LocalDate.of(endYear, month, 1)
                        .withDayOfMonth(YearMonth.of(endYear, month).lengthOfMonth())
                        .plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

                    Pair(start, end)
                } else {
                    val ym = YearMonth.of(year, month)
                    val start = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    val end = ym.atEndOfMonth()
                        .plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                    Pair(start, end)
                }
            }

            currentStartMillis = fromMillis
            currentEndMillis = endMillis

            val (total, txnCount, mergedCategories) = withContext(Dispatchers.IO) {
                val total = dao.getTotalSpendBetween(fromMillis, endMillis)
                val txnCount = dao.getTxnCountBetween(fromMillis, endMillis)

                val dbList = dao.getCategoryTotalsBetween(fromMillis, endMillis)
                val map = dbList.associateBy { it.name.trim().lowercase(Locale.ROOT) }

                val merged = allCategories.map { cat ->
                    map[cat.trim().lowercase(Locale.ROOT)] ?: CategoryTotal(cat, 0.0, 0)
                }

                val ranked = merged.sortedWith(
                    compareByDescending<CategoryTotal> { it.total }
                        .thenBy { it.name.lowercase(Locale.ROOT) }
                )

                Triple(total, txnCount, merged)
            }

            tvHeaderTotal.text = "₹" + String.format(Locale.US, "%.2f", total)
            tvHeaderTxnCount.text = "$txnCount transactions"
            categoryAdapter.submit(mergedCategories)
        }
    }
}
