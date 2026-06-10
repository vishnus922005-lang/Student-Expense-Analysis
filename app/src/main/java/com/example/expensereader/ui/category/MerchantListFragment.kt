package com.example.expensereader.ui.category

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.expensereader.R
import com.example.expensereader.db.AppDatabase
import com.example.expensereader.model.Expense
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class MerchantListFragment : Fragment(R.layout.fragment_merchant_list) {

    private val categories = listOf(
        "Food", "Travel", "Shopping", "Bills & Utilities",
        "Entertainment", "Groceries", "Friends & Family", "Savings",
        "Rent/Hostel", "Education", "Health Medicine & Personal Care", "Others"
    )

    private lateinit var rvMerchants: RecyclerView
    private lateinit var rvUnknownSms: RecyclerView
    private lateinit var tvEmpty: TextView

    private lateinit var merchantAdapter: MerchantListAdapter
    private lateinit var unknownAdapter: UnknownSmsAdapter

    private var category: String = ""
    private var startMillis: Long = 0L
    private var endMillis: Long = 0L

    // ✅ the 1 category which should show Unknown SMS list instead of merchants
    private val specialCategoryForSms = "Others"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        category = requireArguments().getString("category").orEmpty()
        startMillis = requireArguments().getLong("startMillis")
        endMillis = requireArguments().getLong("endMillis")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvMerchants = view.findViewById(R.id.rvMerchants)
        rvUnknownSms = view.findViewById(R.id.rvUnknownSms)
        tvEmpty = view.findViewById(R.id.tvEmpty)

        rvMerchants.layoutManager = LinearLayoutManager(requireContext())
        rvUnknownSms.layoutManager = LinearLayoutManager(requireContext())

        rvMerchants.itemAnimator = null
        rvUnknownSms.itemAnimator = null

        val dao = AppDatabase.getInstance(requireContext()).expenseDao()

        // ✅ Unknown SMS adapter (Edit icon -> showEditDialog)
        unknownAdapter = UnknownSmsAdapter(
            onClick = { /* keep unchanged */ },
            onEditClick = { expense ->
                showEditDialog(expense)
            }
        )
        rvUnknownSms.adapter = unknownAdapter

        // ✅ Merchant adapter (NOW supports Edit All)
        merchantAdapter = MerchantListAdapter(
            onToggle = { merchant, expand ->
                if (category.equals(specialCategoryForSms, ignoreCase = true)) return@MerchantListAdapter

                if (expand) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        dao.getMerchantTransactionsInRange(category, merchant, startMillis, endMillis)
                            .collectLatest { list ->
                                merchantAdapter.updateTransactions(merchant, list)
                            }
                    }
                }
            },
            onEditAllClick = { merchantName ->
                showEditAllDialog(merchantName)
            }
        )

        rvMerchants.adapter = merchantAdapter

        refreshScreen()
    }

    private fun refreshScreen() {
        val dao = AppDatabase.getInstance(requireContext()).expenseDao()

        // ✅ SWITCH MODE
        if (category.equals(specialCategoryForSms, ignoreCase = true)) {

            rvMerchants.visibility = View.GONE
            rvUnknownSms.visibility = View.VISIBLE

            viewLifecycleOwner.lifecycleScope.launch {
                dao.getUnknownSmsExpensesInRange(startMillis, endMillis)
                    .collectLatest { list ->
                        unknownAdapter.submit(list)
                        tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    }
            }

        } else {

            rvUnknownSms.visibility = View.GONE
            rvMerchants.visibility = View.VISIBLE

            viewLifecycleOwner.lifecycleScope.launch {
                dao.getMerchantsForCategoryInRange(category, startMillis, endMillis)
                    .collectLatest { list ->
                        merchantAdapter.submitList(list)

                        if (list.isEmpty()) {
                            rvMerchants.visibility = View.GONE
                            tvEmpty.visibility = View.VISIBLE
                            tvEmpty.text = "No transactions"
                        } else {
                            rvMerchants.visibility = View.VISIBLE
                            tvEmpty.visibility = View.GONE
                        }
                    }
            }
        }
    }

    // ✅ Helpers used by showEditDialog (copied from your Home flow expectation)
    private fun normalizeKey(s: String?): String {
        return s.orEmpty()
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[^a-z0-9 ]"), "")
            .trim()
    }

    private fun preferDisplayName(a: String, b: String): String {
        // prefer longer + more spaced name
        val aa = a.trim()
        val bb = b.trim()
        if (bb.length > aa.length) return bb
        if (aa.length > bb.length) return aa
        // if equal length, prefer one with more spaces (looks like full name)
        val sa = aa.count { it == ' ' }
        val sb = bb.count { it == ' ' }
        return if (sb > sa) bb else aa
    }

    // ✅ Same UI logic as you pasted, but Home-only calls are replaced by refreshScreen()
    // ✅ Edit ONLY this SMS/transaction (used by UnknownSmsAdapter)
    private fun showEditDialog(expense: Expense) {

        val cobalt = ContextCompat.getColor(requireContext(), R.color.cobalt_blue)

        val nameEt = AutoCompleteTextView(requireContext()).apply {
            setText(expense.name, false)
            hint = "Name"
            threshold = 1
            setBackgroundResource(R.drawable.bg_edit_white)
            setTextColor(Color.BLACK)
            setHintTextColor(Color.GRAY)
            setDropDownBackgroundDrawable(ColorDrawable(Color.WHITE))
        }

        val spinner = Spinner(requireContext()).apply {
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_spinner_blue_outline)
            adapter = ArrayAdapter(
                requireContext(),
                R.layout.item_spinner_black,
                categories
            ).also {
                it.setDropDownViewResource(R.layout.item_spinner_black_dropdown)
            }
        }

        val selectedIndex =
            categories.indexOf(expense.category).takeIf { it >= 0 } ?: categories.indexOf("Others")
        spinner.setSelection(selectedIndex)

        val suggestedBox = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setBackgroundColor(Color.WHITE)
        }

        val suggestedTitle = TextView(requireContext()).apply {
            text = "Choose name for THIS SMS (same Acc No):"
            textSize = 13f
            setPadding(0, 12, 0, 8)
            setTextColor(cobalt)
            setBackgroundColor(Color.WHITE)
        }
        suggestedBox.addView(suggestedTitle)

        val suggestedList = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        suggestedBox.addView(suggestedList)

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 10)
            setBackgroundColor(Color.WHITE)

            addView(nameEt)
            addView(View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 14
                )
                setBackgroundColor(Color.WHITE)
            })
            addView(spinner)
            addView(suggestedBox)
        }

        val scrollView = android.widget.ScrollView(requireContext()).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(
                container,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        scrollView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.75f).toInt()
        )

        val isUnknownSms = expense.name.trim().startsWith("unknown", ignoreCase = true)

        val dialog = AlertDialog.Builder(
            requireContext(),
            android.R.style.Theme_Material_Light_Dialog_Alert
        )
            .setTitle("Edit Expense (Only this SMS)")
            .setView(scrollView)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .show()

        dialog.findViewById<TextView>(
            requireContext().resources.getIdentifier("alertTitle", "id", "android")
        )?.setTextColor(cobalt)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(cobalt)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(cobalt)

        // ✅ Load suggestions (optional)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val dao = AppDatabase.getInstance(requireContext()).expenseDao()

                val rawNames = dao.getAllKnownNamesForSuggest()
                val grouped = mutableMapOf<String, String>()
                for (n in rawNames) {
                    val key = normalizeKey(n)
                    if (key.isBlank()) continue
                    val existing = grouped[key]
                    grouped[key] = if (existing == null) n else preferDisplayName(existing, n)
                }
                val suggestionListGlobal = grouped.values.distinct().sorted()

                nameEt.setAdapter(
                    ArrayAdapter(
                        requireContext(),
                        R.layout.item_dropdown_black,
                        suggestionListGlobal
                    )
                )

                // ✅ Special helper for unknown sms: show same-account suggested names
                if (isUnknownSms) {
                    val acc = expense.merchantAcc?.trim()
                    if (!acc.isNullOrBlank()) {

                        val namesForAcc: List<String> = dao.getKnownNamesByMerchantAcc(acc)

                        if (namesForAcc.isNotEmpty()) {

                            val merged = namesForAcc
                                .filter { it.isNotBlank() }
                                .groupBy { normalizeKey(it) }
                                .mapValues { (_, list) ->
                                    list.reduce { a, b -> preferDisplayName(a, b) }
                                }

                            val finalNames = merged.values.distinct().sorted()
                            suggestedList.removeAllViews()

                            // ✅ If only 1 choice, auto apply it
                            if (finalNames.size == 1) {
                                val autoName = finalNames.first()

                                val resolvedCat =
                                    com.example.expensereader.ml.CategoryResolver.resolve(requireContext(), autoName)
                                val catGuess =
                                    if (resolvedCat.isNotBlank()) resolvedCat
                                    else (dao.getLastCategoryForName(autoName) ?: "Others")

                                dao.updateNameCategory(expense.id, autoName, catGuess)
                                dao.clearNeedsStatementImport(expense.id)

                                hideKeyboard(nameEt)
                                nameEt.clearFocus()


                                dialog.dismiss()
                                Toast.makeText(requireContext(), "Auto updated: $autoName", Toast.LENGTH_SHORT).show()
                                refreshScreen()
                                return@launch
                            }

                            suggestedBox.visibility = View.VISIBLE

                            finalNames.forEach { nm ->
                                val btn = MaterialButton(
                                    requireContext(),
                                    null,
                                    com.google.android.material.R.attr.materialButtonOutlinedStyle
                                ).apply {
                                    text = nm
                                    isAllCaps = false
                                    setBackgroundColor(Color.WHITE)
                                    setTextColor(Color.BLACK)
                                    strokeColor = ContextCompat.getColorStateList(requireContext(), R.color.cobalt_blue)

                                    setOnClickListener {
                                        nameEt.setText(nm, false)
                                        nameEt.setSelection(nameEt.text?.length ?: 0)
                                        nameEt.dismissDropDown()

                                        viewLifecycleOwner.lifecycleScope.launch {
                                            try {
                                                val cg = dao.getLastCategoryForName(nm) ?: "Others"
                                                val idx = categories.indexOf(cg).takeIf { it >= 0 }
                                                    ?: categories.indexOf("Others")
                                                spinner.setSelection(idx)
                                            } catch (_: Exception) { }
                                        }
                                    }
                                }
                                suggestedList.addView(btn)
                            }
                        } else {
                            suggestedBox.visibility = View.GONE
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("EDIT_SUGGEST", "Failed loading suggestions", e)
            }
        }

        // ✅ Save (only this row)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {

            val newName = nameEt.text.toString().trim()
            val newCat = spinner.selectedItem?.toString()?.trim().orEmpty()

            if (newName.isBlank()) {
                Toast.makeText(requireContext(), "Name required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val dao = AppDatabase.getInstance(requireContext()).expenseDao()

                    dao.updateNameCategory(expense.id, newName, newCat)
                    dao.clearNeedsStatementImport(expense.id)

                    Toast.makeText(requireContext(), "Updated this SMS only", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    refreshScreen()

                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Update failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ✅ Edit ALL transactions for this merchant (bulk update)
    private fun showEditAllDialog(merchantName: String) {
        val cobalt = ContextCompat.getColor(requireContext(), R.color.cobalt_blue)

        val nameEt = AutoCompleteTextView(requireContext()).apply {
            setText(merchantName, false)
            hint = "New Merchant Name"
            threshold = 1
            setBackgroundResource(R.drawable.bg_edit_white)
            setTextColor(Color.BLACK)
            setHintTextColor(Color.GRAY)
            setDropDownBackgroundDrawable(ColorDrawable(Color.WHITE))
        }

        val spinner = Spinner(requireContext()).apply {
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_spinner_blue_outline)
            adapter = ArrayAdapter(
                requireContext(),
                R.layout.item_spinner_black,
                categories
            ).also { it.setDropDownViewResource(R.layout.item_spinner_black_dropdown) }
        }

        val currentIdx = categories.indexOf(category).takeIf { it >= 0 } ?: categories.indexOf("Others")
        spinner.setSelection(currentIdx)

        val box = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 10)
            setBackgroundColor(Color.WHITE)
            addView(nameEt)
            addView(View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 14
                )
                setBackgroundColor(Color.WHITE)
            })
            addView(spinner)
        }

        val dialog = AlertDialog.Builder(
            requireContext(),
            android.R.style.Theme_Material_Light_Dialog_Alert
        )
            .setTitle("Edit ALL transactions")
            .setMessage("This will update every transaction of this merchant in the selected date range.")
            .setView(box)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .show()

        dialog.findViewById<TextView>(
            requireContext().resources.getIdentifier("alertTitle", "id", "android")
        )?.setTextColor(cobalt)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(cobalt)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(cobalt)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {

            val newName = nameEt.text.toString().trim()
            val newCat = spinner.selectedItem?.toString()?.trim().orEmpty()

            if (newName.isBlank()) {
                Toast.makeText(requireContext(), "Name required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val dao = AppDatabase.getInstance(requireContext()).expenseDao()

                    // Prefer merchantAcc bulk update if available
                    val acc = dao.getAnyMerchantAccForNameInRange(merchantName, startMillis, endMillis)

                    val updated = if (!acc.isNullOrBlank()) {
                        dao.bulkUpdateMerchantByAccInRange(acc, newName, newCat, startMillis, endMillis)
                    } else {
                        dao.bulkUpdateMerchantByNameInRange(merchantName, newName, newCat, startMillis, endMillis)
                    }

                    Toast.makeText(requireContext(), "Updated $updated transactions", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    refreshScreen()

                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Update failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun hideKeyboard(view: View?) {
        if (view == null) return
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }



}
