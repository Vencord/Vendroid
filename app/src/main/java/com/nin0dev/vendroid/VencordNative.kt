package com.nin0dev.vendroid

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
        val conn = HttpClient.fetch(sPrefs.getString("vencordLocation", if(sPrefs.getBoolean("equicord", false)) Constants.EQUICORD_BUNDLE_URL else Constants.JS_BUNDLE_URL)!!)
        vendroidFile.writeText(HttpClient.readAsText(conn.inputStream))
        activity.showDiscordToast("Updated Vencord, restart to apply changes!", "SUCCESS")
    }

    @JavascriptInterface
    fun checkVendroidUpdates() {
        activity.checkUpdates(ignoreSetting = true)
    }
}
