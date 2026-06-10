package com.example.expensereader.ui.analysis

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.expensereader.R
import java.text.NumberFormat
import java.util.Locale

class MerchantBarAdapter : RecyclerView.Adapter<MerchantBarAdapter.VH>() {

    private val items = mutableListOf<MerchantBarRow>()

    fun submit(list: List<MerchantBarRow>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_merchant_bar_row, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = items[position]

        holder.tvMerchant.text = row.merchant
        holder.tvAmountInside.text = formatINR(row.amount)
        holder.tvPercent.text = "${row.percent.toInt()}%"
        holder.tvTxn.text = "${row.txnCount} txns"

        // ✅ Animate bar fill 0 -> ratio
        val target = row.ratio.coerceIn(0f, 1f)

        holder.barFill.pivotX = 0f
        holder.barFill.animate().cancel()
        holder.barFill.scaleX = 0f

        holder.barFill.animate()
            .scaleX(target)
            .setDuration(700)
            .setStartDelay((position * 20L).coerceAtMost(200L))
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        holder.barFill.animate().cancel()
    }

    override fun getItemCount(): Int = items.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvMerchant: TextView = v.findViewById(R.id.tvMerchant)
        val tvAmountInside: TextView = v.findViewById(R.id.tvAmountInside)
        val tvPercent: TextView = v.findViewById(R.id.tvPercent)
        val tvTxn: TextView = v.findViewById(R.id.tvTxn)
        val barFill: View = v.findViewById(R.id.barFill)
    }

    private fun formatINR(amount: Double): String {
        val nf = NumberFormat.getNumberInstance(Locale("en", "IN"))
        nf.maximumFractionDigits = 0
        return "₹" + nf.format(amount)
    }
}
