package com.example.expensereader.ui.savings

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout

object AddSchemeSavingDialog {
    fun show(context: Context, title: String, onDone: (amount: Double, note: String?) -> Unit) {
        val amountEt = EditText(context).apply {
            hint = "Amount saved (₹)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val noteEt = EditText(context).apply {
            hint = "Note (optional)"
        }

        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 0)
            addView(amountEt)
            addView(noteEt)
        }

        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val amt = amountEt.text.toString().toDoubleOrNull() ?: 0.0
                onDone(amt, noteEt.text?.toString()?.takeIf { it.isNotBlank() })
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
