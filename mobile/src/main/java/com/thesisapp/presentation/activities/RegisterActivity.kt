package com.thesisapp.presentation.activities

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.thesisapp.R
import com.thesisapp.utils.AuthManager
import com.thesisapp.utils.UserRole

class RegisterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val emailInput = findViewById<TextInputEditText>(R.id.inputEmail)
        val passwordInput = findViewById<TextInputEditText>(R.id.inputPassword)
        val roleGroup = findViewById<RadioGroup>(R.id.roleGroup)
        val radioCoach = findViewById<RadioButton>(R.id.radioCoach)
        val radioSwimmer = findViewById<RadioButton>(R.id.radioSwimmer)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val linkLogin = findViewById<TextView>(R.id.linkLogin)

        fun selectedRole(): UserRole = if (radioCoach.isChecked) UserRole.COACH else UserRole.SWIMMER

        linkLogin.setOnClickListener {
            // Navigate back to login
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
        }

        btnRegister.setOnClickListener {
            val email = emailInput.text?.toString()?.trim().orEmpty()
            val password = passwordInput.text?.toString()?.trim().orEmpty()
            val role = selectedRole()
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val existingRole = AuthManager.getUserRole(this, email)
            if (existingRole != null && existingRole != role) {
                Toast.makeText(this, "Email already registered as ${existingRole.name.lowercase()}", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val ok = AuthManager.register(this, email, password, role)
            if (!ok) {
                Toast.makeText(this, "User already exists", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (AuthManager.login(this, email, password, role)) {
                onAuthSuccessful(role)
            } else {
                Toast.makeText(this, "Registration succeeded, but login failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onAuthSuccessful(role: UserRole) {
        when (role) {
            UserRole.COACH -> {
                val user = AuthManager.currentUser(this)!!
                val teams = AuthManager.getCoachTeams(this, user.email)
                if (teams.isEmpty()) {
                    startActivity(Intent(this, CreateTeamActivity::class.java))
                } else {
                    AuthManager.setCurrentTeamId(this, teams.first())
                    startActivity(Intent(this, MainActivity::class.java))
                }
                finish()
            }
            UserRole.SWIMMER -> {
                val user = AuthManager.currentUser(this)!!
                val teams = AuthManager.getSwimmerTeams(this, user.email)
                // Always go to MainActivity - it will show empty state if no teams
                if (teams.isNotEmpty()) {
                    AuthManager.setCurrentTeamId(this, teams.first())
                }
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }
    
    // Hide keyboard when touching outside of EditText
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val view = currentFocus
            if (view != null) {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
                view.clearFocus()
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}
