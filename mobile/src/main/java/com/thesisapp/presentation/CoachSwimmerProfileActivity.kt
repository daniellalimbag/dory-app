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
        tvMetricsTitle.text = "Metrics for: ${session.date} at ${session.timeStart}"
        
        // TODO: Load actual session data and display metrics
        // For now, show placeholder values
        tvStrokeCount.text = "--"
        tvStrokeLength.text = "--"
        tvDistance.text = "-- m"
        tvDuration.text = session.timeEnd
        
        // Setup charts with dummy data (will be replaced with real data)
        setupDummyPerformanceChart()
        setupDummyHeartRateChart()
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

    private fun setupDummyHeartRateChart() {
        val entries = listOf(
            BarEntry(0f, floatArrayOf(120f, 165f)), // Before, After
            BarEntry(1f, floatArrayOf(125f, 170f)),
            BarEntry(2f, floatArrayOf(122f, 168f))
        )
        
        val dataSet = BarDataSet(entries, "Heart Rate").apply {
            colors = listOf(getColor(R.color.accent), getColor(R.color.error))
            stackLabels = arrayOf("Before", "After")
        }
        
        heartRateChart.data = BarData(dataSet)
        heartRateChart.description.isEnabled = false
        heartRateChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        heartRateChart.axisRight.isEnabled = false
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
