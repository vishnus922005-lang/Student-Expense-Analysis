package com.example.expensereader.ui.category

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.expensereader.R
import com.example.expensereader.db.MerchantTxnRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MerchantInlineTxnAdapter : RecyclerView.Adapter<MerchantInlineTxnAdapter.VH>() {

    // ✅ ONLY TYPE CHANGED
    private val items = mutableListOf<MerchantTxnRow>()

    // ✅ SAME FORMAT AS BEFORE
    private val dtFmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    // ✅ ONLY TYPE CHANGED
    fun submit(list: List<MerchantTxnRow>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvDate: TextView = v.findViewById(R.id.tvTxnDate)
        val tvAmount: TextView = v.findViewById(R.id.tvTxnAmount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_merchant_txn, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = items[position]
        holder.tvDate.text = dtFmt.format(Date(row.date))
        holder.tvAmount.text = "₹ " + row.amount.toInt()
    }
}
