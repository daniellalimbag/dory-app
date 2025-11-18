package com.thesisapp.presentation.fragments

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.non_dao.Goal
import com.thesisapp.data.non_dao.GoalProgress
import com.thesisapp.data.non_dao.MlResult
import com.thesisapp.data.non_dao.Swimmer
import com.thesisapp.presentation.activities.TrackSwimmerActivity
import com.thesisapp.presentation.activities.UncategorizedSessionsActivity
import com.thesisapp.presentation.adapters.SessionAdapter
import com.thesisapp.presentation.adapters.SessionListAdapter
import com.thesisapp.utils.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SwimmerHomeFragment : Fragment() {

    private var swimmer: Swimmer? = null
    private lateinit var db: AppDatabase
    private var currentGoal: Goal? = null
    private var sessions = listOf<MlResult>()

    // Goal card views
    private lateinit var noGoalLayout: LinearLayout
    private lateinit var goalExistsLayout: LinearLayout
    private lateinit var tvGoalTitle: TextView
    private lateinit var tvGoalDeadline: TextView
    private lateinit var goalProgressChart: LineChart

    // Recording button
    private lateinit var btnRecordSession: Button
    private lateinit var tvWatchStatus: TextView
    private lateinit var watchStatusIndicatorCard: View

    // Sessions
    private lateinit var sessionsRecycler: RecyclerView
    private lateinit var sessionAdapter: SessionListAdapter
    private lateinit var pendingBadge: MaterialButton
    private lateinit var tvTeamName: TextView

    // Metrics card
    private lateinit var metricsCard: MaterialCardView
    private lateinit var tvMetricsTitle: TextView
    private lateinit var tvStrokeCount: TextView
    private lateinit var tvStrokeLength: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvDuration: TextView
    private lateinit var tvStrokeIndex: TextView
    private lateinit var tvLapTime: TextView
    private lateinit var performanceChart: LineChart
    private lateinit var heartRateChart: BarChart

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
        return inflater.inflate(R.layout.fragment_swimmer_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.Companion.getInstance(requireContext())

        // Initialize views
        noGoalLayout = view.findViewById(R.id.noGoalLayout)
        goalExistsLayout = view.findViewById(R.id.goalExistsLayout)
        tvGoalTitle = view.findViewById(R.id.tvGoalTitle)
        tvGoalDeadline = view.findViewById(R.id.tvGoalDeadline)
        goalProgressChart = view.findViewById(R.id.goalProgressChart)

        btnRecordSession = view.findViewById(R.id.btnRecordSession)
        tvWatchStatus = view.findViewById(R.id.tvWatchStatus)
        watchStatusIndicatorCard = view.findViewById(R.id.watchStatusIndicatorCard)

        sessionsRecycler = view.findViewById(R.id.sessionsRecycler)
        pendingBadge = view.findViewById(R.id.pendingBadge)

        metricsCard = view.findViewById(R.id.metricsCard)
        tvMetricsTitle = view.findViewById(R.id.tvMetricsTitle)
        tvStrokeCount = view.findViewById(R.id.tvStrokeCount)
        tvStrokeLength = view.findViewById(R.id.tvStrokeLength)
        tvDistance = view.findViewById(R.id.tvDistance)
        tvDuration = view.findViewById(R.id.tvDuration)
        tvStrokeIndex = view.findViewById(R.id.tvStrokeIndex)
        tvLapTime = view.findViewById(R.id.tvLapTime)
        performanceChart = view.findViewById(R.id.performanceChart)
        heartRateChart = view.findViewById(R.id.heartRateChart)

        // Setup vertical RecyclerView for sessions (max 3 visible with scroll)
        sessionsRecycler.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        sessionsRecycler.isNestedScrollingEnabled = true

        // Setup record button
        btnRecordSession.setOnClickListener {
            startRecording()
        }

        // Load data
        loadData()
        updateWatchStatus()
    }

    override fun onResume() {
        super.onResume()
        loadData()
        updateWatchStatus()
    }

    private fun loadData() {
        val swimmerLocal = swimmer ?: return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val teamId = AuthManager.currentTeamId(requireContext())

            // Load goal
            currentGoal = if (teamId != null) {
                db.goalDao().getActiveGoalForSwimmer(swimmerLocal.id, teamId)
            } else null

            // Load all categorized sessions (not just recent)
            sessions = db.mlResultDao().getResultsForSwimmer(swimmerLocal.id)
                .filter { it.exerciseId != null }
                .sortedByDescending { it.date } // Most recent first

            // Count uncategorized sessions separately
            val allSessions = db.mlResultDao().getResultsForSwimmer(swimmerLocal.id)
            val uncategorizedCount = allSessions.count { it.exerciseId == null }

            withContext(Dispatchers.Main) {
                updateGoalUI()

                // Update pending badge - make it clickable
                if (uncategorizedCount > 0) {
                    pendingBadge.visibility = View.VISIBLE
                    pendingBadge.text = "$uncategorizedCount pending"
                    pendingBadge.setOnClickListener {
                        Toast.makeText(
                            requireContext(),
                            "Opening $uncategorizedCount uncategorized sessions",
                            Toast.LENGTH_SHORT
                        ).show()
                        showUncategorizedSessions(allSessions.filter { it.exerciseId == null })
                    }
                } else {
                    pendingBadge.visibility = View.GONE
                }

                // Update sessions list (vertical scrolling list)
                if (sessions.isEmpty()) {
                    sessionsRecycler.visibility = View.GONE
                    metricsCard.visibility = View.GONE
                } else {
                    sessionsRecycler.visibility = View.VISIBLE
                    metricsCard.visibility = View.VISIBLE

                    // Use SessionAdapter for vertical list with click to view details
                    val adapter = SessionAdapter(sessions) { session ->
                        displayMetricsForSession(session)
                    }
                    sessionsRecycler.adapter = adapter

                    // Display latest session by default
                    displayMetricsForSession(sessions[0])
                }
            }
        }
    }

    private fun updateGoalUI() {
        if (currentGoal == null) {
            showNoGoal()
        } else {
            showGoalExists()
        }
    }

    private fun showNoGoal() {
        noGoalLayout.visibility = View.VISIBLE
        goalExistsLayout.visibility = View.GONE
    }

    private fun showGoalExists() {
        noGoalLayout.visibility = View.GONE
        goalExistsLayout.visibility = View.VISIBLE

        currentGoal?.let { goal ->
            tvGoalTitle.text = "${goal.eventName} < ${goal.goalTime}"
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            tvGoalDeadline.text = "Deadline: ${dateFormat.format(Date(goal.endDate))}"

            // Load and display progress graph (same as coach view)
            loadProgressGraph(goal.id)
        }
    }

    private fun loadProgressGraph(goalId: Int) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val progressPoints = db.goalProgressDao().getProgressForGoal(goalId)

            withContext(Dispatchers.Main) {
                if (progressPoints.isEmpty()) {
                    // Could show "No progress data yet" message
                    setupGoalProgressGraph(emptyList())
                } else {
                    setupGoalProgressGraph(progressPoints)
                }
            }
        }
    }

    private fun setupGoalProgressGraph(progressPoints: List<GoalProgress>) {
        if (progressPoints.isEmpty()) {
            goalProgressChart.visibility = View.GONE
            return
        }

        goalProgressChart.visibility = View.VISIBLE

        // Use date (timestamp) for x-axis instead of index
        val entries = progressPoints.map { progress ->
            val timeInSeconds = timeStringToSeconds(progress.projectedRaceTime)
            Entry(progress.date.toFloat(), timeInSeconds)
        }

        val dataSet = LineDataSet(entries, "Progress").apply {
            color = requireContext().getColor(R.color.primary)
            setCircleColor(requireContext().getColor(R.color.primary))
            lineWidth = 3f
            circleRadius = 5f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        goalProgressChart.data = LineData(dataSet)
        goalProgressChart.description.isEnabled = false
        goalProgressChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            granularity = 86400000f  // 1 day in milliseconds
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
                    return sdf.format(Date(value.toLong()))
                }
            }
        }
        goalProgressChart.axisRight.isEnabled = false
        goalProgressChart.axisLeft.apply {
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val minutes = (value / 60).toInt()
                    val seconds = (value % 60).toInt()
                    return String.format("%d:%02d", minutes, seconds)
                }
            }
        }
        goalProgressChart.legend.isEnabled = false
        goalProgressChart.animateX(1000)
        goalProgressChart.invalidate()
    }

    private fun timeStringToSeconds(timeString: String): Float {
        val parts = timeString.split(":")
        if (parts.size != 2) return 0f
        val minutes = parts[0].toFloatOrNull() ?: 0f
        val seconds = parts[1].toFloatOrNull() ?: 0f
        return minutes * 60 + seconds
    }

    private fun displayMetricsForSession(session: MlResult) {
        metricsCard.visibility = View.VISIBLE

        // Load exercise details to get prescribed effort level
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val exercise = session.exerciseId?.let { db.exerciseDao().getExerciseById(it) }

            withContext(Dispatchers.Main) {
                // Display exercise details
                val exerciseDetails = buildString {
                    session.exerciseName?.let { append("$it") } ?: append("Session")
                    session.sets?.let { sets ->
                        session.distance?.let { distance ->
                            append(" - $sets sets × ${distance}m")
                        }
                    }
                    exercise?.effortLevel?.let { append(" @ $it%") }
                }
                tvMetricsTitle.text = exerciseDetails

                // Display actual metrics
                tvStrokeCount.text = session.strokeCount?.toString() ?: "--"
                tvStrokeLength.text =
                    session.avgStrokeLength?.let { String.format("%.2f m", it) } ?: "--"
                tvDistance.text = session.totalDistance?.let { "$it m" } ?: "--"
                tvStrokeIndex.text = session.strokeIndex?.let { String.format("%.2f", it) } ?: "--"
                tvLapTime.text = session.avgLapTime?.let { formatTime(it) } ?: "--"

                // Display duration
                val duration = calculateDuration(session.timeStart, session.timeEnd)
                tvDuration.text = duration

                // Setup charts
                session.avgLapTime?.let { setupPerformanceChart(session) }
                setupHeartRateChart(session)
            }
        }
    }

    private fun formatTime(seconds: Float): String {
        val minutes = (seconds / 60).toInt()
        val secs = seconds % 60
        return if (minutes > 0) {
            String.format("%d:%05.2f", minutes, secs)
        } else {
            String.format("%.2fs", secs)
        }
    }

    private fun calculateDuration(startTime: String, endTime: String): String {
        // Simple duration calculation - could be improved
        return try {
            val format = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val start = format.parse(startTime)
            val end = format.parse(endTime)
            if (start != null && end != null) {
                val durationMillis = end.time - start.time
                val minutes = (durationMillis / 1000 / 60).toInt()
                "$minutes min"
            } else {
                "--"
            }
        } catch (e: Exception) {
            "--"
        }
    }

    private fun setupPerformanceChart(session: MlResult) {
        val avgLapTime = session.avgLapTime ?: return
        val laps = (session.sets ?: 5) * (session.reps ?: 2)

        val entries = (1..laps).map { lap ->
            val variation = (Math.random().toFloat() - 0.5f) * 6f
            Entry(lap.toFloat(), avgLapTime + variation)
        }

        val dataSet = LineDataSet(entries, "Lap Times (s)").apply {
            color = requireContext().getColor(R.color.primary)
            setCircleColor(requireContext().getColor(R.color.primary))
            lineWidth = 2f
            circleRadius = 4f
        }

        performanceChart.data = LineData(dataSet)
        performanceChart.description.isEnabled = false
        performanceChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            granularity = 1f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return "Lap ${value.toInt()}"
                }
            }
        }
        performanceChart.axisRight.isEnabled = false
        performanceChart.axisLeft.apply {
            setDrawGridLines(true)
            granularity = 5f
        }
        performanceChart.invalidate()
    }

    private fun setupHeartRateChart(session: MlResult) {
        val hrBefore = session.heartRateBefore?.toFloat() ?: 90f
        val hrAfter = session.heartRateAfter?.toFloat() ?: 150f

        val entries = listOf(
            BarEntry(0f, hrBefore),
            BarEntry(1f, hrAfter)
        )

        val dataSet = BarDataSet(entries, "Heart Rate (BPM)").apply {
            colors = listOf(
                requireContext().getColor(R.color.accent),
                requireContext().getColor(R.color.error)
            )
            valueTextSize = 12f
            valueTextColor = requireContext().getColor(R.color.text)
        }

        heartRateChart.data = BarData(dataSet)
        heartRateChart.description.isEnabled = false
        heartRateChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            granularity = 1f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return when (value.toInt()) {
                        0 -> "Before"
                        1 -> "After"
                        else -> ""
                    }
                }
            }
        }
        heartRateChart.axisRight.isEnabled = false
        heartRateChart.axisLeft.apply {
            axisMinimum = 0f
            granularity = 20f
            setDrawGridLines(true)
        }
        heartRateChart.legend.isEnabled = false
        heartRateChart.invalidate()
    }

    private fun updateWatchStatus() {
        val isConnected = checkWatchConnection()

        if (isConnected) {
            tvWatchStatus.text = "Watch Connected"
            tvWatchStatus.setTextColor(requireContext().getColor(R.color.accent))
            ViewCompat.setBackgroundTintList(
                watchStatusIndicatorCard,
                ColorStateList.valueOf(requireContext().getColor(R.color.accent))
            )
            btnRecordSession.isEnabled = true
        } else {
            tvWatchStatus.text = "Watch Not Connected"
            tvWatchStatus.setTextColor(requireContext().getColor(R.color.error))
            ViewCompat.setBackgroundTintList(
                watchStatusIndicatorCard,
                ColorStateList.valueOf(requireContext().getColor(R.color.error))
            )
            btnRecordSession.isEnabled = false
        }
    }

    private fun checkWatchConnection(): Boolean {
        // TODO: Implement actual Bluetooth watch connection check
        return false
    }

    private fun startRecording() {
        val swimmerLocal = swimmer ?: return
        val intent = Intent(requireContext(), TrackSwimmerActivity::class.java)
        intent.putExtra("SWIMMER_ID", swimmerLocal.id)
        startActivity(intent)
    }

    private fun showUncategorizedSessions(uncategorizedSessions: List<MlResult>) {
        val swimmerLocal = swimmer ?: return
        val intent = Intent(requireContext(), UncategorizedSessionsActivity::class.java)
        intent.putExtra("SWIMMER_ID", swimmerLocal.id)
        startActivity(intent)
    }

    companion object {
        private const val ARG_SWIMMER = "swimmer"

        fun newInstance(swimmer: Swimmer) = SwimmerHomeFragment().apply {
            arguments = Bundle().apply {
                putParcelable(ARG_SWIMMER, swimmer)
            }
        }
    }
}