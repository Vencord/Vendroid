package com.nin0dev.vendroid

import android.util.Log

object Logger {
    private const val TAG = "Vencord"
    @JvmStatic
    fun e(message: String?) {
        Log.e(TAG, message!!)
    }

    @JvmStatic
    fun e(message: String?, e: Throwable?) {
        Log.e(TAG, message, e)
    }

    @JvmStatic
    fun w(message: String?) {
        Log.w(TAG, message!!)
    }

    @JvmStatic
    fun i(message: String?) {
        Log.i(TAG, message!!)
    }

    @JvmStatic
    fun d(message: String?) {
        Log.d(TAG, message!!)
    }
}
