package com.example.expensereader.ui.analysis

import android.content.Context
import android.widget.TextView
import com.example.expensereader.R
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import java.util.Locale

class Last10DaysMarkerView(
    context: Context,
    private val labels: List<String>  // xIndex -> "dd MMM"
) : MarkerView(context, R.layout.marker_last10) {

    private val tvDate: TextView = findViewById(R.id.tvMarkerDate)
    private val tvAmount: TextView = findViewById(R.id.tvMarkerAmount)

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (e != null) {
            val idx = e.x.toInt()
            val dateLabel = if (idx in labels.indices) labels[idx] else ""
            val amt = e.y

            tvDate.text = dateLabel
            tvAmount.text = String.format(Locale.US, "₹ %.0f", amt)
        }
        super.refreshContent(e, highlight)
    }

    // Center the popup above the point
    override fun getOffset(): MPPointF {
        return MPPointF(-(width / 2f), -height.toFloat() - 12f)
    }
}
