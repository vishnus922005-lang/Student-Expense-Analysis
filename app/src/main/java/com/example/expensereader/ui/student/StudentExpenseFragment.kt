package com.example.expensereader.ui.student

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.expensereader.R
import com.example.expensereader.util.SmartSavingPrefs
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox

class StudentExpenseFragment : Fragment(R.layout.fragment_student_expense) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ Content (clean pattern: headings + bullet points)
        view.findViewById<TextView>(R.id.tvStudentContent).text = """

    This option helps you save a small amount regularly without changing your lifestyle. You don’t need extra income. You don’t need strict rules. Just save a little every day and let the habit grow.
Even ₹10–₹20 per day can make a meaningful difference over time.

Why Daily Saving Matters:
• Students often spend daily on food, travel, snacks, recharge, and small needs.
• Daily saving is not about restriction — it’s about awareness.
• This option works like digital saving or gold saving plans, but focused on daily habits and progress, making saving simple and stress-free.

Benefits of Using This Option:
• Builds a consistent saving habit
• Helps control unnecessary spending
• Improves money awareness without stress
• Encourages discipline and confidence
• Makes saving feel simple and achievable

How Saving Is Tracked:
• Your daily saving is tracked automatically
• The app checks whether your daily saving goal is met
• Clear insights show if saving is done or missed for the day

No pressure — only awareness.

Saving Progress Overview:
• Daily status: Saved or Not Saved
• Weekly progress: How many days you saved
• Monthly progress: Total savings + consistency

Trusted Apps You Can Also Use:
• PhonePe — gold saving & daily savings
• Paytm — digital saving & gold plans
• Jar — daily auto-saving
• Groww — long-term saving options

Positive Side of Saving:
• No stress, no guilt
• Missing a day is okay
• Progress matters more than perfection
Small steps today lead to strong financial habits tomorrow.
Start small. Stay consistent.
Your future self will thank you 
        """.trimIndent()

        // ✅ Checklist + Start button enable logic
        val cb1 = view.findViewById<MaterialCheckBox>(R.id.cbReadIntro)
        val cb2 = view.findViewById<MaterialCheckBox>(R.id.cbUnderstandTracking)
        val btnStart = view.findViewById<MaterialButton>(R.id.btnStartStudent)

        fun updateStartState() {
            val allChecked = cb1.isChecked && cb2.isChecked 
            btnStart.isEnabled = allChecked
            btnStart.alpha = if (allChecked) 1f else 0.5f
        }

        cb1.setOnCheckedChangeListener { _, _ -> updateStartState() }
        cb2.setOnCheckedChangeListener { _, _ -> updateStartState() }
        
        updateStartState()

        // ✅ Button click animation + action
        btnStart.setOnClickListener {
            if (!btnStart.isEnabled) return@setOnClickListener

            // nice press animation (scale down then up)
            btnStart.animate()
                .scaleX(0.96f).scaleY(0.96f)
                .setDuration(90)
                .withEndAction {
                    btnStart.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(120)
                        .start()
                }
                .start()

            // ✅ MARK "STARTED" so Home knows user started saving
            SmartSavingPrefs.setStarted(requireContext(), true)

            // ✅ Go back to Home
            try {
                findNavController().navigateUp()
            } catch (_: Exception) {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
    }
}
