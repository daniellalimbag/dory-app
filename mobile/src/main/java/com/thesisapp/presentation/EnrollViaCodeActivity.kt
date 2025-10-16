package com.thesisapp.presentation

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.utils.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EnrollViaCodeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enroll_via_code)

        val inputCode = findViewById<EditText>(R.id.inputSwimmerCode)
        val btnEnroll = findViewById<Button>(R.id.btnEnroll)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        btnBack.setOnClickListener { finish() }

        btnEnroll.setOnClickListener {
            val code = inputCode.text.toString().trim().uppercase()
            if (code.isEmpty()) {
                Toast.makeText(this, "Enter code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch(Dispatchers.IO) {
                val db = AppDatabase.getInstance(this@EnrollViaCodeActivity)
                val swimmer = db.swimmerDao().getByCode(code)
                withContext(Dispatchers.Main) {
                    if (swimmer == null) {
                        Toast.makeText(this@EnrollViaCodeActivity, "Invalid code", Toast.LENGTH_SHORT).show()
                    } else {
                        val user = AuthManager.currentUser(this@EnrollViaCodeActivity)
                        if (user == null) {
                            Toast.makeText(this@EnrollViaCodeActivity, "Please login first", Toast.LENGTH_SHORT).show()
                            return@withContext
                        }
                        AuthManager.linkSwimmerToTeam(this@EnrollViaCodeActivity, user.email, swimmer.teamId, swimmer.id)
                        AuthManager.setCurrentTeamId(this@EnrollViaCodeActivity, swimmer.teamId)
                        Toast.makeText(this@EnrollViaCodeActivity, "Enrolled in team", Toast.LENGTH_LONG).show()
                        startActivity(Intent(this@EnrollViaCodeActivity, MainActivity::class.java))
                        finish()
                    }
                }
            }
        }
    }
}

