package com.example.expensereader.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream

object TesseractOcr {

    private const val TAG = "TESS_OCR"

    // Tesseract expects: <datapath>/tessdata/<lang>.traineddata
    private const val TESS_DIR = "tesseract"
    private const val TESSDATA_DIR = "tessdata"

    /**
     * Initialize tessdata by copying traineddata from assets to internal storage.
     * Call once before OCR.
     */
    fun ensureTrainedData(context: Context, langs: List<String>) {
        val baseDir = File(context.filesDir, TESS_DIR)
        val tessDataDir = File(baseDir, TESSDATA_DIR)
        if (!tessDataDir.exists()) tessDataDir.mkdirs()

        langs.forEach { lang ->
            val outFile = File(tessDataDir, "$lang.traineddata")
            if (outFile.exists() && outFile.length() > 0) return@forEach

            val assetPath = "$TESSDATA_DIR/$lang.traineddata"
            try {
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Copied traineddata: $assetPath -> ${outFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Missing traineddata in assets: $assetPath", e)
            }
        }
    }

    /**
     * Offline OCR using Tesseract for Tamil + English.
     * langs example: "tam+eng"
     */
    fun ocrBitmap(context: Context, bitmap: Bitmap, langs: String = "tam+eng"): String {
        // Ensure both files exist:
        ensureTrainedData(context, langs.split("+"))

        val baseDir = File(context.filesDir, TESS_DIR)
        val tess = TessBaseAPI()

        // Init
        val ok = tess.init(baseDir.absolutePath, langs)
        if (!ok) {
            tess.end()
            return ""
        }

        // Improve results with simple preprocessing
        val processed = preprocess(bitmap)

        // Settings for receipts/tickets
        tess.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "") // keep all chars
        tess.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO

        tess.setImage(processed)
        val text = tess.utF8Text ?: ""

        tess.clear()
        tess.end()

        return text.trim()
    }

    /**
     * Simple preprocessing: grayscale + binary threshold.
     * Helps low-contrast ticket prints.
     */
    private fun preprocess(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        // grayscale + threshold
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = src.getPixel(x, y)
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()

                // threshold (tune if needed)
                val v = if (gray < 160) 0 else 255
                out.setPixel(x, y, Color.rgb(v, v, v))
            }
        }
        return out
    }
}
