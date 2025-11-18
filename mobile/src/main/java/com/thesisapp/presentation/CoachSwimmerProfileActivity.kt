package com.thesisapp.presentation

import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.card.MaterialCardView
import com.thesisapp.R
import com.thesisapp.data.*
import com.thesisapp.presentation.adapters.SessionListAdapter
import com.thesisapp.presentation.dialogs.SetGoalDialogFragment
import com.thesisapp.utils.StrokeMetrics
import com.thesisapp.utils.MetricsApiClient
import com.thesisapp.utils.MetricsSample
import com.thesisapp.utils.MetricsSessionRequest
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class CoachSwimmerProfileActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var swimmer: Swimmer
    private var currentGoal: Goal? = null
    private var sessions: List<MlResult> = listOf()
    // Views
    private lateinit var tvSwimmerName: TextView
    private lateinit var btnBack: ImageButton
    private lateinit var goalCard: MaterialCardView
    private lateinit var noGoalLayout: View
    private lateinit var goalExistsLayout: View
    private lateinit var tvGoalTitle: TextView
    private lateinit var tvGoalDeadline: TextView
    private lateinit var goalProgressChart: LineChart
    private lateinit var btnSetGoal: Button
    private lateinit var btnEditGoal: Button
    private lateinit var btnDeleteGoal: Button
    
    private lateinit var metricsCard: MaterialCardView
    private lateinit var tvMetricsTitle: TextView
    private lateinit var performanceChart: LineChart
    private lateinit var heartRateChart: BarChart
    private lateinit var tvStrokeCount: TextView
    private lateinit var tvStrokeLength: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvDuration: TextView
    private lateinit var tvStrokeIndex: TextView
    private lateinit var tvLapTime: TextView
    private lateinit var tvLapBreakdownTitleCoach: TextView
    private lateinit var tvLapBreakdownCoach: TextView
    
    private lateinit var exerciseListRecycler: RecyclerView
    private lateinit var sessionAdapter: SessionListAdapter

    companion object {
        const val EXTRA_SWIMMER = "extra_swimmer"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_swimmer_profile_coach)

        db = AppDatabase.getInstance(this)

        // Get swimmer from intent
        swimmer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_SWIMMER, Swimmer::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_SWIMMER)
        } ?: run {
            finish()
            return
        }

        initializeViews()
        setupListeners()
        loadData()
    }

    private fun initializeViews() {
        tvSwimmerName = findViewById(R.id.tvSwimmerName)
        btnBack = findViewById(R.id.btnBack)
        
        goalCard = findViewById(R.id.goalCard)
        noGoalLayout = findViewById(R.id.noGoalLayout)
        goalExistsLayout = findViewById(R.id.goalExistsLayout)
        tvGoalTitle = findViewById(R.id.tvGoalTitle)
        tvGoalDeadline = findViewById(R.id.tvGoalDeadline)
        goalProgressChart = findViewById(R.id.goalProgressChart)
        btnSetGoal = findViewById(R.id.btnSetGoal)
        btnEditGoal = findViewById(R.id.btnEditGoal)
        btnDeleteGoal = findViewById(R.id.btnDeleteGoal)
        
        metricsCard = findViewById(R.id.metricsCard)
        tvMetricsTitle = findViewById(R.id.tvMetricsTitle)
        performanceChart = findViewById(R.id.performanceChart)
        heartRateChart = findViewById(R.id.heartRateChart)
        tvStrokeCount = findViewById(R.id.tvStrokeCount)
        tvStrokeLength = findViewById(R.id.tvStrokeLength)
        tvDistance = findViewById(R.id.tvDistance)
        tvDuration = findViewById(R.id.tvDuration)
        tvStrokeIndex = findViewById(R.id.tvStrokeIndex)
        tvLapTime = findViewById(R.id.tvLapTime)
        tvLapBreakdownTitleCoach = findViewById(R.id.tvLapBreakdownTitleCoach)
        tvLapBreakdownCoach = findViewById(R.id.tvLapBreakdownCoach)
        
        exerciseListRecycler = findViewById(R.id.exerciseListRecycler)
        
        tvSwimmerName.text = swimmer.name
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }
        
        btnSetGoal.setOnClickListener { showSetGoalDialog(null) }
        btnEditGoal.setOnClickListener { currentGoal?.let { showSetGoalDialog(it) } }
        btnDeleteGoal.setOnClickListener { showDeleteGoalConfirmation() }
        
        // Setup RecyclerView
        sessionAdapter = SessionListAdapter(sessions) { session, position ->
            displayMetricsForSession(session)
        }
        exerciseListRecycler.layoutManager = LinearLayoutManager(this)
        exerciseListRecycler.adapter = sessionAdapter
    }

    private fun loadData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val teamId = com.thesisapp.utils.AuthManager.currentTeamId(this@CoachSwimmerProfileActivity) ?: return@launch
            
            // Load goal
            currentGoal = db.goalDao().getActiveGoalForSwimmer(swimmer.id, teamId)
            
            // Load sessions for this swimmer
            sessions = db.mlResultDao().getResultsForSwimmer(swimmer.id)
            
            withContext(Dispatchers.Main) {
                updateGoalUI()
                sessionAdapter.updateSessions(sessions)
                
                // Display metrics for latest session by default
                if (sessions.isNotEmpty()) {
                    displayMetricsForSession(sessions[0])
                }
            }
        }
    }

    private fun updateGoalUI() {
        if (currentGoal != null) {
            showGoalExists()
        } else {
            showNoGoal()
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
            tvGoalTitle.text = "Goal: ${goal.eventName} < ${goal.goalTime}"
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            tvGoalDeadline.text = "Deadline: ${dateFormat.format(Date(goal.endDate))}"
            
            // Load and display progress graph
            loadProgressGraph(goal.id)
        }
    }

    private fun loadProgressGraph(goalId: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            val progressPoints = db.goalProgressDao().getProgressForGoal(goalId)
            
            withContext(Dispatchers.Main) {
                if (progressPoints.isEmpty()) {
                    // Show dummy data
                    setupGoalProgressGraph(generateDummyProgress())
                } else {
                    setupGoalProgressGraph(progressPoints)
                }
            }
        }
    }

    private fun generateDummyProgress(): List<GoalProgress> {
        // Generate monthly dummy progress points showing improvement (4 months of data)
        val baseTime = Calendar.getInstance().apply {
            add(Calendar.MONTH, -3) // Start 3 months ago
        }
        
        return listOf(
            GoalProgress(1, currentGoal?.id ?: 0, baseTime.timeInMillis, "1:05.50", null),
            GoalProgress(2, currentGoal?.id ?: 0, baseTime.apply { add(Calendar.MONTH, 1) }.timeInMillis, "1:03.20", null),
            GoalProgress(3, currentGoal?.id ?: 0, baseTime.apply { add(Calendar.MONTH, 1) }.timeInMillis, "1:01.80", null),
            GoalProgress(4, currentGoal?.id ?: 0, baseTime.apply { add(Calendar.MONTH, 1) }.timeInMillis, "0:59.50", null)
        )
    }

    private fun setupGoalProgressGraph(progressPoints: List<GoalProgress>) {
        val entries = progressPoints.mapIndexed { index, point ->
            // Convert time string to float (seconds)
            val timeFloat = timeStringToFloat(point.projectedRaceTime)
            Entry(index.toFloat(), timeFloat)
        }

        val dataSet = LineDataSet(entries, "Projected Time").apply {
            color = getColor(R.color.primary)
            lineWidth = 3f
            setCircleColor(getColor(R.color.primary))
            circleRadius = 6f
            setDrawValues(true) // Show values on dots
            valueTextSize = 10f
            valueTextColor = getColor(R.color.primary)
            mode = LineDataSet.Mode.LINEAR // Use linear to show clear month-to-month progression
            
            // Format values as time strings
            valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                override fun getPointLabel(entry: Entry?): String {
                    entry?.let {
                        val minutes = (it.y.toInt() / 60)
                        val seconds = it.y % 60
                        return String.format("%d:%05.2f", minutes, seconds)
                    }
                    return ""
                }
            }
        }

        goalProgressChart.data = LineData(dataSet)
        
        // Add goal line if we have a goal time
        currentGoal?.let { goal ->
            val goalTimeFloat = timeStringToFloat(goal.goalTime)
            val goalLine = LineDataSet(listOf(
                Entry(0f, goalTimeFloat),
                Entry((progressPoints.size - 1).toFloat(), goalTimeFloat)
            ), "Goal").apply {
                color = getColor(R.color.error)
                lineWidth = 2f
                enableDashedLine(10f, 5f, 0f)
                setDrawCircles(false)
                setDrawValues(false)
            }
            goalProgressChart.data.addDataSet(goalLine)
        }
        
        // Add current time vertical line indicator
        val currentIndex = progressPoints.size - 1 // Latest point is "now"
        val minY = entries.minOfOrNull { it.y } ?: 0f
        val maxY = entries.maxOfOrNull { it.y } ?: 100f
        
        val currentTimeLine = LineDataSet(listOf(
            Entry(currentIndex.toFloat(), minY - 5),
            Entry(currentIndex.toFloat(), maxY + 5)
        ), "Current").apply {
            color = getColor(R.color.accent)
            lineWidth = 2f
            enableDashedLine(5f, 5f, 0f)
            setDrawCircles(false)
            setDrawValues(false)
        }
        goalProgressChart.data.addDataSet(currentTimeLine)
        
        // Setup month labels on X-axis
        val dateFormat = SimpleDateFormat("MMM", Locale.getDefault())
        val monthLabels = progressPoints.map { point ->
            dateFormat.format(Date(point.date))
        }
        
        goalProgressChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            valueFormatter = IndexAxisValueFormatter(monthLabels)
            granularity = 1f
            textSize = 11f
            setDrawGridLines(false)
        }
        
        goalProgressChart.axisLeft.apply {
            textSize = 10f
            setDrawGridLines(true)
        }
        
        goalProgressChart.description.isEnabled = false
        goalProgressChart.axisRight.isEnabled = false
        goalProgressChart.legend.textSize = 12f
        goalProgressChart.animateX(1000)
        goalProgressChart.invalidate()
    }

    private fun timeStringToFloat(timeString: String): Float {
        // Convert "1:02.50" or "0:58.00" to seconds as float
        val parts = timeString.split(":")
        if (parts.size != 2) return 0f
        val minutes = parts[0].toFloatOrNull() ?: 0f
        val seconds = parts[1].toFloatOrNull() ?: 0f
        return minutes * 60 + seconds
    }

    private fun displayMetricsForSession(session: MlResult) {
        // Load exercise details and compute real kinematic metrics from SwimData
        lifecycleScope.launch(Dispatchers.IO) {
            val exercise = session.exerciseId?.let { db.exerciseDao().getExerciseById(it) }

            // Load raw samples for this session
            val swimData = db.swimDataDao().getSwimDataForSession(session.sessionId)

            // Try calling the external Python metrics API first
            var lapsFromApi: List<com.thesisapp.utils.MetricsLapOut>? = null
            var sessionAvgsFromApi: com.thesisapp.utils.MetricsSessionAveragesOut? = null

            if (swimData.isNotEmpty()) {
                try {
                    val samples = swimData.map { d ->
                        MetricsSample(
                            timestamp_ms = d.timestamp,
                            accel_x = (d.accel_x ?: 0f).toDouble(),
                            accel_y = (d.accel_y ?: 0f).toDouble(),
                            accel_z = (d.accel_z ?: 0f).toDouble(),
                            gyro_x = (d.gyro_x ?: 0f).toDouble(),
                            gyro_y = (d.gyro_y ?: 0f).toDouble(),
                            gyro_z = (d.gyro_z ?: 0f).toDouble(),
                            stroke_type = null
                        )
                    }

                    val request = MetricsSessionRequest(
                        session_id = session.sessionId,
                        swimmer_id = session.swimmerId,
                        exercise_id = session.exerciseId,
                        samples = samples
                    )

                    val response = MetricsApiClient.service.computeMetrics(request)
                    lapsFromApi = response.laps
                    sessionAvgsFromApi = response.session_averages

                    Log.d(
                        "MetricsDebug",
                        "API laps stroke_count=" + response.laps.map { it.stroke_count } +
                            ", avgStrokeCount=" + response.session_averages.stroke_count
                    )
                } catch (apiError: Exception) {
                    // Ignore API errors and fall back to on-device pipeline below
                }
            }

            // Decide which metrics source to use.
            // If the API call returned averages (even with zero laps), we treat Python as the
            // single source of truth and use its output. We only fall back to StrokeMetrics
            // when the API call itself failed or there is no swim data.
            val useApi = sessionAvgsFromApi != null

            val lapMetrics: List<StrokeMetrics.LapMetrics>
            val sessionAvgs: StrokeMetrics.SessionAverages?
            val totalStrokeCount: Int
            val totalDistanceMeters: Int

            if (useApi) {
                // Map API laps into a light-weight LapMetrics-like view just for charts/text
                val laps = lapsFromApi.orEmpty()
                val apiAvgs = sessionAvgsFromApi!!

                // We keep StrokeMetrics.SessionAverages type for convenience when formatting
                sessionAvgs = StrokeMetrics.SessionAverages(
                    avgLapTimeSeconds = apiAvgs.avg_lap_time_s,
                    avgStrokeCount = apiAvgs.stroke_count,
                    avgVelocityMetersPerSecond = apiAvgs.avg_velocity_m_per_s,
                    avgStrokeRatePerSecond = apiAvgs.avg_stroke_rate_hz,
                    avgStrokeLengthMeters = apiAvgs.avg_stroke_length_m,
                    avgStrokeIndex = apiAvgs.avg_stroke_index
                )

                lapMetrics = laps.map { m ->
                    StrokeMetrics.LapMetrics(
                        lapTimeSeconds = m.lap_time_s,
                        strokeCount = m.stroke_count,
                        strokeRateSpm = m.stroke_rate_spm,
                        strokeLengthMeters = m.stroke_length_m,
                        velocityMetersPerSecond = m.velocity_m_per_s,
                        strokeRatePerSecond = m.stroke_rate_hz,
                        strokeIndex = m.stroke_index
                    )
                }

                totalStrokeCount = laps.sumOf { it.stroke_count }
                totalDistanceMeters = (50.0 * laps.size).toInt()
            } else {
                // Fallback: on-device StrokeMetrics pipeline
                val rawLapMetrics = if (swimData.isNotEmpty()) {
                    StrokeMetrics.computeLapMetrics(swimData)
                } else {
                    emptyList()
                }

                val effectiveLaps = if (rawLapMetrics.isNotEmpty()) {
                    rawLapMetrics
                } else if (swimData.isNotEmpty()) {
                    val firstTs = swimData.first().timestamp
                    val lastTs = swimData.last().timestamp
                    val totalTimeSeconds = (lastTs - firstTs).coerceAtLeast(1L) / 1000.0
                    val strokeCount = StrokeMetrics.computeStrokeCount(swimData)

                    val poolLengthMeters = 50.0
                    val velocity = if (totalTimeSeconds > 0.0) poolLengthMeters / totalTimeSeconds else 0.0
                    val strokeRatePerSecond = if (totalTimeSeconds > 0.0) strokeCount / totalTimeSeconds else 0.0
                    val strokeRateSpm = strokeRatePerSecond * 60.0
                    val strokeLengthFallback = if (strokeRatePerSecond > 0.0) velocity / strokeRatePerSecond else 0.0
                    val strokeIdx = velocity * strokeLengthFallback

                    listOf(
                        StrokeMetrics.LapMetrics(
                            lapTimeSeconds = totalTimeSeconds,
                            strokeCount = strokeCount,
                            strokeRateSpm = strokeRateSpm,
                            strokeLengthMeters = strokeLengthFallback,
                            velocityMetersPerSecond = velocity,
                            strokeRatePerSecond = strokeRatePerSecond,
                            strokeIndex = strokeIdx
                        )
                    )
                } else {
                    emptyList()
                }

                lapMetrics = effectiveLaps
                sessionAvgs = if (effectiveLaps.isNotEmpty()) {
                    StrokeMetrics.computeSessionAverages(effectiveLaps)
                } else {
                    null
                }

                totalStrokeCount = effectiveLaps.sumOf { it.strokeCount }
                totalDistanceMeters = (50.0 * effectiveLaps.size).toInt()
            }

            withContext(Dispatchers.Main) {
                // Display exercise details in format: "Name - Sets × Distance @ Effort%"
                // e.g., "100m Fast Pace - 6 sets × 100m @ 90%"
                val exerciseDetails = buildString {
                    session.exerciseName?.let { append("$it") } ?: append("Session")

                    // Show sets and distance per set
                    session.sets?.let { sets ->
                        session.distance?.let { distance ->
                            append(" - $sets sets × ${distance}m")
                        }
                    }

                    // Show prescribed effort level from exercise
                    exercise?.effortLevel?.let { append(" @ $it%") }
                }
                tvMetricsTitle.text = exerciseDetails

                if (sessionAvgs != null && lapMetrics.isNotEmpty()) {
                    tvStrokeCount.text = totalStrokeCount.toString()
                    tvStrokeLength.text = String.format("%.2f m", sessionAvgs.avgStrokeLengthMeters)
                    tvDistance.text = "$totalDistanceMeters m"
                    tvStrokeIndex.text = String.format("%.2f", sessionAvgs.avgStrokeIndex)
                    tvLapTime.text = formatTime(sessionAvgs.avgLapTimeSeconds.toFloat())

                    // Build per-lap breakdown similar to HistorySessionActivity
                    val builder = StringBuilder()
                    lapMetrics.forEachIndexed { index, m ->
                        val lapNumber = index + 1
                        if (builder.isNotEmpty()) builder.append('\n')
                        builder.append(
                            "Lap $lapNumber: " +
                                String.format(
                                    Locale.getDefault(),
                                    "time=%.2fs, strokes=%d, v=%.3f m/s, SL=%.3f m, SI=%.3f",
                                    m.lapTimeSeconds,
                                    m.strokeCount,
                                    m.velocityMetersPerSecond,
                                    m.strokeLengthMeters,
                                    m.strokeIndex
                                )
                        )
                    }
                    tvLapBreakdownCoach.text = builder.toString()
                    tvLapBreakdownTitleCoach.visibility = View.VISIBLE
                } else {
                    tvStrokeCount.text = "--"
                    tvStrokeLength.text = "--"
                    tvDistance.text = "--"
                    tvStrokeIndex.text = "--"
                    tvLapTime.text = "--"
                    tvLapBreakdownCoach.text = "No lap metrics available."
                    tvLapBreakdownTitleCoach.visibility = View.VISIBLE
                }

                // Display duration
                val duration = calculateDuration(session.timeStart, session.timeEnd)
                tvDuration.text = duration

                // Use real lap metrics for the performance chart; clear if none
                if (lapMetrics.isNotEmpty()) {
                    setupPerformanceChart(lapMetrics)
                } else {
                    performanceChart.clear()
                }
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
    
    private fun calculateDuration(timeStart: String, timeEnd: String): String {
        return try {
            val parts1 = timeStart.split(":")
            val parts2 = timeEnd.split(":")
            val startMinutes = parts1[0].toInt() * 60 + parts1[1].toInt()
            val endMinutes = parts2[0].toInt() * 60 + parts2[1].toInt()
            val diffMinutes = if (endMinutes >= startMinutes) {
                endMinutes - startMinutes
            } else {
                (24 * 60) - startMinutes + endMinutes
            }
            "${diffMinutes} min"
        } catch (e: Exception) {
            "--"
        }
    }
    
    private fun setupPerformanceChart(lapMetrics: List<StrokeMetrics.LapMetrics>) {
        // Plot real lap times from the metrics pipeline
        val entries = lapMetrics.mapIndexed { index, m ->
            Entry((index + 1).toFloat(), m.lapTimeSeconds.toFloat())
        }
        
        val dataSet = LineDataSet(entries, "Lap Times (s)").apply {
            color = getColor(R.color.primary)
            setCircleColor(getColor(R.color.primary))
            lineWidth = 2f
            circleRadius = 4f
        }
        
        performanceChart.data = LineData(dataSet)
        performanceChart.description.isEnabled = false
        performanceChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            granularity = 1f
            valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
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
        
        // Two separate bars: before and after
        val entries = listOf(
            BarEntry(0f, hrBefore),
            BarEntry(1f, hrAfter)
        )
        
        val dataSet = BarDataSet(entries, "Heart Rate (BPM)").apply {
            colors = listOf(getColor(R.color.accent), getColor(R.color.error))
            valueTextSize = 12f
            valueTextColor = getColor(R.color.text)
        }
        
        heartRateChart.data = BarData(dataSet)
        heartRateChart.description.isEnabled = false
        heartRateChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            granularity = 1f
            valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
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

    private fun setupDummyHeartRateChart() {
        val entries = listOf(
            BarEntry(0f, 90f),
            BarEntry(1f, 150f)
        )
        
        val dataSet = BarDataSet(entries, "Heart Rate (BPM)").apply {
            colors = listOf(getColor(R.color.accent), getColor(R.color.error))
            valueTextSize = 12f
            valueTextColor = getColor(R.color.text)
        }
        
        heartRateChart.data = BarData(dataSet)
        heartRateChart.description.isEnabled = false
        heartRateChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            granularity = 1f
            valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
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

    private fun showSetGoalDialog(existingGoal: Goal?) {
        val teamId = com.thesisapp.utils.AuthManager.currentTeamId(this) ?: return
        
        val dialog = SetGoalDialogFragment.newInstance(swimmer.id, teamId, existingGoal)
        dialog.onGoalSaved = { goal ->
            saveGoal(goal)
        }
        dialog.show(supportFragmentManager, "SetGoalDialog")
    }

    private fun saveGoal(goal: Goal) {
        lifecycleScope.launch(Dispatchers.IO) {
            if (goal.id == 0) {
                // New goal
                db.goalDao().insert(goal)
            } else {
                // Update existing goal
                db.goalDao().update(goal)
            }
            
            // Reload goal
            val teamId = com.thesisapp.utils.AuthManager.currentTeamId(this@CoachSwimmerProfileActivity) ?: return@launch
            currentGoal = db.goalDao().getActiveGoalForSwimmer(swimmer.id, teamId)
            
            withContext(Dispatchers.Main) {
                updateGoalUI()
                Toast.makeText(this@CoachSwimmerProfileActivity, "Goal saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDeleteGoalConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Delete Goal?")
            .setMessage("This will remove the goal and all progress tracking.")
            .setPositiveButton("Delete") { _, _ ->
                deleteGoal()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteGoal() {
        currentGoal?.let { goal ->
            lifecycleScope.launch(Dispatchers.IO) {
                // Delete all progress points
                db.goalProgressDao().deleteAllProgressForGoal(goal.id)
                // Delete goal
                db.goalDao().delete(goal)
                currentGoal = null
                
                withContext(Dispatchers.Main) {
                    updateGoalUI()
                    Toast.makeText(this@CoachSwimmerProfileActivity, "Goal deleted", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
