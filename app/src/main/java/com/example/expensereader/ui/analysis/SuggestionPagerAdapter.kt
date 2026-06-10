package com.example.expensereader.ui.analysis

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.expensereader.R

class SuggestionPagerAdapter(
    private var items: List<SuggestionItem> = emptyList()
) : RecyclerView.Adapter<SuggestionPagerAdapter.VH>() {

    fun submitList(newItems: List<SuggestionItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_suggestion_card, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tv = itemView.findViewById<TextView>(R.id.tvSuggestionText)
        fun bind(item: SuggestionItem) {
            tv.text = item.text
        }
    }
}
