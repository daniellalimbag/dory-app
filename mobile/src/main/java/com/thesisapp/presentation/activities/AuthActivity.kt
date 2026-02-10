package com.thesisapp.presentation.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.thesisapp.R
import com.thesisapp.data.repository.AuthRepository
import com.thesisapp.utils.AuthManager
import com.thesisapp.utils.UserRole
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession
import javax.inject.Inject
import kotlinx.coroutines.launch
import java.net.URLDecoder

@AndroidEntryPoint
class AuthActivity : AppCompatActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var supabase: SupabaseClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        val emailInput = findViewById<TextInputEditText>(R.id.inputEmail)
        val passwordInput = findViewById<TextInputEditText>(R.id.inputPassword)
        val loginBtn = findViewById<Button>(R.id.btnLogin)
        val linkRegister = findViewById<TextView>(R.id.linkRegister)
        val linkForgotPassword = findViewById<TextView>(R.id.linkForgotPassword)

        linkRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        linkForgotPassword.setOnClickListener {
            val etEmail = EditText(this).apply {
                hint = "Email"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                setText(emailInput.text?.toString()?.trim().orEmpty())
            }

            AlertDialog.Builder(this)
                .setTitle("Forgot password")
                .setMessage("We will email you a link to reset your password.")
                .setView(etEmail)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Send") { _, _ ->
                    val email = etEmail.text?.toString()?.trim().orEmpty()
                    if (email.isEmpty()) {
                        Toast.makeText(this, "Enter an email", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    lifecycleScope.launch {
                        try {
                            supabase.auth.resetPasswordForEmail(
                                email = email,
                                redirectUrl = "dory://reset-password"
                            )
                            Toast.makeText(this@AuthActivity, "Password reset email sent", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                this@AuthActivity,
                                e.message ?: "Failed to send reset email",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
                .show()
        }

        loginBtn.setOnClickListener {
            val email = emailInput.text?.toString()?.trim().orEmpty()
            val password = passwordInput.text?.toString()?.trim().orEmpty()
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    authRepository.signIn(email = email, password = password)
                    val role = AuthManager.currentUser(this@AuthActivity)?.role
                        ?: error("Login succeeded but local role is missing")
                    onAuthSuccessful(role)
                } catch (e: Exception) {
                    Toast.makeText(
                        this@AuthActivity,
                        e.message ?: "Login failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent) {
        val data: Uri = intent.data ?: return
        if (data.scheme != "dory" || data.host != "reset-password") return

        val tokens = parseFragmentParams(data.fragment)
        val accessToken = tokens["access_token"]
        val refreshToken = tokens["refresh_token"]

        if (accessToken.isNullOrBlank() || refreshToken.isNullOrBlank()) {
            Toast.makeText(this, "Invalid reset link", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                supabase.auth.importSession(
                    UserSession(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresIn = 3600,
                        tokenType = "Bearer",
                        user = null
                    )
                )
                showResetPasswordDialog()
            } catch (e: Exception) {
                Toast.makeText(
                    this@AuthActivity,
                    e.message ?: "Failed to start password reset",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun parseFragmentParams(fragment: String?): Map<String, String> {
        if (fragment.isNullOrBlank()) return emptyMap()
        return fragment.split('&')
            .mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx <= 0) return@mapNotNull null
                val key = URLDecoder.decode(part.substring(0, idx), "UTF-8")
                val value = URLDecoder.decode(part.substring(idx + 1), "UTF-8")
                key to value
            }
            .toMap()
    }

    private fun showResetPasswordDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val etPassword = EditText(this).apply {
            hint = "New password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val etConfirm = EditText(this).apply {
            hint = "Confirm password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        container.addView(etPassword)
        container.addView(etConfirm)

        AlertDialog.Builder(this)
            .setTitle("Reset password")
            .setView(container)
            .setCancelable(false)
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("Save", null)
            .show()
            .also { dialog ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val p1 = etPassword.text?.toString().orEmpty().trim()
                    val p2 = etConfirm.text?.toString().orEmpty().trim()

                    if (p1.isEmpty() || p2.isEmpty()) {
                        Toast.makeText(this, "Enter and confirm your new password", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    if (p1 != p2) {
                        Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    lifecycleScope.launch {
                        try {
                            supabase.auth.updateUser {
                                password = p1
                            }
                            Toast.makeText(this@AuthActivity, "Password updated", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                        } catch (e: Exception) {
                            Toast.makeText(
                                this@AuthActivity,
                                e.message ?: "Failed to update password",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
    }

    private fun onAuthSuccessful(role: UserRole) {
        when (role) {
            UserRole.COACH -> {
                val teamId = AuthManager.currentTeamId(this)
                if (teamId == null) {
                    startActivity(Intent(this, CreateTeamActivity::class.java))
                } else {
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

                // Not linked yet -> enroll via team code
                startActivity(Intent(this, EnrollViaCodeActivity::class.java))
                finish()
            }
        }
    }
}