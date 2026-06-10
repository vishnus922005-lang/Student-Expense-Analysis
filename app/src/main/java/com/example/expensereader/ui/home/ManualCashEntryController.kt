package com.example.expensereader.ui.home

import android.app.DatePickerDialog
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import com.example.expensereader.R
import com.example.expensereader.db.AppDatabase
import com.example.expensereader.model.Expense
import com.example.expensereader.ocr.BillOcrParser
import com.example.expensereader.ocr.OcrTopToBottom
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ManualCashEntryController(
    private val root: View,
    private val scope: LifecycleCoroutineScope,
    private val onSaved: () -> Unit,
    private val onScanBillClick: () -> Unit
) {

    private val categories = listOf(
        "Food", "Travel", "Shopping", "Bills & Utilities",
        "Entertainment", "Groceries", "Friends & Family", "Savings",
        "Rent/Hostel", "Education", "Health ,Medicine & personal care", "Others"
    )

    private val etName: TextInputEditText = root.findViewById(R.id.etCashName)
    private val etAmount: TextInputEditText = root.findViewById(R.id.etCashAmount)
    private val etDate: TextInputEditText = root.findViewById(R.id.etCashDate)
    private val spCategory: Spinner = root.findViewById(R.id.spCashCategory)

    private val btnCancel: MaterialButton = root.findViewById(R.id.btnCashCancel)
    private val btnSave: MaterialButton = root.findViewById(R.id.btnCashSave)
    private val btnScan: MaterialButton = root.findViewById(R.id.btnScanBill)

    private var selectedDateMillis: Long = System.currentTimeMillis()

    // ----------------------------------------------------
    // Bind UI
    // ----------------------------------------------------
    fun bind() {

        spCategory.adapter = ArrayAdapter(
            root.context,
            R.layout.item_spinner_black,
            categories
        ).also { it.setDropDownViewResource(R.layout.item_spinner_black_dropdown) }

        spCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                (view as? TextView)?.setTextColor(Color.BLACK)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        forceBlack(etName)
        forceBlack(etAmount)
        forceBlack(etDate)

        setDateUi(selectedDateMillis)

        etDate.setOnClickListener { openDatePicker() }
        btnCancel.setOnClickListener { clearFields() }
        btnSave.setOnClickListener { saveCashExpense() }

        btnScan.setOnClickListener {
            onScanBillClick()   // Camera / picker handled by Fragment
        }
    }

    // ----------------------------------------------------
    // OCR ENTRY POINT (called from HomeFragment)
    // ----------------------------------------------------
    fun onBillPicked(uri: Uri?) {
        if (uri == null) return

        Toast.makeText(root.context, "Scanning bill…", Toast.LENGTH_SHORT).show()

        // ✅ Two recognizers: Latin (English) + Indic (Tamil/Indic scripts)
        val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val indicRecognizer =
            TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())

        try {
            val image = InputImage.fromFilePath(root.context, uri)

            latinRecognizer.process(image)
                .addOnSuccessListener { latinText ->

                    indicRecognizer.process(image)
                        .addOnSuccessListener { indicText ->

                            // ✅ Merge both outputs (no logic change)
                            val mergedRaw = (
                                latinText.text.orEmpty().trim() + "\n" +
                                    indicText.text.orEmpty().trim()
                                ).trim()

                            // ✅ Sort merged text TOP→BOTTOM (your existing ordering tool)
                            val orderedText = OcrTopToBottom.toTopBottomText(latinText)

                            // ✅ Logs (same style as before)
                            Log.d(
                                "BILL_OCR_MERGED",
                                "================ OCR MERGED (LATIN + INDIC) START ================"
                            )
                            mergedRaw.lines().forEachIndexed { i, line ->
                                Log.d("BILL_OCR_MERGED", "ROW ${i + 1} -> '$line'")
                            }
                            Log.d(
                                "BILL_OCR_MERGED",
                                "================ OCR MERGED (LATIN + INDIC) END =================="
                            )

                            Log.d(
                                "BILL_OCR_RAW",
                                "================ OCR TOP→BOTTOM START ================"
                            )
                            Log.d("BILL_OCR_RAW", orderedText)
                            Log.d(
                                "BILL_OCR_RAW",
                                "================ OCR TOP→BOTTOM END =================="
                            )

                            orderedText.lines().forEachIndexed { i, line ->
                                Log.d("BILL_OCR_LINE", "ROW ${i + 1} -> '$line'")
                            }

                            // ✅ Keep your existing flow (parser + category + amount)
                            applyOcrResult(orderedText)

                            Toast.makeText(root.context, "Bill scanned", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(
                                root.context,
                                "Indic OCR failed: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()

                            // fallback: still use Latin (ordered)
                            val orderedText = OcrTopToBottom.toTopBottomText(latinText)
                            applyOcrResult(orderedText)
                        }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(root.context, "Latin OCR failed: ${e.message}", Toast.LENGTH_LONG)
                        .show()
                }

        } catch (e: Exception) {
            Toast.makeText(
                root.context,
                "Cannot read image: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ----------------------------------------------------
    // APPLY OCR RESULT (BillOcrParser + AUTO CATEGORY)
    // ----------------------------------------------------
    fun applyOcrResult(ocrText: String) {

        val result = BillOcrParser.parse(ocrText)

        // ✅ Merchant name
        result.merchant?.let { merchantName ->
            etName.setText(merchantName)

            // ✅ AUTO CATEGORY RESOLUTION
            val autoCategory =
                com.example.expensereader.ml.CategoryResolver
                    .resolve(root.context, merchantName)
                    .trim()

            if (autoCategory.isNotBlank()) {
                val idx = categories.indexOf(autoCategory)
                if (idx >= 0) {
                    spCategory.setSelection(idx)
                }
            }
        }

        // ✅ Amount
        result.amount?.let { amt ->
            etAmount.setText(
                if (amt % 1 == 0.0) amt.toInt().toString()
                else amt.toString()
            )
        }
    }

    // ----------------------------------------------------
    // Helpers
    // ----------------------------------------------------
    private fun forceBlack(et: EditText) {
        et.setTextColor(Color.BLACK)
        et.setHintTextColor(Color.parseColor("#6B6B6B"))
    }

    fun clearFields() {
        etName.setText("")
        etAmount.setText("")
        spCategory.setSelection(categories.indexOf("Others").takeIf { it >= 0 } ?: 0)

        selectedDateMillis = System.currentTimeMillis()
        setDateUi(selectedDateMillis)

        forceBlack(etName)
        forceBlack(etAmount)
        forceBlack(etDate)
    }

    private fun setDateUi(millis: Long) {
        val fmt = SimpleDateFormat("dd MMM, yyyy", Locale.ENGLISH)
        etDate.setText(fmt.format(Date(millis)))
        etDate.setTextColor(Color.BLACK)
    }

    private fun openDatePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }

        DatePickerDialog(
            root.context,
            { _, y, m, d ->
                val c = Calendar.getInstance()
                c.set(y, m, d, 12, 0, 0)
                c.set(Calendar.MILLISECOND, 0)
                selectedDateMillis = c.timeInMillis
                setDateUi(selectedDateMillis)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // ----------------------------------------------------
    // Save Cash Expense
    // ----------------------------------------------------
    private fun saveCashExpense() {

        val name = etName.text?.toString()?.trim().orEmpty()
        val amountText = etAmount.text?.toString()?.trim().orEmpty()
        val category = spCategory.selectedItem?.toString()?.trim().orEmpty()

        if (name.isBlank()) {
            Toast.makeText(root.context, "Enter name", Toast.LENGTH_SHORT).show()
            return
        }

        val amount = amountText.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            Toast.makeText(root.context, "Enter valid amount", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launchWhenStarted {
            try {
                val dao = AppDatabase.getInstance(root.context).expenseDao()

                val exp = Expense(
                    name = name,
                    amount = amount,
                    date = selectedDateMillis,
                    category = category.ifBlank { "Others" },
                    type = "DEBIT",
                    source = "MANUAL",
                    accNo = null,
                    merchantAcc = "CASH",
                    upiRef = null,
                    userEdited = true,
                    needsStatementImport = false,
                    refNo = "",
                    accountId = null
                )

                dao.insert(exp)

                Toast.makeText(root.context, "Cash expense saved", Toast.LENGTH_SHORT).show()
                clearFields()
                onSaved()

            } catch (e: Exception) {
                Toast.makeText(
                    root.context,
                    "Save failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
