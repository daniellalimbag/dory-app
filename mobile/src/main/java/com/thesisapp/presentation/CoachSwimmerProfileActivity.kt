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

            var totalStrokeCount = 0
            var avgStrokeLength = 0.0
            var avgStrokeIndex = 0.0
            var avgLapTimeSeconds = 0.0
            var totalDistanceMeters = 0
            var hasMetrics = false

            if (swimData.isNotEmpty()) {
                val lapMetricsRaw = StrokeMetrics.computeLapMetrics(swimData)

                // Fallback: if no laps detected, treat whole session as a single lap
                val lapMetrics = if (lapMetricsRaw.isNotEmpty()) {
                    lapMetricsRaw
                } else {
                    val firstTs = swimData.first().timestamp
                    val lastTs = swimData.last().timestamp
                    val totalTimeSeconds = (lastTs - firstTs).coerceAtLeast(1L) / 1000.0
                    val strokeCount = StrokeMetrics.computeStrokeCount(swimData)

                    val poolLengthMeters = 50.0
                    val velocity = if (totalTimeSeconds > 0.0) poolLengthMeters / totalTimeSeconds else 0.0
                    val strokeRatePerSecond = if (totalTimeSeconds > 0.0) strokeCount / totalTimeSeconds else 0.0
                    val strokeRateSpm = strokeRatePerSecond * 60.0
                    val strokeLength = if (strokeRatePerSecond > 0.0) velocity / strokeRatePerSecond else 0.0
                    val strokeIdx = velocity * strokeLength

                    listOf(
                        StrokeMetrics.LapMetrics(
                            lapTimeSeconds = totalTimeSeconds,
                            strokeCount = strokeCount,
                            strokeRateSpm = strokeRateSpm,
                            strokeLengthMeters = strokeLength,
                            velocityMetersPerSecond = velocity,
                            strokeRatePerSecond = strokeRatePerSecond,
                            strokeIndex = strokeIdx
                        )
                    )
                }

                if (lapMetrics.isNotEmpty()) {
                    val sessionAvgs = StrokeMetrics.computeSessionAverages(lapMetrics)

                    avgStrokeLength = sessionAvgs.avgStrokeLengthMeters
                    avgStrokeIndex = sessionAvgs.avgStrokeIndex
                    avgLapTimeSeconds = sessionAvgs.avgLapTimeSeconds
                    totalStrokeCount = lapMetrics.sumOf { it.strokeCount }

                    val poolLengthMeters = 50.0
                    totalDistanceMeters = (poolLengthMeters * lapMetrics.size).toInt()

                    hasMetrics = true
                }
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

                if (hasMetrics) {
                    tvStrokeCount.text = totalStrokeCount.toString()
                    tvStrokeLength.text = String.format("%.2f m", avgStrokeLength)
                    tvDistance.text = "$totalDistanceMeters m"
                    tvStrokeIndex.text = String.format("%.2f", avgStrokeIndex)
                    tvLapTime.text = formatTime(avgLapTimeSeconds.toFloat())
                } else {
                    tvStrokeCount.text = "--"
                    tvStrokeLength.text = "--"
                    tvDistance.text = "--"
                    tvStrokeIndex.text = "--"
                    tvLapTime.text = "--"
                }

                // Display duration
                val duration = calculateDuration(session.timeStart, session.timeEnd)
                tvDuration.text = duration

                // Keep existing chart behavior for now (dummy if no avgLapTime)
                session.avgLapTime?.let { setupPerformanceChart(session) } ?: setupDummyPerformanceChart()
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
    
    private fun setupPerformanceChart(session: MlResult) {
        // Generate lap times based on average with some variation
        val avgLapTime = session.avgLapTime ?: 35f
        val laps = (session.sets ?: 5) * (session.reps ?: 2)
        
        val entries = (1..laps).map { lap ->
            val variation = randomFloat(-3f, 3f)
            Entry(lap.toFloat(), avgLapTime + variation)
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
    
    private fun randomFloat(min: Float, max: Float): Float {
        return min + java.util.Random().nextFloat() * (max - min)
    }

    private fun setupDummyPerformanceChart() {
        val entries = listOf(
            Entry(1f, 32.5f),
            Entry(2f, 31.8f),
            Entry(3f, 32.2f),
            Entry(4f, 31.5f),
            Entry(5f, 31.0f)
        )
        
        val dataSet = LineDataSet(entries, "Lap Times (s)").apply {
            color = getColor(R.color.primary)
            setCircleColor(getColor(R.color.primary))
            lineWidth = 2f
            circleRadius = 4f
        }
        
        performanceChart.data = LineData(dataSet)
        performanceChart.description.isEnabled = false
        performanceChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        performanceChart.axisRight.isEnabled = false
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
