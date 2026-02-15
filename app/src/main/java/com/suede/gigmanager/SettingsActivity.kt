package com.suede.gigmanager

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var ownerNameEdit: EditText
    private lateinit var appLabelEdit: EditText
    private lateinit var pickImageBtn: Button
    private lateinit var saveBtn: Button
    private lateinit var previewImage: ImageView

    private val PREFS = "gig_prefs"
    private val KEY_OWNER = "owner_name"
    private val KEY_LABEL = "app_label"
    private val KEY_ICON_URI = "app_icon_uri"
    private val REQ_PICK_IMAGE = 1234

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        ownerNameEdit = findViewById(R.id.editOwnerName)
        appLabelEdit = findViewById(R.id.editAppLabel)
        pickImageBtn = findViewById(R.id.btnPickImage)
        saveBtn = findViewById(R.id.btnSaveSettings)
        previewImage = findViewById(R.id.previewImage)

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        ownerNameEdit.setText(prefs.getString(KEY_OWNER, ""))
        appLabelEdit.setText(prefs.getString(KEY_LABEL, getString(R.string.app_name)))

        prefs.getString(KEY_ICON_URI, null)?.let { uriStr ->
            try {
                val uri = Uri.parse(uriStr)
                contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    previewImage.setImageBitmap(bmp)
                }
            } catch (_: Exception) {}
        }

        pickImageBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            startActivityForResult(intent, REQ_PICK_IMAGE)
        }

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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_IMAGE && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            // Persist permission to access this URI
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
            prefs.edit().putString(KEY_ICON_URI, uri.toString()).apply()

            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    previewImage.setImageBitmap(bmp)
                }
            } catch (_: Exception) {}
        }
    }
}
