package com.thesisapp.presentation

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.thesisapp.R
import com.thesisapp.utils.AuthManager

class EnrollViaCodeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enroll_via_code)

        val inputCode = findViewById<EditText>(R.id.inputSwimmerCode)
        val btnEnroll = findViewById<Button>(R.id.btnEnroll)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        btnBack.setOnClickListener { finish() }

        btnEnroll.setOnClickListener {
            val code = inputCode.text.toString().trim()
            if (code.isEmpty()) {
                Toast.makeText(this, "Enter code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Dummy codes
            val dummy = listOf("La Salle")
            val isValid = dummy.any { it.equals(code, ignoreCase = true) }
            if (!isValid) {
                Toast.makeText(this, "Invalid team code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val user = AuthManager.currentUser(this)
            if (user == null) {
                Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Mark as enrolled in-memory via a pseudo team id stored in preferences
            val dummyTeamId = 999999 // Int to match expected type
            AuthManager.setCurrentTeamId(this, dummyTeamId)
            Toast.makeText(this, "Enrolled in team", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}

