package com.nin0dev.vendroid

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.CookieSyncManager
import android.webkit.WebView
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar

class RecoveryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.statusBarColor = Color.TRANSPARENT
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        window.navigationBarColor = Color.TRANSPARENT

        setContentView(R.layout.activity_recovery)

        findViewById<MaterialCardView>(R.id.start_normally).setOnClickListener {
            finish()
            startActivity(Intent(this, MainActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.safe_mode).setOnClickListener {
            val sPrefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            val e = sPrefs.edit()
            e.putBoolean("safeMode", true)
            e.apply()
            finish()
            startActivity(Intent(this, MainActivity::class.java))
        }

    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
    }
}