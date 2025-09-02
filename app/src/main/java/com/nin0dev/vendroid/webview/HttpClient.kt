package com.nin0dev.vendroid.webview

import android.app.Activity
import android.content.Context
import android.widget.Toast
import com.nin0dev.vendroid.BuildConfig
import com.nin0dev.vendroid.R
import com.nin0dev.vendroid.utils.Constants
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object HttpClient {
    @JvmField
    var VencordRuntime: String? = null
    @JvmField
    var VencordMobileRuntime: String? = null
    @JvmStatic
    @Throws(IOException::class)
    fun fetchVencord(activity: Activity) {
        val sPrefs = activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val e = sPrefs.edit()
        val bundleURLToUse = if(sPrefs.getString("clientMod", "vencord") == "equicord") Constants.EQUICORD_BUNDLE_URL else Constants.JS_BUNDLE_URL
        val vendroidFile = File(activity.filesDir, "vencord.js")
        val res = activity.resources
        res.openRawResource(R.raw.vencord_mobile).use { `is` -> VencordMobileRuntime = readAsText(`is`) }
        if (VencordRuntime != null) return
        if (sPrefs.getInt("lastMajorUpdateThatUserHasUpdatedVencord", 0) < BuildConfig.VERSION_CODE) {
            if(BuildConfig.DEBUG) Toast.makeText(activity, "Just updated app version, redownloading Vencord", Toast.LENGTH_LONG).show()
            vendroidFile.delete()
        }
        if ((sPrefs.getString("vencordLocation", Constants.JS_BUNDLE_URL) != Constants.JS_BUNDLE_URL && sPrefs.getString("vencordLocation", Constants.JS_BUNDLE_URL) != Constants.EQUICORD_BUNDLE_URL) || BuildConfig.DEBUG) { // user is debugging vencord or app, always redownload
            Toast.makeText(activity, "Debugging app or Vencord, bundle will be redownloaded. Avoid using on limited networks", Toast.LENGTH_LONG).show()
            vendroidFile.delete()
        }
        if (vendroidFile.exists()) {
            VencordRuntime = vendroidFile.readText()
        }
        else {
            val conn = fetch(sPrefs.getString("vencordLocation", bundleURLToUse)!!)
            vendroidFile.writeText(readAsText(conn.inputStream))
            e.putInt("lastMajorUpdateThatUserHasUpdatedVencord", BuildConfig.VERSION_CODE)
            e.apply()
            VencordRuntime = vendroidFile.readText()
        }
    }

    @Throws(IOException::class)
    fun fetch(url: String?): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        if (conn.getResponseCode() >= 300) {
            throw HttpException(conn)
        }
        return conn
    }

    @Throws(IOException::class)
    fun readAsText(`is`: InputStream): String {
        ByteArrayOutputStream().use { baos ->
            var n: Int
            val buf = ByteArray(16384) // 16 KB
            while (`is`.read(buf).also { n = it } > -1) {
                baos.write(buf, 0, n)
            }
            baos.flush()
            return baos.toString("UTF-8")
        }
    }

    class HttpException(private val conn: HttpURLConnection) : IOException() {
        override var message: String? = null
            get() {
                if (field == null) {
                    try {
                        conn.errorStream.use { es ->
                            field = String.format(
                                    Locale.ENGLISH,
                                    "%d: %s (%s)\n%s",
                                    conn.getResponseCode(),
                                    conn.getResponseMessage(),
                                    conn.url.toString(),
                                    readAsText(es)
                            )
                        }
                    } catch (ex: IOException) {
                        field = "Error while building message lmao. Url is " + conn.url.toString()
                    }
                }
                return field
            }
            private set
    }
}
