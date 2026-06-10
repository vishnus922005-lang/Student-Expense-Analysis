package com.example.expensereader.util

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

object BrowserUtils {
    fun openUrl(context: Context, url: String) {
        if (url.isBlank()) return
        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
    }
}
