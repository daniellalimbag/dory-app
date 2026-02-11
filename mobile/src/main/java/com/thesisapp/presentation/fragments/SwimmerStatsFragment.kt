package com.thesisapp.presentation.fragments

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.non_dao.MlResult
import com.thesisapp.data.non_dao.Swimmer
import com.thesisapp.presentation.adapters.SessionAdapter
import com.thesisapp.data.repository.SwimSessionsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class SwimmerStatsFragment : Fragment() {

    @Inject
    lateinit var swimSessionsRepository: SwimSessionsRepository

    private var swimmer: Swimmer? = null
    private lateinit var db: AppDatabase
    private var sessions = listOf<MlResult>()

    private lateinit var sessionsRecyclerView: RecyclerView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var btnCreateDummySession: MaterialButton
    private var sessionAdapter: SessionAdapter? = null

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
        return inflater.inflate(R.layout.fragment_swimmer_stats, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.Companion.getInstance(requireContext())

        // Initialize views
        sessionsRecyclerView = view.findViewById(R.id.sessionsRecyclerView)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        btnCreateDummySession = view.findViewById(R.id.btnCreateDummySession)

        // Setup RecyclerView
        sessionsRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Dummy session creator button
        btnCreateDummySession.setOnClickListener {
            createDummySession()
        }

        // Load data
        loadData()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun loadData() {
        val swimmerLocal = swimmer ?: return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            // Load all sessions for this swimmer
            sessions = runCatching {
                swimSessionsRepository.getSessionsForSwimmer(swimmerLocal.id.toLong())
            }.getOrElse {
                db.mlResultDao().getResultsForSwimmer(swimmerLocal.id)
            }

            withContext(Dispatchers.Main) {
                if (sessions.isEmpty()) {
                    sessionsRecyclerView.visibility = View.GONE
                    emptyStateLayout.visibility = View.VISIBLE
                } else {
                    sessionsRecyclerView.visibility = View.VISIBLE
                    emptyStateLayout.visibility = View.GONE

                    // Create adapter - only allow viewing categorized sessions
                    sessionAdapter = SessionAdapter(sessions) { session ->
                        // Only allow viewing details for categorized sessions
                        if (session.exerciseId != null) {
                            openSessionDetails(session)
                        }
                    }
                    sessionsRecyclerView.adapter = sessionAdapter
                }
            }
        }
    }

    private fun openSessionDetails(session: MlResult) {
        Toast.makeText(
            requireContext(),
            "Session details are not available in this version",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun createDummySession() {
        val swimmerLocal = swimmer ?: return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            // Create a dummy session
            val sessionId = System.currentTimeMillis().toInt()
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            val dummySession = MlResult(
                sessionId = sessionId,
                swimmerId = swimmerLocal.id.toInt(),
                date = sdf.format(Date()),
                timeStart = "10:00:00",
                timeEnd = "10:45:00",
                totalDistance = 2000,
                avgLapTime = 32.5f,
                strokeCount = 450,
                avgStrokeLength = 2.22f,
                strokeIndex = 2.5f,
                heartRateBefore = 72,
                heartRateAfter = 145,
                exerciseName = "Uncategorized",
                sets = 10,
                distance = 50,
                backstroke = 0.2f,
                breaststroke = 0.2f,
                butterfly = 0.1f,
                freestyle = 0.5f
            )

            db.mlResultDao().insert(dummySession)

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    requireContext(),
                    "Dummy session created! Click to categorize.",
                    Toast.LENGTH_SHORT
                ).show()
                loadData()  // Refresh the list
            }
        }
    }

    companion object {
        private const val ARG_SWIMMER = "swimmer"

        fun newInstance(swimmer: Swimmer) = SwimmerStatsFragment().apply {
            arguments = Bundle().apply {
                putParcelable(ARG_SWIMMER, swimmer)
            }
        }
    }
}