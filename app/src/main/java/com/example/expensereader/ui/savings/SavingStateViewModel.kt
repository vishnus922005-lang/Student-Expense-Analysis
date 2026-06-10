package com.example.expensereader.ui.savings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SavingStateViewModel : ViewModel() {

    private val _started = MutableLiveData(false)
    val started: LiveData<Boolean> = _started

    fun setStarted(value: Boolean) {
        _started.value = value
    }
}
