package com.nin0dev.vendroid

import android.app.Activity
import java.io.ByteArrayOutputStream
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
        if (VencordRuntime != null) return
        val res = activity.resources
        res.openRawResource(R.raw.vencord_mobile).use { `is` -> VencordMobileRuntime = readAsText(`is`) }
        val conn = fetch(Constants.JS_BUNDLE_URL)
        conn.inputStream.use { `is` -> VencordRuntime = readAsText(`is`) }
    }

    @Throws(IOException::class)
    private fun fetch(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        if (conn.getResponseCode() >= 300) {
            throw HttpException(conn)
        }
        return conn
    }

    @Throws(IOException::class)
    private fun readAsText(`is`: InputStream): String {
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
