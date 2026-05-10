package com.suede.gigmanager

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class AccountActivity : AppCompatActivity() {

    private lateinit var syncService: GigSyncService
    private lateinit var dataManager: GigDataManager

    private lateinit var layoutLoggedIn: View
    private lateinit var layoutLoggedOut: View
    private lateinit var tvLoggedInAs: TextView
    private lateinit var btnSyncNow: Button
    private lateinit var btnSignOut: Button
    private lateinit var btnTabLogin: Button
    private lateinit var btnTabRegister: Button
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnSubmit: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView

    private var isRegisterMode = false

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, AccountActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Sync Account"

        syncService = GigSyncService(this)
        dataManager = GigDataManager(this)

        layoutLoggedIn = findViewById(R.id.layoutLoggedIn)
        layoutLoggedOut = findViewById(R.id.layoutLoggedOut)
        tvLoggedInAs = findViewById(R.id.tvLoggedInAs)
        btnSyncNow = findViewById(R.id.btnSyncNow)
        btnSignOut = findViewById(R.id.btnSignOut)
        btnTabLogin = findViewById(R.id.btnTabLogin)
        btnTabRegister = findViewById(R.id.btnTabRegister)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnSubmit = findViewById(R.id.btnSubmit)
        progressBar = findViewById(R.id.progressBar)
        tvError = findViewById(R.id.tvError)

        refreshUi()

        btnTabLogin.setOnClickListener { switchMode(register = false) }
        btnTabRegister.setOnClickListener { switchMode(register = true) }
        btnSubmit.setOnClickListener { onSubmit() }
        btnSyncNow.setOnClickListener { onSyncNow() }
        btnSignOut.setOnClickListener { onSignOut() }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun refreshUi() {
        if (syncService.isLoggedIn()) {
            layoutLoggedIn.visibility = View.VISIBLE
            layoutLoggedOut.visibility = View.GONE
            tvLoggedInAs.text = "Logged in as:\n${syncService.getEmail()}"
        } else {
            layoutLoggedIn.visibility = View.GONE
            layoutLoggedOut.visibility = View.VISIBLE
        }
    }

    private fun switchMode(register: Boolean) {
        isRegisterMode = register
        tvError.visibility = View.GONE
        if (register) {
            btnTabRegister.isEnabled = false
            btnTabLogin.isEnabled = true
            btnSubmit.text = "Register"
        } else {
            btnTabLogin.isEnabled = false
            btnTabRegister.isEnabled = true
            btnSubmit.text = "Sign In"
        }
    }

    private fun onSubmit() {
        val email = etEmail.text?.toString()?.trim() ?: ""
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
                        refreshUi()
                        Toast.makeText(this, if (isRegisterMode) "Account created" else "Signed in", Toast.LENGTH_SHORT).show()
                    }
                    is ApiResult.Error -> showError(result.message)
                }
            }
        }.start()
    }

    private fun onSyncNow() {
        setLoading(true)
        Thread {
            val tourName = dataManager.getCurrentTourName()
            val gigs = dataManager.loadGigs()
            val archives = dataManager.loadArchives()
            val result = syncService.push(tourName, gigs, archives)
            runOnUiThread {
                setLoading(false)
                when (result) {
                    is ApiResult.Success -> Toast.makeText(this, "Synced successfully", Toast.LENGTH_SHORT).show()
                    is ApiResult.Error -> Toast.makeText(this, "Sync failed: ${result.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun onSignOut() {
        syncService.clearCredentials()
        refreshUi()
        Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show()
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnSubmit.isEnabled = !loading
        btnSyncNow.isEnabled = !loading
        btnSignOut.isEnabled = !loading
        tvError.visibility = View.GONE
    }

    private fun showError(msg: String) {
        tvError.text = msg
        tvError.visibility = View.VISIBLE
    }
}
