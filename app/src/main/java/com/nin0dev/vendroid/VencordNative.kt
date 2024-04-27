package com.nin0dev.vendroid

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import java.io.File

class VencordNative(private val activity: MainActivity, private val wv: WebView) {
    @JavascriptInterface
    fun goBack() {
        activity.runOnUiThread {
            if (wv.canGoBack()) wv.goBack() else  // no idea what i was smoking when I wrote this
                activity.getActionBar()
        }
    }

    @JavascriptInterface
    fun openSettings() {
        activity.startActivity(Intent(activity, SettingsActivity::class.java))
    }

    @JavascriptInterface
    fun updateVencord() {
        val sPrefs = activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
        var vendroidFile = File(activity.filesDir, "vencord.js")
        val conn = HttpClient.fetch(sPrefs.getString("vencordLocation", Constants.JS_BUNDLE_URL)!!)
        vendroidFile.writeText(HttpClient.readAsText(conn.inputStream))
        activity.showDiscordToast("Updated Vencord, restart to apply changes!", "SUCCESS")
    }
}
