package com.example.expensereader.ui.savings

import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.expensereader.R
import com.example.expensereader.util.BrowserUtils

class SavingSchemesSection(
    private val fragment: SavingsFragment
) {

    private var allRows: List<SavingSchemeRow> = emptyList()

    // current filters
    private var currentState: String? = null
    private var currentType: String? = null
    private var currentName: String? = null

    fun setup(root: View) {
        val rv = root.findViewById<RecyclerView>(R.id.rvSavingSchemes) ?: return
        val pb = root.findViewById<ProgressBar>(R.id.pbSchemes)
        val empty = root.findViewById<TextView>(R.id.tvSchemesEmpty)

        rv.layoutManager = LinearLayoutManager(root.context)

        val adapter = SavingSchemesCsvAdapter(
            onView = { item ->
                val url = item.officialWebsite.trim()
                if (url.isBlank() || !url.startsWith("http")) return@SavingSchemesCsvAdapter
                BrowserUtils.openUrl(root.context, url)
            }
        )
        rv.adapter = adapter

        try {
            pb?.visibility = View.VISIBLE
            empty?.visibility = View.GONE

            allRows = SavingSchemesCsvReader.readFromAssets(root.context)
            Log.d("SCHEMES", "CSV loaded rows=${allRows.size}")

            applyFilterAndShow(adapter, pb, empty)
        } catch (e: Exception) {
            pb?.visibility = View.GONE
            empty?.visibility = View.VISIBLE
            empty?.text = "Unable to load schemes"
            Log.e("SCHEMES", "CSV load failed", e)
        }

        root.findViewById<View>(R.id.btnSchemeFilter)?.setOnClickListener {
            Log.d("SCHEMES_FILTER", "Filter icon clicked")

            SchemeFilterBottomSheet(
                allRows = allRows,
                currentState = currentState,
                currentType = currentType,
                currentName = currentName
            ) { s, t, n ->
                currentState = s
                currentType = t
                currentName = n
                applyFilterAndShow(adapter, pb, empty)
            }.show(fragment.parentFragmentManager, "scheme_filter")
        }
    }

    private fun applyFilterAndShow(
        adapter: SavingSchemesCsvAdapter,
        pb: ProgressBar?,
        empty: TextView?
    ) {
        fun norm(s: String?): String =
            s?.trim()?.replace(Regex("\\s+"), " ").orEmpty()

        val selState = norm(currentState).ifBlank { null }
        val selType = norm(currentType).ifBlank { null }
        val selName = norm(currentName).ifBlank { null }

        val filtered = allRows.filter { row ->
            val rowState = norm(row.stateOrUt)
            val rowType = norm(row.schemeType)
            val rowName = norm(row.schemeName)

            val stateOk = selState.isNullOrBlank() || rowState.equals(selState, true)
            val typeOk  = selType.isNullOrBlank()  || rowType.equals(selType, true)
            val nameOk  = selName.isNullOrBlank()  || rowName.equals(selName, true)

            stateOk && typeOk && nameOk
        }

        Log.d(
            "SCHEMES_FILTER",
            "Filter result: state=$selState type=$selType name=$selName -> ${filtered.size}/${allRows.size}"
        )

        val uiList = filtered.map {
            SavingSchemeUi(
                stateOrUt = norm(it.stateOrUt),
                schemeName = norm(it.schemeName),
                schemeType = norm(it.schemeType),
                benefit = norm(it.benefit),
                officialWebsite = it.officialWebsite.trim()
            )
        }

        adapter.submit(uiList)

        pb?.visibility = View.GONE
        empty?.visibility = if (uiList.isEmpty()) View.VISIBLE else View.GONE
        if (uiList.isEmpty()) empty?.text = "No schemes found"
    }
}
