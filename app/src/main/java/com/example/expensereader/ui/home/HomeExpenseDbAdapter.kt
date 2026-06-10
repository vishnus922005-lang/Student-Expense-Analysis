package com.example.expensereader.ui.home

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.expensereader.R
import com.example.expensereader.model.Expense
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeExpenseDbAdapter(
    private val onEdit: (Expense) -> Unit,
    private val onDelete: (Expense) -> Unit
) : ListAdapter<Expense, HomeExpenseDbAdapter.VH>(DIFF) {

    private val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).id

    fun submit(list: List<Expense>) {
        submitList(list)
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tvName)
        val tvCategory: TextView = v.findViewById(R.id.tvCategory)
        val tvTxnSource: TextView? = v.findViewById(R.id.tvTxnSource) // optional if exists
        val tvTime: TextView = v.findViewById(R.id.tvTime)
        val tvAmount: TextView = v.findViewById(R.id.tvAmount)
        val btnEdit: ImageView = v.findViewById(R.id.btnEdit)
        val btnDelete: ImageView = v.findViewById(R.id.btnDelete) // ✅ NEW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_expense, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = getItem(position)
        val d = Date(e.date)

        val isPending = e.needsStatementImport

        holder.tvName.text = if (isPending) "Unknown" else e.name.orEmpty()

        holder.tvCategory.text =
            if (isPending) {
                "Others"
            } else {
                val cat = e.category?.trim().orEmpty()
                if (cat.isNotBlank()) cat else "Others"
            }

        // ✅ Dot + CASH / UPI (if your layout has tvTxnSource)
        holder.tvTxnSource?.let { tv ->
            when {
                e.source.equals("MANUAL", true) || e.merchantAcc.equals("CASH", true) -> {
                    tv.text = "● CASH"
                    tv.setTextColor(Color.parseColor("#16A34A")) // green
                    tv.visibility = View.VISIBLE
                }
                e.source.equals("SMS", true) && !e.upiRef.isNullOrBlank() -> {
                    tv.text = "● UPI"
                    tv.setTextColor(Color.parseColor("#16A34A")) // green
                    tv.visibility = View.VISIBLE
                }
                else -> tv.visibility = View.GONE
            }
        }

        holder.tvTime.text = "${timeFmt.format(d)} • ${dateFmt.format(d)}"
        holder.tvAmount.text = "₹ ${e.amount.toInt()}"

        holder.btnEdit.setOnClickListener { onEdit(e) }

        // ✅ Show delete icon ONLY for manual/cash
        val isManual =
            e.source.equals("MANUAL", ignoreCase = true) ||
            e.merchantAcc.equals("CASH", ignoreCase = true)

        holder.btnDelete.visibility = if (isManual) View.VISIBLE else View.GONE
        holder.btnDelete.setOnClickListener { onDelete(e) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Expense>() {
            override fun areItemsTheSame(oldItem: Expense, newItem: Expense): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Expense, newItem: Expense): Boolean =
                oldItem == newItem
        }
    }
}
