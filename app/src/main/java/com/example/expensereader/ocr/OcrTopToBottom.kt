package com.example.expensereader.ocr

import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.text.Text
import kotlin.math.abs

object OcrTopToBottom {

    private const val TAG = "OCR_SORT"

    data class Row(
        val text: String,
        val box: Rect
    )

    fun toTopBottomText(visionText: Text): String {

        val rows = mutableListOf<Row>()

        // 1️⃣ Collect EACH OCR LINE with position
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val box = line.boundingBox ?: continue
                val text = line.text.trim()
                if (text.isNotEmpty()) {
                    rows.add(Row(text, box))
                }
            }
        }

        // 2️⃣ Sort TOP → BOTTOM, then LEFT → RIGHT
        val rowTolerance = 18 // pixels (adjust if needed)

        rows.sortWith { a, b ->
            val dy = a.box.top - b.box.top
            if (abs(dy) <= rowTolerance) {
                a.box.left - b.box.left
            } else {
                dy
            }
        }

        // 3️⃣ LOG ROW-BY-ROW (THIS IS WHAT YOU WANTED)
        Log.d(TAG, "======= OCR TOP → BOTTOM ROW ORDER =======")
        rows.forEachIndexed { index, row ->
            Log.d(
                TAG,
                String.format(
                    "ROW %02d | y=%4d x=%4d | %s",
                    index + 1,
                    row.box.top,
                    row.box.left,
                    row.text
                )
            )
        }
        Log.d(TAG, "======= OCR ROW LOG END =======")

        // 4️⃣ Build ordered text
        return rows.joinToString("\n") { it.text }
    }
}
