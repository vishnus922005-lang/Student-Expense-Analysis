package com.example.expensereader.ui.category

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.expensereader.R
import com.example.expensereader.db.MerchantSummaryRow
import com.example.expensereader.db.MerchantTxnRow
import com.google.android.material.button.MaterialButton

class MerchantListAdapter(
    private val onToggle: (merchant: String, expand: Boolean) -> Unit,
    private val onEditAllClick: (merchant: String) -> Unit
) : ListAdapter<MerchantSummaryRow, MerchantListAdapter.VH>(DIFF) {

    private var expandedMerchant: String? = null

    // ✅ cache loaded transactions so it doesn't reload every time
    private val txnCache = mutableMapOf<String, List<MerchantTxnRow>>()

    fun setExpanded(merchant: String?) {
        val prev = expandedMerchant
        expandedMerchant = merchant
        prev?.let { notifyItemChanged(indexOfMerchant(it)) }
        merchant?.let { notifyItemChanged(indexOfMerchant(it)) }
    }

    fun updateTransactions(merchant: String, list: List<MerchantTxnRow>) {
        txnCache[merchant] = list
        notifyItemChanged(indexOfMerchant(merchant))
    }

    private fun indexOfMerchant(merchant: String): Int {
        val list = currentList
        for (i in list.indices) {
            if (list[i].merchant == merchant) return i
        }
        return -1
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvMerchant: TextView = v.findViewById(R.id.tvMerchant)
        val tvInfo: TextView = v.findViewById(R.id.tvInfo)
        val ivToggle: ImageView = v.findViewById(R.id.ivToggle)
        val ivEditAll: ImageView = v.findViewById(R.id.ivEditAll)


        val expandContainer: LinearLayout = v.findViewById(R.id.expandContainer)
        val rvInlineTxns: RecyclerView = v.findViewById(R.id.rvInlineTxns)

        val inlineAdapter = MerchantInlineTxnAdapter()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_merchant_row, parent, false)

        return VH(v).apply {
            rvInlineTxns.layoutManager = LinearLayoutManager(parent.context)
            rvInlineTxns.adapter = inlineAdapter
            rvInlineTxns.itemAnimator = null
        }
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = getItem(position)

        holder.tvMerchant.text = row.merchant
        holder.tvInfo.text = "${row.txnCount} txns • ₹${row.total.toInt()}"

        val isExpanded = (row.merchant == expandedMerchant)

        holder.expandContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE
        holder.ivToggle.setImageResource(
            if (isExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
        )

        // ✅ show cached transactions if already loaded
        val cached = txnCache[row.merchant]
        if (isExpanded && cached != null) holder.inlineAdapter.submit(cached)
        if (!isExpanded) holder.inlineAdapter.submit(emptyList())

        // ✅ Edit All click
        holder.ivEditAll.setOnClickListener {
            onEditAllClick(row.merchant)
        }

        // Expand toggle (card click)
        holder.itemView.setOnClickListener {
            val newExpanded = if (isExpanded) null else row.merchant
            setExpanded(newExpanded)
            onToggle(row.merchant, newExpanded != null)
        }

        // Expand toggle (arrow click)
        holder.ivToggle.setOnClickListener {
            val newExpanded = if (isExpanded) null else row.merchant
            setExpanded(newExpanded)
            onToggle(row.merchant, newExpanded != null)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MerchantSummaryRow>() {
            override fun areItemsTheSame(oldItem: MerchantSummaryRow, newItem: MerchantSummaryRow): Boolean =
                oldItem.merchant == newItem.merchant

            override fun areContentsTheSame(oldItem: MerchantSummaryRow, newItem: MerchantSummaryRow): Boolean =
                oldItem == newItem
        }
    }
}
