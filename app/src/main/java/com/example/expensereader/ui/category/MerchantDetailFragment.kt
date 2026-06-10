package com.example.expensereader.ui.category

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.expensereader.R
import com.example.expensereader.db.AppDatabase
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MerchantDetailFragment : Fragment(R.layout.fragment_merchant_detail) {

    private lateinit var rv: RecyclerView
    private lateinit var adapter: MerchantDetailAdapter
    private lateinit var tvTitle: TextView

    private var category: String = ""
    private var merchant: String = ""
    private var startMillis: Long = 0L
    private var endMillis: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        category = requireArguments().getString(ARG_CATEGORY).orEmpty()
        merchant = requireArguments().getString(ARG_MERCHANT).orEmpty()
        startMillis = requireArguments().getLong(ARG_START)
        endMillis = requireArguments().getLong(ARG_END)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvTitle = view.findViewById(R.id.tvMerchantTitle)
        tvTitle.text = merchant

        rv = view.findViewById(R.id.rvMerchantTxns)
        rv.layoutManager = LinearLayoutManager(requireContext())
        adapter = MerchantDetailAdapter()
        rv.adapter = adapter

        val dao = AppDatabase.getInstance(requireContext()).expenseDao()

        viewLifecycleOwner.lifecycleScope.launch {
            dao.getMerchantTransactionsInRange(category, merchant, startMillis, endMillis)
                .collectLatest { list -> adapter.submit(list) }
        }
    }

    companion object {
        private const val ARG_CATEGORY = "category"
        private const val ARG_MERCHANT = "merchant"
        private const val ARG_START = "start"
        private const val ARG_END = "end"

        fun newInstance(category: String, merchant: String, startMillis: Long, endMillis: Long) =
            MerchantDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CATEGORY, category)
                    putString(ARG_MERCHANT, merchant)
                    putLong(ARG_START, startMillis)
                    putLong(ARG_END, endMillis)
                }
            }
    }
}
