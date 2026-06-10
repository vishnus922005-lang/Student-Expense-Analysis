package com.example.expensereader.ui.savings

import android.content.Context
import android.util.Log

object SavingSchemesCsvReader {

    private const val TAG = "SCHEMES_CSV"

    fun readFromAssets(
        context: Context,
        fileName: String = "saving_tips_schemes.csv"
    ): List<SavingSchemeRow> {

        val rows = mutableListOf<SavingSchemeRow>()
        var skipped = 0

        context.assets.open(fileName).bufferedReader().useLines { lines ->
            val it = lines.iterator()
            if (!it.hasNext()) return@useLines

            // ✅ Skip header
            it.next()

            var lineNo = 1
            while (it.hasNext()) {
                lineNo++
                val raw = it.next()

                // ✅ clean: remove BOM, trim, remove \r
                val line = raw
                    .replace("\uFEFF", "")  // BOM
                    .replace("\r", "")      // windows CR
                    .trim()

                // ✅ skip empty
                if (line.isBlank()) continue

                val cols = splitCsvLine(line)

                // ✅ If there are more than 6 cols (because commas in text), keep only first 6
                if (cols.size < 6) {
                    skipped++
                    Log.w(TAG, "Skip line $lineNo cols=${cols.size} : $line")
                    continue
                }

                val fixed = cols.take(6).map { cleanCell(it) }

                rows.add(
                    SavingSchemeRow(
                        stateOrUt = fixed[0],
                        schemeName = fixed[1],
                        schemeType = fixed[2],
                        benefit = fixed[3],
                        howToApply = fixed[4],
                        officialWebsite = fixed[5]
                    )
                )
            }
        }

        Log.d(TAG, "Loaded rows=${rows.size}, skipped=$skipped")
        Log.d(TAG, "All India count=" + rows.count { it.stateOrUt.equals("All India", true) })

        return rows
    }

    private fun cleanCell(s: String): String {
        return s
            .replace("\uFEFF", "")
            .replace("\r", "")
            .trim()
    }

    // ✅ Handles commas inside quotes
    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val ch = line[i]
            when (ch) {
                '"' -> {
                    // Handle escaped quote ""
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ',' -> {
                    if (inQuotes) sb.append(ch)
                    else {
                        result.add(sb.toString().trim().removeSurrounding("\""))
                        sb.setLength(0)
                    }
                }
                else -> sb.append(ch)
            }
            i++
        }

        result.add(sb.toString().trim().removeSurrounding("\""))
        return result
    }
}
