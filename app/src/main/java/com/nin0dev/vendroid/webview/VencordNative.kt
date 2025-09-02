package com.nin0dev.vendroid.webview

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.view.View.GONE
import android.view.View.VISIBLE
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.LinearLayout
import com.google.android.material.textfield.TextInputEditText
import com.nin0dev.vendroid.MainActivity
import com.nin0dev.vendroid.R
import com.nin0dev.vendroid.utils.Constants
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
    fun updateVencord() {
        val sPrefs = activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val vendroidFile = File(activity.filesDir, "vencord.js")
        val conn = HttpClient.fetch(
            sPrefs.getString(
                "vencordLocation",
                if (
                    sPrefs.getString("clientMod", "vencord") == "equicord"
                ) Constants.EQUICORD_BUNDLE_URL else Constants.JS_BUNDLE_URL
            )
        )
        vendroidFile.writeText(HttpClient.readAsText(conn.inputStream))
        activity.showDiscordToast("Updated Vencord, restart to apply changes!", "SUCCESS")
    }

    @JavascriptInterface
    fun updateVendroid() {
        activity.checkUpdates(ignoreSetting = true)
    }

    @JavascriptInterface
    fun getString(id: String, defaultValue: String): String {
        val sPrefs = activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
        return try {
            sPrefs.getString(id, defaultValue)!!;
        } catch (e: Exception) {
            "None";
        }
    }

    @JavascriptInterface
    fun getBool(id: String, defaultValue: Boolean): Boolean {
        val sPrefs = activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
        return try {
            sPrefs.getBoolean(id, defaultValue);
        } catch (e: Exception) {
            false;
        }
    }

    @JavascriptInterface
    fun setString(id: String, value: String) {
        val sPrefs = activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val e = sPrefs.edit()
        if(id == "clientMod") e.putInt("lastMajorUpdateThatUserHasUpdatedVencord", 0)
        e.putString(id, value)
        e.apply()
    }

    @JavascriptInterface
    fun setBool(id: String, value: Boolean) {
        val sPrefs = activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val e = sPrefs.edit()
        e.putBoolean(id, value)
        e.apply()
    }

    @JavascriptInterface
    fun changeAppIcon(id: String) {
        val icons = arrayOf("Main", "Jolly", "Discord", "Retro", "TS12")
        for (icon in icons) {
            activity.packageManager.setComponentEnabledSetting(
                ComponentName(activity.applicationContext,
                "com.nin0dev.vendroid.${icon}MainActivity"
            ), if (icon == id) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }

    @JavascriptInterface
    fun openQuickCss(quickCss: String) {
        activity.runOnUiThread {
            activity.findViewById<LinearLayout>(R.id.quickcss).visibility = VISIBLE
            activity.findViewById<LinearLayout>(R.id.loading_screen).visibility = GONE
            activity.findViewById<WebView>(R.id.webview).visibility = GONE
            activity.findViewById<TextInputEditText>(R.id.css).setText(quickCss)
        }
    }


}
