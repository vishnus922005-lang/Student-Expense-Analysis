package com.example.expensereader.ui.analysis

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.expensereader.R
import com.example.expensereader.db.AppDatabase
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlinx.coroutines.launch
import java.util.Calendar
import com.example.expensereader.model.CategorySummary


class MonthlyAnalysisFragment : Fragment(R.layout.fragment_monthly_analysis) {

    // NOTE: keep category names same as your DB stored values
    private val categories = listOf(
        "Food", "Travel", "Shopping", "Bills & Utilities",
        "Entertainment", "Groceries", "Friends & Family", "Savings",
        "Rent/Hostel", "Education", "Health ,Medicine & personal care", "Others"
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val monthDrop = view.findViewById<MaterialAutoCompleteTextView>(R.id.monthDropdown)
        val yearDrop = view.findViewById<MaterialAutoCompleteTextView>(R.id.yearDropdown)
        val pie = view.findViewById<PieChart>(R.id.categoryPieChart)

        val rv = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.categorySummaryRecycler)
        val adapter = CategorySummaryAdapter()
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        // Month options
        val monthNames = listOf(
            "January","February","March","April","May","June",
            "July","August","September","October","November","December"
        )
        monthDrop.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, monthNames))

        // Year options
        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR)
        val years = (currentYear downTo (currentYear - 5)).map { it.toString() }
        yearDrop.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, years))

        // Default current month/year
        monthDrop.setText(monthNames[now.get(Calendar.MONTH)], false)
        yearDrop.setText(currentYear.toString(), false)

        fun refresh() {
            val monthIndex = monthNames.indexOf(monthDrop.text.toString()).coerceAtLeast(0)
            val year = yearDrop.text.toString().toIntOrNull() ?: currentYear

            val (startMillis, endMillis) = monthRangeMillis(year, monthIndex)

            lifecycleScope.launch {
                val dao = AppDatabase.getInstance(requireContext()).expenseDao()
                val rows = dao.getCategorySummaryBetween(startMillis, endMillis)

                val map = rows.associateBy { it.category.trim() }

                val list12 = categories.map { cat ->
                    val r = map[cat]
                    CategorySummary(
                        category = cat,
                        totalAmount = r?.totalAmount ?: 0.0,
                        txnCount = r?.txnCount ?: 0
                    )
                }

                adapter.submitList(list12)
                updatePie(pie, list12)
            }
        }

        monthDrop.setOnItemClickListener { _, _, _, _ -> refresh() }
        yearDrop.setOnItemClickListener { _, _, _, _ -> refresh() }

        refresh()
    }

    private fun monthRangeMillis(year: Int, monthIndex0: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, monthIndex0)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        return start to cal.timeInMillis
    }

    private fun updatePie(pie: PieChart, list: List<CategorySummary>) {
        val entries = list.filter { it.totalAmount > 0.0 }
            .map { PieEntry(it.totalAmount.toFloat(), it.category) }

        if (entries.isEmpty()) {
            pie.clear()
            pie.setNoDataText("No data for selected month")
            pie.invalidate()
            return
        }

        val set = PieDataSet(entries, "")
        set.valueTextSize = 12f

        pie.data = PieData(set)
        pie.description.isEnabled = false
        pie.legend.isEnabled = true
        pie.invalidate()
    }
}
