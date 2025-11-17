package com.thesisapp.presentation

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.Session
import com.thesisapp.data.Swimmer
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SwimmerSessionsFragment : Fragment() {

    private var swimmer: Swimmer? = null
    private lateinit var adapter: SessionsAdapter
    private var allSessions = listOf<Session>()
    private var filteredSessions = listOf<Session>()
    private var sortAscending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            swimmer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getParcelable(ARG_SWIMMER, Swimmer::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getParcelable(ARG_SWIMMER)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_swimmer_sessions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.sessionsRecyclerView)
        val noSessionsLayout = view.findViewById<LinearLayout>(R.id.noSessionsLayout)
        val btnFilterPerson = view.findViewById<Button>(R.id.btnFilterPerson)
        val btnSortDate = view.findViewById<Button>(R.id.btnSortDate)

        val swimmerId = swimmer?.id
        if (swimmerId == null) {
            noSessionsLayout.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            btnFilterPerson.visibility = View.GONE
            btnSortDate.visibility = View.GONE
            return
        }

        val db = AppDatabase.getInstance(requireContext())

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val results = db.mlResultDao().getResultsForSwimmer(swimmerId)

                val sessionsFromDb = results.map { ml ->
                    val timeRange = "${ml.timeStart} - ${ml.timeEnd}"
                    Session(
                        id = ml.sessionId,
                        fileName = ml.exerciseName ?: "Session",
                        date = ml.date,
                        time = timeRange,
                        swimmerName = swimmer?.name ?: "",
                        swimmerId = swimmerId
                    )
                }

                allSessions = sessionsFromDb
                filteredSessions = allSessions.sortedByDescending { parseDate(it.date) }

                withContext(Dispatchers.Main) {
                    if (filteredSessions.isEmpty()) {
                        noSessionsLayout.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                        btnFilterPerson.visibility = View.GONE
                        btnSortDate.visibility = View.GONE
                    } else {
                        noSessionsLayout.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE

                        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())

                        adapter = SessionsAdapter(filteredSessions) { session ->
                            val intent = Intent(requireContext(), HistorySessionActivity::class.java)
                            intent.putExtra("sessionId", session.id)
                            startActivity(intent)
                        }
                        recyclerView.adapter = adapter
                    }

                    // Since we're only showing one swimmer, hide the filter button
                    btnFilterPerson.visibility = View.GONE

                    btnSortDate.setOnClickListener {
                        sortSessions()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to load sessions: ${e.message}", Toast.LENGTH_LONG).show()
                    noSessionsLayout.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                    btnFilterPerson.visibility = View.GONE
                    btnSortDate.visibility = View.GONE
                }
            }
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
        Toast.makeText(requireContext(), "Sorted: $sortOrder", Toast.LENGTH_SHORT).show()
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
        // Return all dummy sessions - will be filtered by swimmer ID
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

    companion object {
        private const val ARG_SWIMMER = "swimmer"

        fun newInstance(swimmer: Swimmer) = SwimmerSessionsFragment().apply {
            arguments = Bundle().apply {
                putParcelable(ARG_SWIMMER, swimmer)
            }
        }
    }
}
