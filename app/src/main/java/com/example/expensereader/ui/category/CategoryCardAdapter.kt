package com.example.expensereader.ui.category

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.expensereader.R
import com.example.expensereader.db.CategoryTotal
import java.util.Locale

class CategoryCardAdapter(
    private val onClick: (CategoryTotal) -> Unit
) : RecyclerView.Adapter<CategoryCardAdapter.VH>() {

    private val items = mutableListOf<CategoryTotal>()

    // ✅ total spend across the list shown
    private var totalSpend: Double = 0.0

    // 🔹 TEMP BUDGET MAP (can later move to DB / Prefs)
    private val budgetMap = mapOf(
        "food" to 5000.0,
        "shopping" to 4000.0,
        "travel" to 3000.0,
        "groceries" to 3500.0,
        "bills & utilities" to 2500.0,
        "entertainment" to 2000.0,
        "rent/hostel" to 8000.0,
        "education" to 6000.0,
        "health medicine & personal care" to 2500.0,
        "savings" to 4000.0,
        "friends & family" to 2000.0,
        "others" to 1500.0
    )

    fun submit(list: List<CategoryTotal>) {
        items.clear()
        items.addAll(list)

        // ✅ total for % of total (all 12 = 100%)
        totalSpend = list.sumOf { it.total }

        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_card, parent, false)
        return VH(v, onClick, budgetMap)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], totalSpend)
    }

    override fun getItemCount(): Int = items.size

    class VH(
        itemView: View,
        private val onClick: (CategoryTotal) -> Unit,
        private val budgetMap: Map<String, Double>
    ) : RecyclerView.ViewHolder(itemView) {

        private val ivIcon: ImageView = itemView.findViewById(R.id.ivIcon)
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvBudget: TextView = itemView.findViewById(R.id.tvBudget)
        private val tvTxnCount: TextView = itemView.findViewById(R.id.tvTxnCount)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)

        fun bind(row: CategoryTotal, totalSpend: Double) {
            tvName.text = row.name
            tvAmount.text = "₹" + String.format(Locale.US, "%.2f", row.total)
            tvTxnCount.text = "${row.txnCount} transactions"

            // 🔹 If you still want to use budgets later, keep this:
            val key = normalize(row.name)
            val budget = budgetMap[key] ?: 0.0
            // (budget variable kept; not used now because you want 100% total share)

            // ✅ % of TOTAL spend (all 12 categories sum to 100%)
            tvBudget.text =
                if (totalSpend > 0) {
                    val pct = (row.total / totalSpend) * 100.0
                    String.format(Locale.US, "%.1f%% of total", pct)
                } else {
                    "0.0% of total"
                }

            ivIcon.setImageResource(iconFor(row.name))
            itemView.setOnClickListener { onClick(row) }
        }

        private fun normalize(name: String): String =
            name.trim()
                .lowercase(Locale.ROOT)
                .replace(Regex("\\s+"), " ")

        private fun iconFor(name: String): Int {
            return when (normalize(name)) {
                "food" -> R.drawable.ic_food
                "shopping" -> R.drawable.ic_shopping
                "travel" -> R.drawable.ic_travel
                "groceries" -> R.drawable.ic_grocery
                "bills & utilities" -> R.drawable.ic_bill
                "entertainment" -> R.drawable.ic_entertainment
                "rent/hostel" -> R.drawable.ic_rent
                "education" -> R.drawable.ic_education
                "health medicine & personal care" -> R.drawable.ic_health
                "savings" -> R.drawable.ic_savings
                "friends & family" -> R.drawable.ic_family
                "others" -> R.drawable.ic_others
                else -> R.drawable.ic_category
            }
        }
    }
}
