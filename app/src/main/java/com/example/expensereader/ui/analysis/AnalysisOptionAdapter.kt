package com.example.expensereader.ui.analysis

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.expensereader.R

class AnalysisOptionAdapter(
    private val items: List<AnalysisOption>,
    private val onClick: (AnalysisOption) -> Unit
) : RecyclerView.Adapter<AnalysisOptionAdapter.OptionVH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OptionVH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_analysis_option, parent, false)
        return OptionVH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: OptionVH, position: Int) {
        holder.bind(items[position], onClick)
    }

    class OptionVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivOptionIcon)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvOptionTitle)
        private val ivNext: ImageView = itemView.findViewById(R.id.ivNext)

        fun bind(item: AnalysisOption, onClick: (AnalysisOption) -> Unit) {
            tvTitle.text = item.title
            ivIcon.setImageResource(item.iconRes)

            // Blue left icon + gray arrow
            ivIcon.setColorFilter(0xFF2563EB.toInt())
            ivNext.setColorFilter(0xFF9CA3AF.toInt())

            itemView.setOnClickListener { onClick(item) }
        }
    }
}
