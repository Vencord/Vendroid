package com.nin0dev.vendroid.webview

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.annotation.RequiresApi
import java.io.IOException
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.URL

class VWebviewClient : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url
        if ("discord.com" == url.authority || "about:blank" == url.toString()) {
            return false
        }
        val intent = Intent(Intent.ACTION_VIEW, url)
        view.context.startActivity(intent)
        return true
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        try {
            HttpClient.VencordRuntime?.let { view.evaluateJavascript(it, null) }
            HttpClient.VencordMobileRuntime?.let { view.evaluateJavascript(it, null) }
        }
        catch (e: Exception) {
            Toast.makeText(view.context, "Couldn't load Vencord, try restarting the app.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onPageFinished(view: WebView, url: String) {
        view.visibility = View.VISIBLE
        super.onPageFinished(view, url)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? {
        val uri = req.url
        if (req.isForMainFrame || req.url.path!!.endsWith(".css")) {
            try {
                return doFetch(req)
            } catch (ex: IOException) {
                //e("Error during shouldInterceptRequest", ex)
            }
        }
        return null
    }

        @RequiresApi(Build.VERSION_CODES.N)
        private fun doFetch(req: WebResourceRequest): WebResourceResponse {
            val url = req.url.toString()
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.setRequestMethod(req.method)
            for ((key, value) in req.requestHeaders) {
                conn.setRequestProperty(key, value)
            }
            val code = conn.getResponseCode()
            val msg = conn.getResponseMessage()
            val headers = conn.headerFields
            val modifiedHeaders = HashMap<String, String>(headers.size)
            for ((key, valueList) in headers) {
                if (key == null) {
                    continue
                }
                if (!"Content-Security-Policy".equals(key, ignoreCase = true)) {
                    if (valueList != null && valueList.isNotEmpty()) {
                        val value = valueList[0]
                        modifiedHeaders[key] = value
                    }
                }
            }
            if (url.endsWith(".css")) modifiedHeaders["Content-Type"] = "text/css"
            val wong = modifiedHeaders.getOrDefault("Content-Type", "application/octet-stream")
            return WebResourceResponse(wong, "utf-8", code, msg, modifiedHeaders, conn.inputStream)
        }
}
