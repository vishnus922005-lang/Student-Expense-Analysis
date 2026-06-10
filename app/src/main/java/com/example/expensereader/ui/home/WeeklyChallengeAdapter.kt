// File: app/src/main/java/com/example/expensereader/ui/home/WeeklyChallengeAdapter.kt
package com.example.expensereader.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.expensereader.R
import com.example.expensereader.model.WeeklyChallenge
import com.google.android.material.button.MaterialButton

class WeeklyChallengeAdapter(
    private val onAccept: (WeeklyChallenge) -> Unit,
    private val onSkip: (WeeklyChallenge) -> Unit
) : RecyclerView.Adapter<WeeklyChallengeAdapter.VH>() {

    private val items = mutableListOf<WeeklyChallenge>()

    fun submit(list: List<WeeklyChallenge>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_weekly_challenge, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) = h.bind(items[position])

    override fun getItemCount(): Int = items.size

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val tvTitle = v.findViewById<TextView>(R.id.tvTitle)
        private val tvDesc = v.findViewById<TextView>(R.id.tvDesc)
        private val tvMeta = v.findViewById<TextView>(R.id.tvMeta)
        private val btnAccept = v.findViewById<MaterialButton>(R.id.btnAccept)
        private val btnSkip = v.findViewById<MaterialButton>(R.id.btnSkip)

        fun bind(c: WeeklyChallenge) {
            tvTitle.text = "${c.emoji} ${c.title}"
            tvDesc.text = c.description

            // simple default display
            tvMeta.text = "Reward: +${c.rewardPoints} Points  •  Time Left: ${c.durationDays - 1} Days"

            btnAccept.setOnClickListener { onAccept(c) }
            btnSkip.setOnClickListener { onSkip(c) }
        }
    }
}
