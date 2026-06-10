package com.example.expensereader.importer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.expensereader.R
import com.example.expensereader.db.AppDatabase
import com.example.expensereader.ml.CategoryPredictor
import kotlinx.coroutines.launch

class StatementImportActivity : AppCompatActivity() {

    private val pickPdf = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            if (uri != null) {
                importPdf(uri)
            } else {
                Toast.makeText(this, "No PDF selected", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        } else {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statement_import)
        openPdfPicker()
    }

    private fun openPdfPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        pickPdf.launch(intent)
    }

    private fun importPdf(pdfUri: Uri) {
        // ✅ Keep permission so app can read later too
        try {
            contentResolver.takePersistableUriPermission(
                pdfUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) { }

        lifecycleScope.launch {
            try {
                val txns = BankPdfParser.parse(this@StatementImportActivity, pdfUri)
                StatementFromPdfSaver.applyPdfToSms(this@StatementImportActivity, txns)

                // ✅ NEW: After PDF merge, auto-resolve single-name Unknowns (moves them to Recent)
                autoResolvePendingSinglesAndUpdateCategory()

                Toast.makeText(
                    this@StatementImportActivity,
                    "Statement imported. SMS updated!",
                    Toast.LENGTH_LONG
                ).show()

                setResult(Activity.RESULT_OK)
                finish()

            } catch (e: Exception) {
                Toast.makeText(
                    this@StatementImportActivity,
                    "Import failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()

                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        }
    }

    /**
     * ✅ No UI logic change, only DB fix after PDF import:
     * - Find pending Unknown rows that have merchantAcc
     * - If that merchantAcc has ONLY ONE unique known name => update name + category + clear needsStatementImport
     *
     * Needs DAO:
     *  - getPendingUnknownWithAcc()
     *  - getKnownNamesByMerchantAcc(acc)
     *  - clearNeedsStatementImport(id)
     *  - updateNameCategory(id, name, category)
     */
    private suspend fun autoResolvePendingSinglesAndUpdateCategory() {
        try {
            val dao = AppDatabase.getInstance(this).expenseDao()

            val pending = dao.getPendingUnknownWithAcc()
            if (pending.isEmpty()) return

            for (row in pending) {
                val acc = row.merchantAcc.trim()
                if (acc.isBlank()) continue

                val namesForAcc = dao.getKnownNamesByMerchantAcc(acc)
                    .filter { it.isNotBlank() }

                if (namesForAcc.isEmpty()) continue

                // ✅ Merge like: "Tharun" and "Tharun P" -> "tharun" (you already use this logic in HomeFragment)
                val finalNames = mergeSimilarNames(namesForAcc)

                if (finalNames.size == 1) {
                    val finalName = finalNames.first()

                    // ✅ category from ML (same as SmsReader)
                    val cat = try {
                        CategoryPredictor.predict(this, finalName)
                    } catch (_: Exception) {
                        dao.getLastCategoryForName(finalName) ?: "Others"
                    }

                    dao.updateNameCategory(row.id, finalName, cat)
                    dao.clearNeedsStatementImport(row.id)
                }
            }
        } catch (e: Exception) {
            Log.e("PDF_AUTO_RESOLVE", "autoResolvePendingSingles failed", e)
        }
    }

    /**
     * Same merging idea you wanted:
     * - removes titles
     * - removes single-letter initials (P, K, S)
     * - groups by normalized key
     */
    private fun mergeSimilarNames(names: List<String>): List<String> {
        val titles = setOf("mr", "mrs", "ms", "miss", "dr", "sir", "sri", "shri", "kum", "selvi")

        fun normalizeKey(raw: String): String {
            val parts = raw.lowercase()
                .replace(".", " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .split(" ")
                .filter { it.isNotBlank() }
                .filter { it !in titles }
                .filter { it.length > 1 } // ✅ remove initials like "p"
            return parts.joinToString("").replace(Regex("[^a-z0-9]"), "").trim()
        }

        fun preferDisplayName(a: String, b: String): String {
            val titleRegex = Regex("""\b(mr|mrs|ms|miss|dr|sir|sri|shri)\b""", RegexOption.IGNORE_CASE)
            val aHasTitle = titleRegex.containsMatchIn(a)
            val bHasTitle = titleRegex.containsMatchIn(b)
            return when {
                aHasTitle && !bHasTitle -> a
                bHasTitle && !aHasTitle -> b
                a.length >= b.length -> a
                else -> b
            }
        }

        val grouped = mutableMapOf<String, String>()
        for (n in names) {
            val key = normalizeKey(n)
            if (key.isBlank()) continue
            val ex = grouped[key]
            grouped[key] = if (ex == null) n else preferDisplayName(ex, n)
        }
        return grouped.values.distinct().sorted()
    }
}
