package com.example.expensereader.sms

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SmsInboxObserver(
    private val context: Context,
    private val owner: LifecycleOwner,
    handler: Handler = Handler(Looper.getMainLooper())
) : ContentObserver(handler) {

    private var job: Job? = null

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)

        // Debounce: some devices trigger multiple times for one SMS
        job?.cancel()
        job = owner.lifecycleScope.launch {
            delay(600)
            SmsReader.importNew(context)
        }
    }

    fun register() {
        context.contentResolver.registerContentObserver(
            Telephony.Sms.Inbox.CONTENT_URI,
            true,
            this
        )
    }

    fun unregister() {
        context.contentResolver.unregisterContentObserver(this)
    }
}
