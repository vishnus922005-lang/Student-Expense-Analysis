package com.example.expensereader.ui.analysis

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.expensereader.R

class WeeklySummaryAdapter :
    ListAdapter<SummaryItem, WeeklySummaryAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<SummaryItem>() {
        override fun areItemsTheSame(oldItem: SummaryItem, newItem: SummaryItem) =
            oldItem.title == newItem.title

        override fun areContentsTheSame(oldItem: SummaryItem, newItem: SummaryItem) =
            oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_summary_card, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle = itemView.findViewById<TextView>(R.id.tvTitle)
        private val tvValue = itemView.findViewById<TextView>(R.id.tvValue)

        fun bind(item: SummaryItem) {
            tvTitle.text = item.title
            tvValue.text = item.value
        }
    }
}
