package com.nin0dev.vendroid

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch


class WelcomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.statusBarColor = Color.TRANSPARENT
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        window.navigationBarColor = Color.TRANSPARENT

        setContentView(R.layout.activity_welcome)

        findViewById<ExtendedFloatingActionButton>(R.id.save_settings).setOnClickListener {
            val sPrefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            val editor = sPrefs.edit()

            editor.putBoolean("checkVendroidUpdates", findViewById<MaterialSwitch>(R.id.check_vendroid_updates).isChecked)
            if (findViewById<RadioButton>(R.id.stable).isChecked) editor.putString("discordBranch", "stable")
            if (findViewById<RadioButton>(R.id.ptb).isChecked) editor.putString("discordBranch", "ptb")
            if (findViewById<RadioButton>(R.id.canary).isChecked) editor.putString("discordBranch", "canary")

            editor.apply()
            finish()
            startActivity(Intent(this@WelcomeActivity, MainActivity::class.java))
        }
    }
}