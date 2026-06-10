package com.example.expensereader.ui.savings

data class SavingSchemeRow(
    val stateOrUt: String,
    val schemeName: String,
    val schemeType: String,
    val benefit: String,
    val howToApply: String,
    val officialWebsite: String
)
