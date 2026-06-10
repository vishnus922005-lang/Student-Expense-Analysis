package com.example.expensereader.ui.analysis

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.expensereader.R
import com.example.expensereader.model.CategorySummary   // ✅ ADD THIS
import java.util.Locale

class CategorySummaryAdapter :
    ListAdapter<CategorySummary, CategorySummaryAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<CategorySummary>() {
        override fun areItemsTheSame(oldItem: CategorySummary, newItem: CategorySummary) =
            oldItem.category == newItem.category

        override fun areContentsTheSame(oldItem: CategorySummary, newItem: CategorySummary) =
            oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_summary_grid, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val dot = itemView.findViewById<View>(R.id.vColorDot)
        private val tvCategory = itemView.findViewById<TextView>(R.id.tvCategory)
        private val tvAmount = itemView.findViewById<TextView>(R.id.tvAmount)
        private val tvPercent = itemView.findViewById<TextView>(R.id.tvPercent)
        private val tvTxn = itemView.findViewById<TextView>(R.id.tvTxn)

        fun bind(row: CategorySummary) {
            tvCategory.text = row.category
            tvAmount.text = "₹" + String.format(Locale.US, "%.2f", row.totalAmount)
            tvPercent.text = String.format(Locale.US, "%.0f%%", row.percent)
            tvTxn.text = "${row.txnCount} txns"

            val color = CategoryColorMap.colorFor(row.category)

            val d = GradientDrawable()
            d.shape = GradientDrawable.OVAL
            d.setColor(color)
            dot.background = d
        }
    }
}
