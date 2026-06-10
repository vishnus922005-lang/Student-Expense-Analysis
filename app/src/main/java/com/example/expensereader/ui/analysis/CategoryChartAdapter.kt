package com.example.expensereader.ui.analysis

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.expensereader.R
import com.example.expensereader.db.MerchantTotalRow
import com.github.mikephil.charting.charts.HorizontalBarChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlin.math.min

class CategoryChartAdapter : RecyclerView.Adapter<CategoryChartAdapter.VH>() {

    data class CategoryBlock(
        val category: String,
        val rows: List<MerchantTotalRow>
    )

    private val items = mutableListOf<CategoryBlock>()

    fun submit(blocks: List<CategoryBlock>) {
        items.clear()
        items.addAll(blocks)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_chart, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = "${item.category} • Top 10"

        // Safety: take max 10
        val shown = item.rows.take(min(10, item.rows.size))

        val merchants = shown.map { it.merchant }
        val entries = shown.mapIndexed { idx, r ->
            BarEntry(idx.toFloat(), r.totalAmount.toFloat())
        }

        val dataSet = BarDataSet(entries, "Spend")
        val barData = BarData(dataSet).apply {
            setValueTextSize(10f)
        }

        holder.chart.apply {
            data = barData
            description.isEnabled = false
            legend.isEnabled = false
            setFitBars(true)

            axisRight.isEnabled = false
            axisLeft.axisMinimum = 0f

            // X-axis merchant labels (NO position set - fixes your build error)
            xAxis.apply {
                granularity = 1f
                setDrawGridLines(false)
                textSize = 10f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val i = value.toInt()
                        val raw = if (i in merchants.indices) merchants[i] else ""
                        return shorten(raw, 18)
                    }
                }
            }

            setScaleEnabled(false)
            invalidate()
        }
    }

    override fun getItemCount(): Int = items.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tvCategoryTitle)
        val chart: HorizontalBarChart = v.findViewById(R.id.barChart)
    }

    private fun shorten(s: String, max: Int): String {
        val t = s.trim()
        if (t.length <= max) return t
        return t.take(max - 1) + "…"
    }
}
