// File: app/src/main/java/com/example/expensereader/ml/CategoryResolver.kt
package com.example.expensereader.ml

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

object CategoryResolver {

    private const val TAG = "CategoryResolver"

    private val rules: MutableList<Pair<String, String>> = mutableListOf()
    @Volatile private var loaded = false

    private fun cleanText(text: String): String {
        return text.lowercase(Locale.ENGLISH)
            .replace(Regex("[^a-z0-9]"), "")
            .trim()
    }

    private fun tokenize(text: String): List<String> {
        return text.lowercase(Locale.ENGLISH)
            .replace(Regex("[^a-z0-9 ]"), " ")
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun containsTokenSequence(textTokens: List<String>, keywordTokens: List<String>): Boolean {
        if (keywordTokens.isEmpty()) return false
        var j = 0
        for (t in textTokens) {
            if (t == keywordTokens[j]) {
                j++
                if (j == keywordTokens.size) return true
            }
        }
        return false
    }

    // ✅ detect "Unknown" rows
    private fun isUnknownName(name: String): Boolean {
        val n = name.trim()
        return n.equals("unknown", true) || n.startsWith("unknown-", true)
    }

    /**
     * ✅ Person-name detector:
     * - has 1 to 4 tokens
     * - mostly letters (not like SHOP123 / UPI / REF)
     * - not containing common business words (hotel, bakery, supermarket, etc.)
     */
    private fun looksLikePersonName(raw: String): Boolean {
        val text = raw.trim()
        if (text.isBlank()) return false
        if (isUnknownName(text)) return false

        val tokens = tokenize(text)
        if (tokens.isEmpty()) return false
        if (tokens.size > 4) return false

        // if many digits -> probably not person
        val digitsCount = raw.count { it.isDigit() }
        if (digitsCount >= 3) return false

        // common business words that should NOT be treated as a person
        val businessWords = setOf(
            "hotel", "bakery", "bakkery", "restaurant", "restaur",
            "supermarket", "market", "mart", "store", "shop",
            "college", "school", "university", "institute", "engineering",
            "travels", "transport", "medical", "pharmacy", "labs", "hospital",
            "juice", "sweets", "snacks", "canteen", "cafe", "coffee"
        )

        if (tokens.any { it in businessWords }) return false

        // must be mostly letters
        val letters = raw.count { it.isLetter() }
        if (letters < 3) return false

        return true
    }

    fun ensureLoaded(context: Context) {
        if (loaded) return

        synchronized(this) {
            if (loaded) return
            rules.clear()

            try {
                context.assets.open("transactions.csv").use { input ->
                    BufferedReader(InputStreamReader(input)).use { br ->
                        var line: String?
                        var first = true
                        while (true) {
                            line = br.readLine() ?: break
                            if (first) { first = false; continue }

                            val parts = line.split(",", limit = 2)
                            if (parts.size < 2) continue

                            val keyword = parts[0].trim()
                            val category = parts[1].trim()

                            if (keyword.isNotBlank() && category.isNotBlank()) {
                                rules.add(keyword.lowercase(Locale.ENGLISH) to category)
                            }
                        }
                    }
                }

                loaded = true
                Log.d(TAG, "Loaded ${rules.size} keyword rules from assets/transactions.csv")
            } catch (e: Exception) {
                loaded = true
                Log.e(TAG, "Failed to load transactions.csv: ${e.message}", e)
            }
        }
    }

    private data class Match(
        val category: String,
        val keyword: String,
        val mode: String,     // "seq" | "normal" | "clean" | "token"
        val score: Int
    )

    fun resolve(context: Context, merchantName: String): String {
        ensureLoaded(context)

        val raw = merchantName.trim()
        if (raw.isBlank()) return "Others"

        // ✅ Unknown must be Others
        if (isUnknownName(raw)) {
            Log.d(TAG, "DEFAULT(unknown) -> Others for '$merchantName'")
            return "Others"
        }

        val textLower = raw.lowercase(Locale.ENGLISH)
        val textJoined = cleanText(raw)
        val textTokens = tokenize(raw)
        val tokenSet = textTokens.toSet()

        var best: Match? = null

        for ((keyword, category) in rules) {
            if (keyword.isBlank()) continue

            val kwTokens = tokenize(keyword)
            val kwJoined = cleanText(keyword)
            val kwLen = keyword.length
            val tokenCount = kwTokens.size

            // ✅ short keyword protection
            val isShort = kwJoined.length <= 2

            // 1) seq match
            if (kwTokens.isNotEmpty() && containsTokenSequence(textTokens, kwTokens)) {
                val score = 300000 + (tokenCount * 1000) + kwLen
                best = pickBetter(best, Match(category, keyword, "seq", score))
                continue
            }

            // 2) normal contains (blocked for short)
            if (!isShort && textLower.contains(keyword)) {
                val score = 200000 + (tokenCount * 1000) + kwLen
                best = pickBetter(best, Match(category, keyword, "normal", score))
                continue
            }

            // short keyword: token match only
            if (isShort && kwTokens.size == 1 && tokenSet.contains(kwTokens[0])) {
                val score = 200000 + (tokenCount * 1000) + kwLen
                best = pickBetter(best, Match(category, keyword, "token", score))
                continue
            }

            // 3) clean contains (blocked for short)
            if (!isShort && kwJoined.isNotBlank() && textJoined.contains(kwJoined)) {
                val score = 100000 + (tokenCount * 1000) + kwLen
                best = pickBetter(best, Match(category, keyword, "clean", score))
                continue
            }
        }

        // ✅ if rules matched, return best
        if (best != null) {
            Log.d(
                TAG,
                "BEST_MATCH(${best!!.mode}) keyword='${best!!.keyword}' -> '${best!!.category}' for '$merchantName'"
            )
            return best!!.category
        }

        // ✅ DEFAULT: Person names -> Friends & Family
        if (looksLikePersonName(raw)) {
            Log.d(TAG, "DEFAULT(person) -> Friends & Family for '$merchantName'")
            return "Friends & Family"
        }

        // ✅ DEFAULT fallback
        Log.d(TAG, "DEFAULT -> Others for '$merchantName'")
        return "Others"
    }

    private fun pickBetter(current: Match?, candidate: Match): Match {
        if (current == null) return candidate
        if (candidate.score > current.score) return candidate
        return current
    }
}
