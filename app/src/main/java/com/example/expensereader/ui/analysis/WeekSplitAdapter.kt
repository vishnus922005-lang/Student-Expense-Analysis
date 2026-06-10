package com.example.expensereader.ui.analysis

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.expensereader.R
import java.util.Locale

class WeekSplitAdapter : RecyclerView.Adapter<WeekSplitAdapter.VH>() {

    private val items = mutableListOf<WeekSplitItem>()

    fun submitList(list: List<WeekSplitItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_week_split, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val tvWeekTitle: TextView = itemView.findViewById(R.id.tvWeekTitle)

        // ✅ Weekdays LEFT, Weekend RIGHT
        private val tvWeekdayPct: TextView = itemView.findViewById(R.id.tvWeekdayPct)
        private val tvWeekendPct: TextView = itemView.findViewById(R.id.tvWeekendPct)

        private val tvWeekdayInfo: TextView = itemView.findViewById(R.id.tvWeekdayInfo)
        private val tvWeekendInfo: TextView = itemView.findViewById(R.id.tvWeekendInfo)

        // ✅ Weekdays LEFT bar, Weekend RIGHT bar
        private val vWeekdayBar: View = itemView.findViewById(R.id.vWeekdayBar)
        private val vWeekendBar: View = itemView.findViewById(R.id.vWeekendBar)

        fun bind(it: WeekSplitItem) {
            tvWeekTitle.text = "Week ${it.weekNo}"

            val total = (it.weekdayAmt + it.weekendAmt).takeIf { t -> t > 0 } ?: 1.0

            val weekdayPct = (it.weekdayAmt / total) * 100.0
            val weekendPct = 100.0 - weekdayPct

            // ✅ % values (Weekdays LEFT, Weekend RIGHT)
            tvWeekdayPct.text = String.format(Locale.US, "%.0f%%", weekdayPct)
            tvWeekendPct.text = String.format(Locale.US, "%.0f%%", weekendPct)

            // ✅ Amount + Txn (Weekdays LEFT, Weekend RIGHT)
            tvWeekdayInfo.text =
                "₹" + String.format(Locale.US, "%,.0f", it.weekdayAmt) + " • ${it.weekdayTxn} txns"
            tvWeekendInfo.text =
                "₹" + String.format(Locale.US, "%,.0f", it.weekendAmt) + " • ${it.weekendTxn} txns"

            // ✅ Bar weights (Weekdays LEFT, Weekend RIGHT)
            val wDay = (weekdayPct / 100.0).toFloat().coerceIn(0.02f, 0.98f)
            val wEnd = 1f - wDay

            val lpDay = vWeekdayBar.layoutParams as LinearLayout.LayoutParams
            lpDay.weight = wDay
            vWeekdayBar.layoutParams = lpDay

            val lpEnd = vWeekendBar.layoutParams as LinearLayout.LayoutParams
            lpEnd.weight = wEnd
            vWeekendBar.layoutParams = lpEnd
        }
    }
}
