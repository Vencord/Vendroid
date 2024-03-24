package com.nin0dev.vendroid

import android.app.Activity
import android.webkit.JavascriptInterface
import android.webkit.WebView

class VencordNative(private val activity: Activity, private val wv: WebView) {
    @JavascriptInterface
    fun goBack() {
        activity.runOnUiThread {
            if (wv.canGoBack()) wv.goBack() else  // no idea what i was smoking when I wrote this
                activity.getActionBar()
        }
    }
}
