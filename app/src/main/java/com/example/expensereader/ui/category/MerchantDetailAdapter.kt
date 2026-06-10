package com.example.expensereader.ui.category

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.expensereader.R
import com.example.expensereader.db.MerchantTxnRow // ✅ because you defined it inside ExpenseDao.kt file
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MerchantDetailAdapter : RecyclerView.Adapter<MerchantDetailAdapter.VH>() {

    private val items = mutableListOf<MerchantTxnRow>()
    private val df = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    fun submit(list: List<MerchantTxnRow>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_merchant_txn, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = items[position]
        holder.tvDate.text = df.format(Date(row.date))
        holder.tvAmount.text = "₹%.2f".format(row.amount)
    }

    override fun getItemCount() = items.size

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDate: TextView = itemView.findViewById(R.id.tvTxnDate)
        val tvAmount: TextView = itemView.findViewById(R.id.tvTxnAmount)
    }
}
