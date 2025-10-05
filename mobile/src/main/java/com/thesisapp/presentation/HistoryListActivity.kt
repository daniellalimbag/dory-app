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
import com.thesisapp.utils.animateClick
import java.text.SimpleDateFormat
import java.util.Locale

class HistoryListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnReturn: ImageButton
    private lateinit var btnFilterPerson: Button
    private lateinit var btnSortDate: Button
    private lateinit var adapter: HistoryListAdapter
    private lateinit var db: AppDatabase

    private var allSessions = listOf<Session>()
    private var filteredSessions = listOf<Session>()
    private var sortAscending = false // Start with newest first

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.history_list)

        btnReturn = findViewById(R.id.btnReturn)
        btnFilterPerson = findViewById(R.id.btnFilterPerson)
        btnSortDate = findViewById(R.id.btnSortDate)
        recyclerView = findViewById(R.id.recyclerViewSessions)
        recyclerView.layoutManager = LinearLayoutManager(this)
        db = AppDatabase.getInstance(this)

        loadSessionSummaries()

        btnReturn.setOnClickListener {
            it.animateClick()
            finish()
        }

        btnFilterPerson.setOnClickListener {
            it.animateClick()
            showFilterDialog()
        }

        btnSortDate.setOnClickListener {
            it.animateClick()
            sortSessions()
        }
    }

    private fun loadSessionSummaries() {
        // Using dummy data for now - will be replaced with database queries later
        allSessions = getAllDummySessions()
        filteredSessions = allSessions.sortedByDescending { parseDate(it.date) }

        adapter = HistoryListAdapter(filteredSessions) { session ->
            // TODO: Navigate to session details
            Toast.makeText(this, "View session: ${session.fileName}", Toast.LENGTH_SHORT).show()
        }
        recyclerView.adapter = adapter
    }

    private fun showFilterDialog() {
        val swimmers = allSessions.map { it.swimmerName }.distinct().sorted().toTypedArray()
        val checkedItems = BooleanArray(swimmers.size) { true }

        AlertDialog.Builder(this)
            .setTitle("Filter by Swimmer")
            .setMultiChoiceItems(swimmers, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("Apply") { _, _ ->
                val selectedSwimmers = swimmers.filterIndexed { index, _ -> checkedItems[index] }
                filterSessions(selectedSwimmers)
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Show All") { _, _ ->
                filteredSessions = if (sortAscending) {
                    allSessions.sortedBy { parseDate(it.date) }
                } else {
                    allSessions.sortedByDescending { parseDate(it.date) }
                }
                adapter.updateSessions(filteredSessions)
                Toast.makeText(this, "Showing all swimmers", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun filterSessions(swimmerNames: List<String>) {
        filteredSessions = if (swimmerNames.isEmpty()) {
            allSessions
        } else {
            allSessions.filter { session ->
                swimmerNames.contains(session.swimmerName)
            }
        }

        // Maintain current sort order
        filteredSessions = if (sortAscending) {
            filteredSessions.sortedBy { parseDate(it.date) }
        } else {
            filteredSessions.sortedByDescending { parseDate(it.date) }
        }

        adapter.updateSessions(filteredSessions)
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

    private fun getAllDummySessions(): List<Session> {
        return listOf(
            Session(1, "session_phelps_001.csv", "October 5, 2025", "10:30 AM - 11:15 AM", "Michael Phelps", 1),
            Session(2, "session_ledecky_001.csv", "October 5, 2025", "2:00 PM - 2:45 PM", "Katie Ledecky", 2),
            Session(3, "session_phelps_002.csv", "October 4, 2025", "9:00 AM - 9:50 AM", "Michael Phelps", 1),
            Session(4, "session_dressel_001.csv", "October 3, 2025", "3:30 PM - 4:20 PM", "Caeleb Dressel", 3),
            Session(5, "session_mckeon_001.csv", "October 3, 2025", "11:00 AM - 11:45 AM", "Emma McKeon", 4),
            Session(6, "session_ledecky_002.csv", "October 2, 2025", "8:00 AM - 8:50 AM", "Katie Ledecky", 2),
            Session(7, "session_peaty_001.csv", "October 1, 2025", "4:00 PM - 4:45 PM", "Adam Peaty", 5),
            Session(8, "session_phelps_003.csv", "September 30, 2025", "1:00 PM - 1:50 PM", "Michael Phelps", 1),
            Session(9, "session_dressel_002.csv", "September 28, 2025", "10:00 AM - 10:45 AM", "Caeleb Dressel", 3),
            Session(10, "session_mckeon_002.csv", "September 27, 2025", "3:00 PM - 3:50 PM", "Emma McKeon", 4),
            Session(11, "session_ledecky_003.csv", "September 25, 2025", "9:30 AM - 10:20 AM", "Katie Ledecky", 2),
            Session(12, "session_peaty_002.csv", "September 23, 2025", "2:30 PM - 3:15 PM", "Adam Peaty", 5),
            Session(13, "session_phelps_004.csv", "September 20, 2025", "11:00 AM - 11:50 AM", "Michael Phelps", 1),
            Session(14, "session_dressel_003.csv", "September 18, 2025", "8:30 AM - 9:20 AM", "Caeleb Dressel", 3),
            Session(15, "session_mckeon_003.csv", "September 15, 2025", "4:30 PM - 5:15 PM", "Emma McKeon", 4)
        )
    }
}