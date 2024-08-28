package com.nin0dev.vendroid

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.snackbar.Snackbar

class RecoveryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.statusBarColor = Color.TRANSPARENT
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        window.navigationBarColor = Color.TRANSPARENT

        setContentView(R.layout.activity_recovery)

        findViewById<Button>(R.id.reset_vencord_location).setOnClickListener {
            val sPrefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            val e = sPrefs.edit()
            e.putString("vencordLocation", Constants.JS_BUNDLE_URL)
            e.putBoolean("pendingReset", true)
            e.apply()
            Snackbar.make(findViewById(R.id.main_layout), "Successfully reset Vencord location", Snackbar.LENGTH_LONG).show()
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
    }
}