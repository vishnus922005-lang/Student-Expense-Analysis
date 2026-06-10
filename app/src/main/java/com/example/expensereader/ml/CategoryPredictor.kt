package com.example.expensereader.ml

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.BufferedReader
import java.io.InputStreamReader

object CategoryPredictor {

    private const val TAG = "CategoryPredictor"
    private const val MODEL_ASSET = "expense_char_model.onnx"
    private const val RULES_ASSET = "transactions.csv"
    private const val THRESHOLD = 0.60f

    private data class Rule(val keyword: String, val category: String)

    private var rules: List<Rule> = emptyList()
    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var inputName: String? = null

    fun init(context: Context) {
        if (session != null && env != null && rules.isNotEmpty()) return

        // Load rules CSV
        rules = loadRules(context)

        // Load ONNX
        env = OrtEnvironment.getEnvironment()
        val modelBytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
        session = env!!.createSession(modelBytes)

        // detect model input name
        inputName = session!!.inputNames.firstOrNull()

        Log.d(TAG, "Initialized. rules=${rules.size}, input=$inputName")
    }

    fun predict(context: Context, text: String?): String {
        if (text.isNullOrBlank()) return "Friends & Family"
        init(context)

        val ruleCat = ruleBased(text)
        if (ruleCat != null) return ruleCat

        return mlBased(text)
    }

    // ---------- RULE-BASED ----------
    private fun cleanText(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9]"), "")

    private fun ruleBased(text: String): String? {
        val textLower = text.lowercase()
        val joined = cleanText(text)

        for (r in rules) {
            val kwLower = r.keyword.lowercase()
            if (kwLower.isBlank()) continue

            // normal match
            if (textLower.contains(kwLower)) return r.category

            // no-space match
            if (cleanText(kwLower).isNotBlank() && joined.contains(cleanText(kwLower))) return r.category
        }
        return null
    }

    private fun loadRules(context: Context): List<Rule> {
        val out = mutableListOf<Rule>()
        try {
            context.assets.open(RULES_ASSET).use { input ->
                BufferedReader(InputStreamReader(input)).useLines { lines ->
                    val it = lines.iterator()
                    if (!it.hasNext()) return@useLines
                    val header = it.next() // skip header
                    // expected: keyword,category
                    while (it.hasNext()) {
                        val line = it.next().trim()
                        if (line.isBlank()) continue
                        val parts = splitCsvLine(line)
                        if (parts.size < 2) continue
                        val keyword = parts[0].trim()
                        val category = parts[1].trim()
                        if (keyword.isNotBlank() && category.isNotBlank()) {
                            out.add(Rule(keyword, category))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load rules.csv", e)
        }
        return out
    }

    // very small CSV splitter: handles commas inside quotes
    private fun splitCsvLine(line: String): List<String> {
        val res = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when (ch) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (inQuotes) sb.append(ch) else {
                    res.add(sb.toString())
                    sb.setLength(0)
                }
                else -> sb.append(ch)
            }
        }
        res.add(sb.toString())
        return res
    }

    // ---------- ML-BASED ----------
    private fun mlBased(text: String): String {
        val s = session ?: return "Friends & Family"
        val e = env ?: return "Friends & Family"
        val inName = inputName ?: return "Friends & Family"

        return try {
            // Model expects [None,1] string tensor (same as your conversion)
            val input = arrayOf(arrayOf(text))
            val tensor = OnnxTensor.createTensor(e, input)

            val outputs = s.run(mapOf(inName to tensor))
            tensor.close()

            // We asked zipmap=false, so usually output is:
            // - probabilities: float[][]  (N x numClasses)
            // - sometimes also labels output separately depending on conversion
            val probsAny = outputs[0].value
            val probs = when (probsAny) {
                is Array<*> -> {
                    // expect Array<FloatArray> or Array<DoubleArray>
                    val first = probsAny.firstOrNull()
                    when (first) {
                        is FloatArray -> probsAny as Array<FloatArray>
                        is DoubleArray -> {
                            @Suppress("UNCHECKED_CAST")
                            val arr = probsAny as Array<DoubleArray>
                            Array(arr.size) { i -> FloatArray(arr[i].size) { j -> arr[i][j].toFloat() } }
                        }
                        else -> null
                    }
                }
                else -> null
            }

            // classes (labels)
            val labels: Array<String> = run {
                // try to find String[] output among outputs
                val labelOut = outputs.firstOrNull { it.value is Array<*> && (it.value as Array<*>).firstOrNull() is String }
                val v = labelOut?.value
                if (v is Array<*>) {
                    @Suppress("UNCHECKED_CAST")
                    v as Array<String>
                } else {
                    // fallback: session metadata might include labels; if not, we cannot map
                    emptyArray()
                }
            }

            outputs.close()

            if (probs == null || probs.isEmpty()) return "Friends & Family"

            val row = probs[0]
            var bestIdx = 0
            var bestProb = row[0]
            for (i in row.indices) {
                if (row[i] > bestProb) {
                    bestProb = row[i]
                    bestIdx = i
                }
            }

            if (bestProb < THRESHOLD) return "Friends & Family"

            // If labels output not available, we can’t map index -> category reliably
            // In that case, fallback safely
            if (labels.isEmpty() || bestIdx !in labels.indices) return "Friends & Family"

            labels[bestIdx]

        } catch (ex: Exception) {
            Log.e(TAG, "ML predict failed", ex)
            "Friends & Family"
        }
    }
}
