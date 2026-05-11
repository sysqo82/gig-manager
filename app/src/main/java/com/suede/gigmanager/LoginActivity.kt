package com.suede.gigmanager

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import android.content.res.ColorStateList

class LoginActivity : AppCompatActivity() {

    private lateinit var syncService: GigSyncService

    private lateinit var btnTabLogin: MaterialButton
    private lateinit var btnTabRegister: MaterialButton
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnSubmit: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView

    private var isRegisterMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        syncService = GigSyncService(this)

        // If already logged in, go straight to the gig list
        if (syncService.isLoggedIn()) {
            goToMain()
            return
        }

        setContentView(R.layout.activity_login)

        btnTabLogin    = findViewById(R.id.btnTabLogin)
        btnTabRegister = findViewById(R.id.btnTabRegister)
        etEmail        = findViewById(R.id.etEmail)
        etPassword     = findViewById(R.id.etPassword)
        btnSubmit      = findViewById(R.id.btnSubmit)
        progressBar    = findViewById(R.id.progressBar)
        tvError        = findViewById(R.id.tvError)

        switchMode(register = false)

        btnTabLogin.setOnClickListener    { switchMode(register = false) }
        btnTabRegister.setOnClickListener { switchMode(register = true) }
        btnSubmit.setOnClickListener      { onSubmit() }
    }

    private fun switchMode(register: Boolean) {
        isRegisterMode = register
        tvError.visibility = View.GONE
        btnSubmit.text = if (register) "Register" else "Sign In"
        val active   = if (register) btnTabRegister else btnTabLogin
        val inactive = if (register) btnTabLogin    else btnTabRegister
        val primary  = getColor(R.color.purple_500)
        active.backgroundTintList   = ColorStateList.valueOf(primary)
        active.setTextColor(Color.WHITE)
        inactive.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        inactive.setTextColor(primary)
    }

    private fun onSubmit() {
        val email    = etEmail.text?.toString()?.trim() ?: ""
        val password = etPassword.text?.toString() ?: ""

        if (email.isEmpty() || password.isEmpty()) {
            showError("Please enter email and password")
            return
        }
        if (isRegisterMode && password.length < 8) {
            showError("Password must be at least 8 characters")
            return
        }

        setLoading(true)
        Thread {
            val result = if (isRegisterMode) {
                syncService.register(email, password)
            } else {
                syncService.login(email, password)
            }
            runOnUiThread {
                setLoading(false)
                when (result) {
                    is ApiResult.Success -> {
                        Toast.makeText(
                            this,
                            if (isRegisterMode) "Account created" else "Signed in",
                            Toast.LENGTH_SHORT
                        ).show()
                        goToMain()
                    }
                    is ApiResult.Error -> showError(result.message)
                }
            }
        }.start()
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnSubmit.isEnabled    = !loading
        tvError.visibility     = View.GONE
    }

    private fun showError(msg: String) {
        tvError.text       = msg
        tvError.visibility = View.VISIBLE
    }
}
