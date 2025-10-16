package com.thesisapp.presentation

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

class JoinTeamByCodeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_join_team_by_code)

        val inputCode = findViewById<EditText>(R.id.inputTeamCode)
        val btnJoin = findViewById<Button>(R.id.btnJoinTeam)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        btnBack.setOnClickListener { finish() }

        btnJoin.setOnClickListener {
            val code = inputCode.text.toString().trim().uppercase()
            if (code.isEmpty()) {
                Toast.makeText(this, R.string.enter_code, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch(Dispatchers.IO) {
                val db = AppDatabase.getInstance(this@JoinTeamByCodeActivity)
                val team = db.teamDao().getByJoinCode(code)
                withContext(Dispatchers.Main) {
                    if (team == null) {
                        Toast.makeText(this@JoinTeamByCodeActivity, R.string.invalid_code, Toast.LENGTH_SHORT).show()
                    } else {
                        val user = AuthManager.currentUser(this@JoinTeamByCodeActivity) ?: return@withContext
                        AuthManager.addCoachTeam(this@JoinTeamByCodeActivity, user.email, team.id)
                        AuthManager.addCoachToTeam(this@JoinTeamByCodeActivity, team.id, user.email)
                        AuthManager.setCurrentTeamId(this@JoinTeamByCodeActivity, team.id)
                        Toast.makeText(this@JoinTeamByCodeActivity, R.string.joined_team, Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            }
        }
    }
}

