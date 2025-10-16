package com.thesisapp.presentation

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.utils.AuthManager
import com.thesisapp.utils.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ManageCoachesActivity : AppCompatActivity() {
    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_coaches)

        val tvJoinCode = findViewById<TextView>(R.id.tvTeamJoinCode)
        val etEmail = findViewById<EditText>(R.id.inputCoachEmail)
        val btnAdd = findViewById<Button>(R.id.btnAddCoach)
        val btnContinue = findViewById<Button>(R.id.btnContinue)
        listView = findViewById(R.id.listCoaches)

        btnContinue.setOnClickListener {
            // Navigate to main dashboard
            val intent = android.content.Intent(this, MainActivity::class.java)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        listView.adapter = adapter

        refreshData(tvJoinCode)

        btnAdd.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isEmpty()) return@setOnClickListener
            val teamId = AuthManager.currentTeamId(this)
            if (teamId == null) {
                Toast.makeText(this, "No team selected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!AuthManager.userExists(this, email)) {
                Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val role = AuthManager.getUserRole(this, email)
            if (role != UserRole.COACH) {
                Toast.makeText(this, "User is not a coach", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AuthManager.addCoachToTeam(this, teamId, email)
            AuthManager.addCoachTeam(this, email, teamId)
            etEmail.text = null
            refreshData(tvJoinCode)
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val teamId = AuthManager.currentTeamId(this)
            val email = adapter.getItem(position) ?: return@setOnItemClickListener
            // Prevent removing last coach or yourself if you are the only coach
            if (teamId == null) return@setOnItemClickListener
            val coaches = AuthManager.getTeamCoaches(this, teamId)
            if (coaches.size <= 1) {
                Toast.makeText(this, "Cannot remove the last coach", Toast.LENGTH_SHORT).show()
                return@setOnItemClickListener
            }
            AuthManager.removeCoachFromTeam(this, teamId, email)
            refreshData(tvJoinCode)
        }
    }

    private fun refreshData(tvJoinCode: TextView) {
        val teamId = AuthManager.currentTeamId(this)
        if (teamId == null) {
            tvJoinCode.text = ""
            adapter.clear()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(this@ManageCoachesActivity)
            val team = db.teamDao().getById(teamId)
            val coaches = AuthManager.getTeamCoaches(this@ManageCoachesActivity, teamId)
            withContext(Dispatchers.Main) {
                tvJoinCode.text = team?.joinCode?.let { getString(R.string.team_join_code, it) } ?: ""
                adapter.clear()
                adapter.addAll(coaches)
                adapter.notifyDataSetChanged()
            }
        }
    }
}