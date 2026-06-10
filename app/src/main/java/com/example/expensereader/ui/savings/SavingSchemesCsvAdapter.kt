package com.example.expensereader.ui.savings

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.expensereader.R
import com.example.expensereader.databinding.ItemSavingSchemeCsvBinding

class SavingSchemesCsvAdapter(
    private val onView: (SavingSchemeUi) -> Unit
) : RecyclerView.Adapter<SavingSchemesCsvAdapter.VH>() {

    private var items: List<SavingSchemeUi> = emptyList()

    fun submit(list: List<SavingSchemeUi>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemSavingSchemeCsvBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    inner class VH(private val b: ItemSavingSchemeCsvBinding) : RecyclerView.ViewHolder(b.root) {

        fun bind(item: SavingSchemeUi) {
            b.tvSchemeName.text = item.schemeName
            b.tvTypeBadge.text = item.schemeType
            b.tvBenefit.text = item.benefit

            val state = item.stateOrUt.trim()
            b.tvStateBadge.text = state

            val isAllIndia = state.equals("All India", true)
            if (isAllIndia) {
                b.tvStateBadge.setBackgroundResource(R.drawable.bg_badge_white_border)
                b.tvStateBadge.setTextColor(Color.BLACK)
            } else {
                b.tvStateBadge.setBackgroundResource(R.drawable.bg_badge_blue)
                b.tvStateBadge.setTextColor(Color.WHITE)
            }

            b.btnView.setOnClickListener { onView(item) }
        }
    }
}
