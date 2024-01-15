package com.playground.app.ui.webview

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class WebViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is webview Fragment"
    }
    val text: LiveData<String> = _text
}