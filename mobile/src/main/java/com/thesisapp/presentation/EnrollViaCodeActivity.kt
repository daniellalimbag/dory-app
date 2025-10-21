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
import com.thesisapp.utils.UserRole
import com.thesisapp.data.Swimmer
import com.thesisapp.utils.CodeGenerator
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
            val code = inputCode.text.toString().trim()
            if (code.isEmpty()) {
                Toast.makeText(this, "Enter code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val user = AuthManager.currentUser(this)
            if (user == null) {
                Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch(Dispatchers.IO) {
                val db = AppDatabase.getInstance(this@EnrollViaCodeActivity)

                // Try swimmer code first
                val swimmer = db.swimmerDao().getByCode(code)
                if (swimmer != null) {
                    AuthManager.linkSwimmerToTeam(this@EnrollViaCodeActivity, user.email, swimmer.teamId, swimmer.id)
                    AuthManager.setCurrentTeamId(this@EnrollViaCodeActivity, swimmer.teamId)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@EnrollViaCodeActivity, "Linked to ${swimmer.name}", Toast.LENGTH_LONG).show()
                        startActivity(Intent(this@EnrollViaCodeActivity, MainActivity::class.java))
                        finish()
                    }
                    return@launch
                }

                // Then try team join code (for team enrollment without swimmer linkage)
                val team = db.teamDao().getByJoinCode(code.uppercase())
                if (team != null) {
                    if (user.role == UserRole.SWIMMER) {
                        AuthManager.addSwimmerTeam(this@EnrollViaCodeActivity, user.email, team.id)
                        AuthManager.setCurrentTeamId(this@EnrollViaCodeActivity, team.id)

                        // Auto-create a swimmer profile and link it so tracking can start
                        val placeholderName = user.email.substringBefore('@').replaceFirstChar { it.uppercase() }
                        val swimmer = Swimmer(
                            teamId = team.id,
                            name = placeholderName,
                            birthday = "2000-01-01",
                            height = 170f,
                            weight = 60f,
                            sex = "Unknown",
                            wingspan = 170f,
                            code = CodeGenerator.code(6)
                        )
                        val newId = db.swimmerDao().insertSwimmer(swimmer).toInt()
                        AuthManager.linkSwimmerToTeam(this@EnrollViaCodeActivity, user.email, team.id, newId)
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@EnrollViaCodeActivity, "Joined team ${team.name}", Toast.LENGTH_LONG).show()
                        startActivity(Intent(this@EnrollViaCodeActivity, MainActivity::class.java))
                        finish()
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@EnrollViaCodeActivity, "Invalid code", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

