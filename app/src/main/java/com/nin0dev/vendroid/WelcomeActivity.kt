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

        val devbuildCheckbox = findViewById<CheckBox>(R.id.allow_custom_location)
        devbuildCheckbox.setOnClickListener {
            if (devbuildCheckbox.isChecked) {
                MaterialAlertDialogBuilder(this)
                        .setTitle("Warning")
                        .setMessage("If you set a custom location, you will be loading and injecting Vencord from a different location. This feature is meant for developers ONLY. Never edit this setting if someone else asked you to, or if you don't know what you're doing! If you do set a custom location, you will not be able to ask for support in the Vencord support channel or in this project's issues. Are you sure you want to continue?")
                        .setNegativeButton(resources.getString(R.string.no)) { _, _ ->
                            devbuildCheckbox.isChecked = false
                        }
                        .setPositiveButton(resources.getString(R.string.yes)) { _, _ ->
                            findViewById<EditText>(R.id.custom_location).visibility = VISIBLE
                        }
                        .show()
            }
            else {
                findViewById<EditText>(R.id.custom_location).visibility = GONE
            }
        }

        findViewById<ExtendedFloatingActionButton>(R.id.save_settings).setOnClickListener {
            val sPrefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            val editor = sPrefs.edit()

            editor.putBoolean("checkVendroidUpdates", findViewById<MaterialSwitch>(R.id.check_vendroid_updates).isChecked)
            if (findViewById<RadioButton>(R.id.stable).isChecked) editor.putString("discordBranch", "stable")
            if (findViewById<RadioButton>(R.id.ptb).isChecked) editor.putString("discordBranch", "ptb")
            if (findViewById<RadioButton>(R.id.canary).isChecked) editor.putString("discordBranch", "canary")
            if (findViewById<CheckBox>(R.id.allow_custom_location).isChecked && findViewById<EditText>(R.id.custom_location).text.isNotBlank()) editor.putString("vencordLocation", findViewById<EditText>(R.id.custom_location).text.toString())

            editor.apply()
            finish()
            startActivity(Intent(this@WelcomeActivity, MainActivity::class.java))
        }
    }
}