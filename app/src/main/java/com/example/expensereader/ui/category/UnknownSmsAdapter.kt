package com.example.expensereader.ui.category

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.expensereader.R
import com.example.expensereader.model.Expense
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UnknownSmsAdapter(
    private val onClick: (Expense) -> Unit,
    private val onEditClick: (Expense) -> Unit
) : RecyclerView.Adapter<UnknownSmsAdapter.VH>() {

    private val items = mutableListOf<Expense>()
    private val dtFmt = SimpleDateFormat("hh:mm a • dd MMM yyyy", Locale.getDefault())

    fun submit(list: List<Expense>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvTitle: TextView = v.findViewById(R.id.tvSmsTitle)
        val tvSub: TextView = v.findViewById(R.id.tvSmsSub)
        val tvAmt: TextView = v.findViewById(R.id.tvSmsAmount)
        val ivEdit: ImageView = v.findViewById(R.id.ivEditAll)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_unknown_sms, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = items[position]

        // Show like Home tab (Unknown-XXXX if you want)
        holder.tvTitle.text = e.name.ifBlank { "Unknown" }

        holder.tvSub.text = dtFmt.format(Date(e.date))
        holder.tvAmt.text = "₹" + String.format(Locale.US, "%.2f", e.amount)

        holder.itemView.setOnClickListener { onClick(e) }
        holder.ivEdit.setOnClickListener { onEditClick(e) }
    }
}
