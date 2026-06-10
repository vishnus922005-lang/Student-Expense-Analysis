package com.example.expensereader.ui.savings

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat
import com.example.expensereader.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.MaterialAutoCompleteTextView

class SchemeFilterBottomSheet(
    private val allRows: List<SavingSchemeRow>,
    private val currentState: String?,
    private val currentType: String?,
    private val currentName: String?,
    private val onApply: (state: String?, type: String?, name: String?) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val dialog = BottomSheetDialog(requireContext())
        val v = LayoutInflater.from(requireContext())
            .inflate(R.layout.bottomsheet_scheme_filter, null)

        val actState = v.findViewById<MaterialAutoCompleteTextView>(R.id.actState)
        val actType = v.findViewById<MaterialAutoCompleteTextView>(R.id.actType)
        val actName = v.findViewById<MaterialAutoCompleteTextView>(R.id.actSchemeName)

        // ✅ open dropdown on click (no it-shadowing)
        listOf(actState, actType, actName).forEach { act ->
            act.setOnClickListener { act.showDropDown() }
        }

        fun norm(s: String?): String =
            s?.trim()?.replace(Regex("\\s+"), " ").orEmpty()

        // ✅ State list (ONLY real states, no "All")
        val states = allRows.map { norm(it.stateOrUt) }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .sorted()

        val types = allRows.map { norm(it.schemeType) }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .sorted()

        actState.setAdapter(ArrayAdapter(requireContext(), R.layout.item_dropdown_black_text, states))
        actType.setAdapter(ArrayAdapter(requireContext(), R.layout.item_dropdown_black_text, types))

        val bg = ContextCompat.getDrawable(requireContext(), R.drawable.bg_dropdown_popup_white)
        actState.setDropDownBackgroundDrawable(bg)
        actType.setDropDownBackgroundDrawable(bg)
        actName.setDropDownBackgroundDrawable(bg)

        // ✅ restore previous selections
        actState.setText(norm(currentState), false)
        actType.setText(norm(currentType), false)
        actName.setText(norm(currentName), false)

        // ✅ Update names based on selected State + Type
        fun updateNames(selectedState: String?, selectedType: String?) {
            val s = norm(selectedState).ifBlank { "" }
            val t = norm(selectedType).ifBlank { "" }

            val filtered = allRows.filter { row ->
                val rowState = norm(row.stateOrUt)
                val rowType = norm(row.schemeType)

                val stateOk = s.isBlank() || rowState.equals(s, true)
                val typeOk = t.isBlank() || rowType.equals(t, true)

                stateOk && typeOk
            }

            val names = filtered.map { norm(it.schemeName) }
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }
                .sorted()

            actName.setAdapter(ArrayAdapter(requireContext(), R.layout.item_dropdown_black_text, names))

            // ✅ if current name is not valid anymore, clear it
            val curName = norm(actName.text?.toString())
            if (curName.isNotBlank() && names.none { it.equals(curName, true) }) {
                actName.setText("", false)
            }
        }

        // initial load of names
        updateNames(currentState, currentType)

        // ✅ When STATE changes -> clear TYPE + NAME (THIS FIXES your "All India shows only 2")
        actState.setOnItemClickListener { _, _, _, _ ->
            val selState = norm(actState.text?.toString())

            actState.setText(selState, false)

            // clear dependent filters
            actType.setText("", false)
            actName.setText("", false)

            updateNames(selState, null)
        }

        // ✅ When TYPE changes -> clear NAME
        actType.setOnItemClickListener { _, _, _, _ ->
            val selState = norm(actState.text?.toString())
            val selType = norm(actType.text?.toString())

            actType.setText(selType, false)

            actName.setText("", false)
            updateNames(selState, selType)
        }

        v.findViewById<View>(R.id.btnReset).setOnClickListener {
            actState.setText("", false)
            actType.setText("", false)
            actName.setText("", false)
            updateNames(null, null)
        }

        v.findViewById<View>(R.id.btnApply).setOnClickListener {
            val s = norm(actState.text?.toString()).ifBlank { null }
            val t = norm(actType.text?.toString()).ifBlank { null }
            val n = norm(actName.text?.toString()).ifBlank { null }

            Log.d("SCHEMES_FILTER", "Apply clicked: state=$s type=$t name=$n")

            onApply(s, t, n)
            dismiss()
        }

        dialog.setContentView(v)
        return dialog
    }
}
