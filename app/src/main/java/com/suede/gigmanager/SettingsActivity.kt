package com.suede.gigmanager

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var ownerNameEdit: EditText
    private lateinit var appLabelEdit: EditText
    private lateinit var saveBtn: Button

    private val PREFS = "gig_prefs"
    private val KEY_OWNER = "owner_name"
    private val KEY_LABEL = "app_label"
    

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        ownerNameEdit = findViewById(R.id.editOwnerName)
        appLabelEdit = findViewById(R.id.editAppLabel)
        saveBtn = findViewById(R.id.btnSaveSettings)

        // Apply window insets so top content isn't hidden by status bar/notch
        try {
            val root = findViewById<View>(R.id.settingsRootScroll)
            ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
                val sys = insets.getInsets(WindowInsetsCompat.Type.statusBars())
                // Convert 8dp to px for small extra spacing
                val scale = resources.displayMetrics.density
                val extra = (8 * scale).toInt()
                v.setPadding(v.paddingLeft, sys.top + extra, v.paddingRight, v.paddingBottom)
                WindowInsetsCompat.CONSUMED
            }
            ViewCompat.requestApplyInsets(root)
        } catch (_: Exception) {}

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        ownerNameEdit.setText(prefs.getString(KEY_OWNER, ""))
        appLabelEdit.setText(prefs.getString(KEY_LABEL, getString(R.string.app_name)))

        // Ensure the activity title matches the loaded app label
        try { supportActionBar?.title = appLabelEdit.text.toString().trim() } catch (_: Exception) {}

        // Keep action bar title in sync while editing
        appLabelEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                try { supportActionBar?.title = s?.toString()?.trim() ?: getString(R.string.app_name) } catch (_: Exception) {}
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        saveBtn.setOnClickListener {
            val owner = ownerNameEdit.text.toString().trim()
            val label = appLabelEdit.text.toString().trim()
            val editor = prefs.edit()
            editor.putString(KEY_OWNER, owner)
            if (label.isNotEmpty()) editor.putString(KEY_LABEL, label)
            editor.apply()
            setResult(Activity.RESULT_OK)
            finish()
        }
    }
}
