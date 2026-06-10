package com.example.expensereader.ui.analysis

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.expensereader.R
import com.example.expensereader.db.MerchantTotalRow
import java.util.Locale

class CategoryMerchantBarsAdapter :
    RecyclerView.Adapter<CategoryMerchantBarsAdapter.VH>() {

    data class CategoryBlock(
        val category: String,
        val rows: List<MerchantTotalRow>
    )

    private val items = mutableListOf<CategoryBlock>()

    fun submit(list: List<CategoryBlock>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_list, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]

        // ✅ BIG category name at TOP of box
        holder.tvCategoryTitle.text = item.category.uppercase(Locale.ROOT)

        // ✅ keep curved pill + set category color
        val color = CategoryColorMap.colorFor(item.category)
        holder.tvCategoryTitle.backgroundTintList = ColorStateList.valueOf(color)

        val totalCategoryAmount =
            item.rows.sumOf { it.totalAmount }.takeIf { it > 0 } ?: 1.0
        val maxAmount =
            item.rows.maxOfOrNull { it.totalAmount }?.takeIf { it > 0 } ?: 1.0

        val barRows = item.rows.map { r ->
            MerchantBarRow(
                merchant = r.merchant,
                amount = r.totalAmount,
                txnCount = r.txnCount,
                percent = (r.totalAmount / totalCategoryAmount) * 100.0, // % of category total
                ratio = (r.totalAmount / maxAmount).toFloat()            // bar length vs max merchant
            )
        }

        holder.inner.submit(barRows)
    }

    override fun getItemCount(): Int = items.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        // ✅ new id from updated XML
        val tvCategoryTitle: TextView = v.findViewById(R.id.tvCategoryTitle)

        private val rv: RecyclerView = v.findViewById(R.id.rvMerchantBars)
        val inner = MerchantBarAdapter()

        init {
            rv.layoutManager = LinearLayoutManager(v.context)
            rv.adapter = inner
        }
    }
}
