package com.thesisapp.presentation

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.Session
import com.thesisapp.data.SessionRepository
import com.thesisapp.utils.AuthManager
import com.thesisapp.utils.UserRole
import com.thesisapp.utils.animateClick
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnReturn: ImageButton
    private lateinit var adapter: HistoryListAdapter
    private lateinit var db: AppDatabase

    private var allSessions = listOf<Session>()
    private var filteredSessions = listOf<Session>()
    private var sortAscending = false // Start with newest first

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.history_list)

        btnReturn = findViewById(R.id.btnReturn)
        recyclerView = findViewById(R.id.recyclerViewSessions)
        recyclerView.layoutManager = LinearLayoutManager(this)
        db = AppDatabase.getInstance(this)

        adapter = HistoryListAdapter(emptyList()) { session ->
            Toast.makeText(this, "View session: ${session.fileName}", Toast.LENGTH_SHORT).show()
        }
        recyclerView.adapter = adapter

        // Observe repository
        SessionRepository.getSessions().observe(this) { list ->
            allSessions = list
            applyRoleFilterAndSort()
        }

        btnReturn.setOnClickListener {
            it.animateClick()
            finish()
        }
    }

    private fun sortSessions() {
        sortAscending = !sortAscending

        filteredSessions = if (sortAscending) {
            filteredSessions.sortedBy { parseDate(it.date) }
        } else {
            filteredSessions.sortedByDescending { parseDate(it.date) }
        }

        adapter.updateSessions(filteredSessions)
        val sortOrder = if (sortAscending) "oldest first" else "newest first"
        Toast.makeText(this, "Sorted: $sortOrder", Toast.LENGTH_SHORT).show()
    }

    private fun parseDate(dateString: String): Long {
        return try {
            val format = SimpleDateFormat("MMMM dd, yyyy", Locale.ENGLISH)
            format.parse(dateString)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun applyRoleFilterAndSort() {
        val user = AuthManager.currentUser(this)
        if (user == null) {
            filteredSessions = emptyList()
            adapter.updateSessions(filteredSessions)
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val result: List<Session> = if (user.role == UserRole.SWIMMER) {
                val teamId = AuthManager.currentTeamId(this@HistoryListActivity)
                val swimmerId = AuthManager.getLinkedSwimmerId(this@HistoryListActivity, user.email, teamId)
                allSessions.filter { it.swimmerId == (swimmerId ?: -1) }
            } else {
                val teamId = AuthManager.currentTeamId(this@HistoryListActivity)
                if (teamId == null) {
                    emptyList()
                } else {
                    val swimmerIds = db.swimmerDao().getSwimmersForTeam(teamId).map { it.id }.toSet()
                    allSessions.filter { swimmerIds.contains(it.swimmerId) }
                }
            }
            val sorted = result.sortedByDescending { parseDate(it.date) }
            withContext(Dispatchers.Main) {
                filteredSessions = sorted
                adapter.updateSessions(filteredSessions)
            }
        }
    }
}