package com.nin0dev.vendroid

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.opengl.Visibility
import android.os.Bundle
import android.service.voice.VoiceInteractionSession.VisibleActivityCallback
import android.view.View
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {
    @SuppressLint("CutPasteId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sPrefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.statusBarColor = Color.TRANSPARENT
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        window.navigationBarColor = Color.TRANSPARENT

        setContentView(R.layout.activity_settings)

        findViewById<MaterialSwitch>(R.id.check_vendroid_updates).isChecked = sPrefs.getBoolean("checkVendroidUpdates", false)
        findViewById<MaterialSwitch>(R.id.desktop_mode).isChecked = sPrefs.getBoolean("desktopMode", false)
        when (sPrefs.getString("discordBranch", "stable")) {
            "stable" -> findViewById<MaterialRadioButton>(R.id.stable).isChecked = true
            "ptb" -> findViewById<MaterialRadioButton>(R.id.ptb).isChecked = true
            "canary" -> findViewById<MaterialRadioButton>(R.id.canary).isChecked = true
        }
        when (sPrefs.getString("splash", "viggy")) {
            "viggy" -> findViewById<MaterialRadioButton>(R.id.viggy).isChecked = true
            "shiggy" -> findViewById<MaterialRadioButton>(R.id.shiggy).isChecked = true
            "oneko" -> findViewById<MaterialRadioButton>(R.id.oneko).isChecked = true
        }
        if(sPrefs.getString("vencordLocation", "")?.isNotBlank() == true) {
            findViewById<CheckBox>(R.id.allow_custom_location).isChecked = true
            val devbuildField = findViewById<TextInputEditText>(R.id.custom_location)
            devbuildField.visibility = View.VISIBLE
            devbuildField.setText(sPrefs.getString("vencordLocation", ""))
        }

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
                            findViewById<EditText>(R.id.custom_location).visibility = View.VISIBLE
                        }
                        .show()
            }
            else {
                findViewById<EditText>(R.id.custom_location).visibility = View.GONE
                findViewById<EditText>(R.id.custom_location).setText("")
            }
        }

        findViewById<ExtendedFloatingActionButton>(R.id.save_settings).setOnClickListener {
            val editor = sPrefs.edit()

            editor.putBoolean("checkVendroidUpdates", findViewById<MaterialSwitch>(R.id.check_vendroid_updates).isChecked)
            editor.putBoolean("desktopMode", findViewById<MaterialSwitch>(R.id.desktop_mode).isChecked)
            if (findViewById<RadioButton>(R.id.stable).isChecked) editor.putString("discordBranch", "stable")
            if (findViewById<RadioButton>(R.id.ptb).isChecked) editor.putString("discordBranch", "ptb")
            if (findViewById<RadioButton>(R.id.canary).isChecked) editor.putString("discordBranch", "canary")
            if (findViewById<RadioButton>(R.id.viggy).isChecked) editor.putString("splash", "viggy")
            if (findViewById<RadioButton>(R.id.shiggy).isChecked) editor.putString("splash", "shiggy")
            if (findViewById<RadioButton>(R.id.oneko).isChecked) editor.putString("splash", "oneko")
            if (findViewById<CheckBox>(R.id.allow_custom_location).isChecked && findViewById<EditText>(R.id.custom_location).text.isNotBlank()) editor.putString("vencordLocation", findViewById<EditText>(R.id.custom_location).text.toString())

            editor.apply()
            Toast.makeText(this, "Settings saved, restart Vendroid to apply them.", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}