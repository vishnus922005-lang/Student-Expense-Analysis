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
import com.example.expensereader.db.MonthSummaryRow
import java.util.Locale

class SemesterMonthAdapter :
    ListAdapter<MonthSummaryRow, SemesterMonthAdapter.VH>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_month_summary, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val vDot: View = itemView.findViewById(R.id.vDot)
        private val tvMonth: TextView = itemView.findViewById(R.id.tvMonth)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        private val tvTxn: TextView = itemView.findViewById(R.id.tvTxn)

        fun bind(row: MonthSummaryRow) {
            val monthName = monthShort(row.monthNo)

            tvMonth.text = monthName
            tvAmount.text = "₹" + String.format(Locale.US, "%,.0f", row.totalAmount)
            tvTxn.text = "${row.txnCount} txns"

            // ✅ dot color matches pie slice color
            val color = MonthColorMap.colorFor(monthName)
            (vDot.background as? GradientDrawable)?.setColor(color)
        }

        private fun monthShort(monthNo: Int): String {
            return when (monthNo) {
                1 -> "Jan"; 2 -> "Feb"; 3 -> "Mar"; 4 -> "Apr"; 5 -> "May"; 6 -> "Jun"
                7 -> "Jul"; 8 -> "Aug"; 9 -> "Sep"; 10 -> "Oct"; 11 -> "Nov"; 12 -> "Dec"
                else -> "—"
            }
        }
    }

    object Diff : DiffUtil.ItemCallback<MonthSummaryRow>() {
        override fun areItemsTheSame(oldItem: MonthSummaryRow, newItem: MonthSummaryRow): Boolean {
            return oldItem.monthNo == newItem.monthNo
        }

        override fun areContentsTheSame(oldItem: MonthSummaryRow, newItem: MonthSummaryRow): Boolean {
            return oldItem == newItem
        }
    }
}
