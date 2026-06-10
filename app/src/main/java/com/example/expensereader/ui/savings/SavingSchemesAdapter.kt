package com.example.expensereader.ui.savings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.expensereader.databinding.ItemSavingSchemeBinding
import com.example.expensereader.model.FbSchemeUi

class SavingSchemesAdapter(
    private val onApply: (FbSchemeUi) -> Unit,
    private val onTrack: (FbSchemeUi) -> Unit
) : RecyclerView.Adapter<SavingSchemesAdapter.VH>() {

    private var items: List<FbSchemeUi> = emptyList()

    fun submit(list: List<FbSchemeUi>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemSavingSchemeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(private val b: ItemSavingSchemeBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: FbSchemeUi) {
            val s = item.data
            b.tvTitle.text = s.title
            b.tvMeta.text = "${s.provider} • ${s.state} • ${s.category}"
            b.tvSavings.text = "Estimated: ₹${s.savingEstimate.minMonthly}–₹${s.savingEstimate.maxMonthly} / month"

            b.btnApply.setOnClickListener { onApply(item) }
            b.btnTrack.setOnClickListener { onTrack(item) }
        }
    }
}
