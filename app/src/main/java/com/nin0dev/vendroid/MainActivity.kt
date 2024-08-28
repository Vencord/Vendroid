package com.nin0dev.vendroid

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.nin0dev.vendroid.HttpClient.fetchVencord
import com.nin0dev.vendroid.Logger.e
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import pl.droidsonroids.gif.GifImageView
import java.io.IOException


@Serializable
data class UpdateData(val version: Int, val changelog: String)

class MainActivity : Activity() {
    private var wvInitialized = false
    private var wv: WebView? = null
    @JvmField
    var filePathCallback: ValueCallback<Array<Uri?>?>? = null

    @OptIn(ExperimentalSerializationApi::class)
    fun checkUpdates(ignoreSetting: Boolean = false)
    {
        val sPrefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        if(sPrefs.getBoolean("checkVendroidUpdates", false) || ignoreSetting) {
            val queue = Volley.newRequestQueue(this)
            val url = "https://vendroid.nin0.dev/api/updates"
            val stringRequest = StringRequest(
                Request.Method.GET, url,
                { response ->
                    val gson = Gson()
                    val updateData = gson.fromJson<UpdateData>(response, UpdateData::class.java)
                    if(updateData.version != BuildConfig.VERSION_CODE)
                    {
                        val madb = MaterialAlertDialogBuilder(this)
                        madb.setTitle(getString(R.string.vendroid_update_available))
                        madb.setMessage("Changelog:\n" + updateData.changelog)
                        madb.setPositiveButton(getString(R.string.update), DialogInterface.OnClickListener { dialogInterface, i ->
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/VendroidEnhanced/Vendroid/releases/latest/download/app-release.apk"))
                            startActivity(browserIntent)
                        })
                        madb.setNegativeButton(getString(R.string.later), DialogInterface.OnClickListener { _, _ ->  })
                        madb.show()
                    }
                    if(ignoreSetting && updateData.version == BuildConfig.VERSION_CODE) {
                        showDiscordToast("No updates available", "MESSAGE")
                    }
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

        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.statusBarColor = Color.TRANSPARENT
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        window.navigationBarColor = Color.TRANSPARENT
        val sPrefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val editor = sPrefs.edit()
        // https://developer.chrome.com/docs/devtools/remote-debugging/webviews/
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        setContentView(R.layout.activity_main)
        if (sPrefs.getString("splash", "viggy") == "viggy") findViewById<GifImageView>(R.id.viggy_gif).visibility = VISIBLE
        else if (sPrefs.getString("splash", "viggy") == "shiggy") findViewById<GifImageView>(R.id.shiggy_gif).visibility = VISIBLE
        else if (sPrefs.getString("splash", "viggy") == "oneko") findViewById<GifImageView>(R.id.oneko_gif).visibility = VISIBLE
        wv = findViewById(R.id.webview)!!
        explodeAndroid()
        wv!!.setWebViewClient(VWebviewClient())
        wv!!.setWebChromeClient(VChromeClient(this))
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
            if (sPrefs.getString("discordBranch", "") == "stable") wv!!.loadUrl("https://discord.com/app")
            else if (sPrefs.getString("discordBranch", "") == "ptb") wv!!.loadUrl("https://ptb.discord.com/app")
            else if (sPrefs.getString("discordBranch", "") == "canary") wv!!.loadUrl("https://canary.discord.com/app")
            else {
                finish()
                startActivity(Intent(this@MainActivity, WelcomeActivity::class.java))
            }
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent) {
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
