package com.thesisapp.presentation

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.Session
import com.thesisapp.data.Swimmer
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class SwimmerSessionsFragment : Fragment() {

    private var swimmer: Swimmer? = null
    private lateinit var adapter: SessionsAdapter
    private var allSessions = listOf<Session>()
    private var filteredSessions = listOf<Session>()
    private var sortAscending = false
    private lateinit var db: AppDatabase

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

        db = AppDatabase.getInstance(requireContext())

        lifecycleScope.launch(Dispatchers.IO) {
            val swimmerId = swimmer?.id
            val list = mutableListOf<Session>()
            try {
                if (swimmerId != null) {
                    val summaries = db.mlResultDao().getSessionSummaries()
                    val swimmerDao = db.swimmerDao()
                    val name = swimmerDao.getById(swimmerId)?.name ?: swimmer?.name ?: "Swimmer"
                    for (ml in summaries.filter { it.swimmerId == swimmerId }) {
                        val session = Session(
                            id = 0,
                            fileName = "session_${ml.swimmerId}_${ml.sessionId}.csv",
                            date = ml.date,
                            time = "${ml.timeStart} - ${ml.timeEnd}",
                            swimmerName = name,
                            swimmerId = ml.swimmerId
                        )
                        list.add(session)
                    }
                }
            } catch (_: Exception) { }

            withContext(Dispatchers.Main) {
                allSessions = list
                filteredSessions = allSessions.sortedByDescending { parseDate(it.date) }

                if (filteredSessions.isEmpty()) {
                    noSessionsLayout.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                    btnFilterPerson.visibility = View.GONE
                    btnSortDate.visibility = View.GONE
                } else {
                    noSessionsLayout.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE

                    recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
                    adapter = SessionsAdapter(filteredSessions) { _ -> }
                    recyclerView.adapter = adapter

                    // Since we're only showing one swimmer, hide the filter button
                    btnFilterPerson.visibility = View.GONE

                    btnSortDate.setOnClickListener { sortSessions() }
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
    }

    private fun parseDate(dateString: String): Long {
        return try {
            val format = SimpleDateFormat("MMMM dd, yyyy", Locale.ENGLISH)
            format.parse(dateString)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
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
