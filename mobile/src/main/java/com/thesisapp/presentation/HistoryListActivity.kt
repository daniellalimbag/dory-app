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
import java.util.Date

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

        // Load from persistent storage so recordings always show
        lifecycleScope.launch(Dispatchers.IO) {
            preloadSessionsFromDatabase()
        }

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

    override fun onResume() {
        super.onResume()
        // Refresh from Room when returning to this screen
        lifecycleScope.launch(Dispatchers.IO) {
            preloadSessionsFromDatabase()
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
        // Always show all sessions to ensure recordings are visible regardless of login/role
        val sorted = allSessions.sortedByDescending { parseDate(it.date) }
        filteredSessions = sorted
        adapter.updateSessions(filteredSessions)
    }

    private suspend fun preloadSessionsFromDatabase() {
        try {
            val mlDao = db.mlResultDao()
            val swimmerDao = db.swimmerDao()
            val summaries = mlDao.getSessionSummaries()

            if (summaries.isNotEmpty()) {
                // Reset and repopulate from ML results to ensure consistency
                SessionRepository.clear()
                for (ml in summaries) {
                    val swimmerName = swimmerDao.getById(ml.swimmerId)?.name ?: "Swimmer ${ml.swimmerId}"
                    val session = Session(
                        id = 0,
                        fileName = "session_${ml.swimmerId}_${ml.sessionId}.csv",
                        date = ml.date,
                        time = "${ml.timeStart} - ${ml.timeEnd}",
                        swimmerName = swimmerName,
                        swimmerId = ml.swimmerId
                    )
                    SessionRepository.add(session)
                }
                return
            }

            // Fallback: derive sessions from raw SwimData if ML results are absent
            val swimDao = db.swimDataDao()
            val allSwims = swimDao.getSwimsBetweenDates(0L, Long.MAX_VALUE)
            if (allSwims.isEmpty()) return

            SessionRepository.clear()
            val bySession = allSwims.groupBy { it.sessionId }
            val formatterDate = java.text.SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
            val formatterTime = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            for ((sid, records) in bySession.entries.sortedByDescending { it.key }) {
                val first = records.minByOrNull { it.timestamp }?.timestamp ?: System.currentTimeMillis()
                val last = records.maxByOrNull { it.timestamp }?.timestamp ?: first
                val date = formatterDate.format(Date(first))
                val time = "${formatterTime.format(Date(first))} - ${formatterTime.format(Date(last))}"
                val swimmerName = "Swimmer"
                val session = Session(
                    id = 0,
                    fileName = "session_$sid.csv",
                    date = date,
                    time = time,
                    swimmerName = swimmerName,
                    swimmerId = -1
                )
                SessionRepository.add(session)
            }
        } catch (_: Exception) {
            // Ignore and show whatever is available
        }
    }
}