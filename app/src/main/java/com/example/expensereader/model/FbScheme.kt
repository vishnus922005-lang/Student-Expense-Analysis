package com.example.expensereader.model

data class FbScheme(
    val title: String = "",
    val provider: String = "",
    val state: List<String> = emptyList(),     // ✅ was String, now Array/List
    val category: String = "Others",
    val studentOnly: Boolean = true,
    val nonCaste: Boolean = true,
    val status: String = "active",
    val savingEstimate: SavingEstimate = SavingEstimate(),
    val apply: ApplyInfo = ApplyInfo()
) {
    data class SavingEstimate(
        val minMonthly: Long = 0,
        val maxMonthly: Long = 0
    )

    data class ApplyInfo(
        val applyUrl: String = "",
        val steps: List<String> = emptyList()
    )
}
