package com.nin0dev.vendroid

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.view.KeyEvent
import android.view.View.VISIBLE
import android.view.WindowManager
import android.webkit.ValueCallback
import android.webkit.WebView
import android.widget.Toast
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.color.DynamicColors
import com.google.gson.Gson
import com.nin0dev.vendroid.webview.HttpClient.fetchVencord
import com.nin0dev.vendroid.utils.Logger.e
import com.nin0dev.vendroid.webview.VChromeClient
import com.nin0dev.vendroid.webview.VWebviewClient
import pl.droidsonroids.gif.GifImageView
import java.io.IOException


data class UpdateData(val id: Int?)

class MainActivity : Activity() {
    private var wvInitialized = false
    private var wv: WebView? = null
    @JvmField
    var filePathCallback: ValueCallback<Array<Uri?>?>? = null

    fun migrateSettings() {
        val sPrefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        if(sPrefs.getBoolean("migratedSettings", false)) return;
        val e = sPrefs.edit()
        e.putBoolean("migratedSettings", true);

        e.putBoolean("checkVDEUpdates", sPrefs.getBoolean("checkVendroidUpdates", true))
        e.putBoolean("checkAnnouncements", sPrefs.getBoolean("checkVendroidUpdates", true)) // The user likely turned off auto-updating for privacy reasons, so also disable phoning home for announcements.
        e.putString("clientMod", if(sPrefs.getBoolean("equicord", false)) "equicord" else "vencord")
        e.putString("splashScreen", sPrefs.getString("splash", "viggy"));

        e.apply()
    }

    fun checkUpdates(ignoreSetting: Boolean = false)
    {
        val sPrefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        if(sPrefs.getBoolean("checkVDEUpdates", true) || sPrefs.getBoolean("checkAnnouncements", true) || ignoreSetting) {
            val queue = Volley.newRequestQueue(this)
            val url = "https://vendroid-staging.nin0.dev/api/updates?version=${BuildConfig.VERSION_CODE}"
            val stringRequest = StringRequest(
                Request.Method.GET, url,
                { response ->
                    val gson = Gson()
                    val updateData = gson.fromJson<UpdateData>(response, UpdateData::class.java)

                },
                { error ->
                    if (BuildConfig.DEBUG)  {
                        e("Network error during update check", error)
                    }
                    Toast.makeText(this, "Failed to check for updates", Toast.LENGTH_SHORT).show()
                }
            )
            stringRequest.setShouldCache(false);
            queue.add(stringRequest)
        }
    }

    @SuppressLint("SetJavaScriptEnabled") // mad? watch this swag
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        migrateSettings()
        DynamicColors.applyToActivitiesIfAvailable(application)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.statusBarColor = Color.TRANSPARENT
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        window.navigationBarColor = Color.TRANSPARENT

        val sPrefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val editor = sPrefs.edit()

        // https://developer.chrome.com/docs/devtools/remote-debugging/webviews/
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        setContentView(R.layout.activity_main)

        findViewById<GifImageView>(mapOf("viggy" to R.id.viggy_gif, "shiggy" to R.id.shiggy_gif, "oneko" to R.id.oneko_gif)[sPrefs.getString("splashScreen", "viggy")]!!).visibility = VISIBLE

        wv = findViewById(R.id.webview)!!
        explodeAndroid()
        wv!!.setWebViewClient(VWebviewClient())
        wv!!.setWebChromeClient(VChromeClient(this))
        if(sPrefs.getBoolean("desktopMode", false)) {
            wv!!.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        }
        val s = wv?.getSettings()!!
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.allowFileAccess = true

        if(!sPrefs.getBoolean("safeMode", false)) {
            wv?.addJavascriptInterface(VencordNative(this, wv!!), "VencordMobileNative")
            try {
                fetchVencord(this)
            } catch (ex: IOException) {

            }
        }
        else {
            Toast.makeText(this, "Safe mode enabled, Vencord won't be loaded", Toast.LENGTH_SHORT).show()
            editor.putBoolean("safeMode", false)
            editor.apply()
        }

        val intent = intent
        if (intent.action == Intent.ACTION_VIEW) {
            val data = intent.data
            if (data != null) handleUrl(intent.data)
        } else {
            wv!!.loadUrl(
                mapOf(
                    "stable" to "https://discord.com/app",
                    "ptb" to "https://discord.com/app",
                    "oneko" to "https://discord.com/app"
                )[sPrefs.getString("discordBranch", "stable")]!!
            )
        }

        checkUpdates()
        wvInitialized = true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && wv != null) {
            runOnUiThread { wv!!.evaluateJavascript("VencordMobile.onBackPress()") { r: String -> if ("false" == r) onBackPressed() } }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        try {
            if(resultCode != RESULT_CANCELED) {
                if (requestCode != FILECHOOSER_RESULTCODE || filePathCallback == null) return
                if (resultCode != RESULT_OK || intent == null) {
                    filePathCallback!!.onReceiveValue(null)
                } else {
                    var uris: Array<Uri?>?
                    try {
                        val clipData = intent.clipData
                        if (clipData != null) { // multiple items
                            uris = arrayOfNulls(clipData.itemCount)
                            for (i in 0 until clipData.itemCount) {
                                uris[i] = clipData.getItemAt(i).uri
                            }
                        } else { // single item
                            uris = arrayOf(intent.data)
                        }
                    } catch (ex: Exception) {
                        e("Error during file upload", ex)
                        uris = null
                    }
                    filePathCallback!!.onReceiveValue(uris)
                }
                filePathCallback = null
            }
        }
        catch (ex: Exception) {
            // it is well known that the best fix for the crash is to wrap the entire function in a try/catch block
        }
    }

    private fun explodeAndroid() {
        StrictMode.setThreadPolicy(
            ThreadPolicy.Builder() // trolley
                .permitNetwork()
                .build()
        )
    }

    fun handleUrl(url: Uri?) {
        if (url != null) {
            if (url.authority != "discord.com" || url.authority != "ptb.discord.com" || url.authority != "canary.discord.com") return
            if (!wvInitialized) {
                wv!!.loadUrl(url.toString())
            } else {
                wv!!.evaluateJavascript("Vencord.Webpack.Common.NavigationRouter.transitionTo(\"" + url.path + "\")", null)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val data = intent.data
        data?.let { handleUrl(it) }
    }

    fun showDiscordToast(message: String, type: String) {
        wv?.post(Runnable {
            wv?.evaluateJavascript("toasts=Vencord.Webpack.Common.Toasts; toasts.show({id: toasts.genId(), message: \"$message\", type: toasts.Type.$type, options: {position: toasts.Position.BOTTOM,}})", null) // NOBODY LIKES TOASTS AT THE TOP
        })
    }

    companion object {
        const val FILECHOOSER_RESULTCODE = 8485
    }
}
