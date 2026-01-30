package com.thesisapp.presentation.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.thesisapp.R
import com.thesisapp.utils.AuthManager
import com.thesisapp.utils.UserRole

class AuthActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        val emailInput = findViewById<TextInputEditText>(R.id.inputEmail)
        val passwordInput = findViewById<TextInputEditText>(R.id.inputPassword)
        val loginBtn = findViewById<Button>(R.id.btnLogin)
        val linkRegister = findViewById<TextView>(R.id.linkRegister)

        linkRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        loginBtn.setOnClickListener {
            val email = emailInput.text?.toString()?.trim().orEmpty()
            val password = passwordInput.text?.toString()?.trim().orEmpty()
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val role = AuthManager.getUserRole(this, email)
            if (role == null) {
                Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (AuthManager.login(this, email, password, role)) {
                onAuthSuccessful(role)
            } else {
                Toast.makeText(this, "Invalid credentials", Toast.LENGTH_SHORT).show()
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
                if (teams.isNotEmpty()) {
                    val teamId = teams.first()
                    AuthManager.setCurrentTeamId(this, teamId)
                    val swimmerId = AuthManager.getLinkedSwimmerId(this, user.email, teamId)
                    if (swimmerId != null) {
                        startActivity(
                            Intent(this, SwimmerProfileActivity::class.java).apply {
                                putExtra(SwimmerProfileActivity.EXTRA_SWIMMER_ID, swimmerId)
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                        )
                        finish()
                        return
                    }
                }

                // Fall back to MainActivity for empty state / not linked yet
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }
}