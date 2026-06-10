package com.example.expensereader.ui.home

import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.expensereader.R
import com.example.expensereader.ml.InsightModel
import com.example.expensereader.ml.InsightSeverity

class InsightCardBinder(private val root: View) {

    private val tvTitle = root.findViewById<TextView>(R.id.tvInsightTitle)
    private val tvMessage = root.findViewById<TextView>(R.id.tvInsightMessage)

    fun bind(model: InsightModel) {

        tvTitle.text = model.title
        tvMessage.text = model.message

        val color = when (model.severity) {
            InsightSeverity.GOOD ->
                ContextCompat.getColor(root.context, android.R.color.holo_green_dark)

            InsightSeverity.WARNING ->
                ContextCompat.getColor(root.context, android.R.color.holo_orange_dark)

            InsightSeverity.CRITICAL ->
                ContextCompat.getColor(root.context, android.R.color.holo_red_dark)
        }

        tvTitle.setTextColor(color)
    }
}
