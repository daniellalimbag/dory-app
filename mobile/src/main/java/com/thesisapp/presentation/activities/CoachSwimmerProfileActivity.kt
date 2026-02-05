package com.thesisapp.presentation.activities

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
import androidx.viewpager2.widget.ViewPager2
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.thesisapp.R
import com.thesisapp.data.*
import com.thesisapp.presentation.adapters.LapChartsPagerAdapter
import com.thesisapp.presentation.adapters.SessionListAdapter
import com.thesisapp.presentation.fragments.SetGoalDialogFragment
import com.thesisapp.utils.StrokeMetrics
import com.thesisapp.utils.MetricsApiClient
import com.thesisapp.utils.MetricsSample
import com.thesisapp.utils.MetricsSessionRequest
import android.util.Log
import android.view.Gravity
import android.widget.TableLayout
import android.widget.TableRow
import com.github.mikephil.charting.components.YAxis
import com.thesisapp.data.non_dao.Goal
import com.thesisapp.data.non_dao.GoalProgress
import com.thesisapp.data.non_dao.MlResult
import com.thesisapp.data.non_dao.Swimmer
import com.thesisapp.utils.AuthManager
import com.thesisapp.utils.MetricsLapOut
import com.thesisapp.utils.MetricsSessionAveragesOut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

import com.thesisapp.data.repository.SwimSessionsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CoachSwimmerProfileActivity : AppCompatActivity() {

    @Inject
    lateinit var swimSessionsRepository: SwimSessionsRepository

    private lateinit var db: AppDatabase
    private lateinit var swimmer: Swimmer
    private var currentGoal: Goal? = null
    private var sessions: List<MlResult> = listOf()
    // Views
    private lateinit var tvSwimmerName: TextView
    private lateinit var tvSwimmerSpecialtyCoach: TextView
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
    private lateinit var velocityChart: LineChart
    private lateinit var heartRateChart: BarChart
    private lateinit var tvStrokeCount: TextView
    private lateinit var tvStrokeLength: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvDuration: TextView
    private lateinit var tvStrokeIndex: TextView
    private lateinit var tvLapTime: TextView
    private lateinit var tvLapBreakdownTitleCoach: TextView
    private lateinit var tableLapBreakdownCoach: TableLayout
    private lateinit var tvLapBreakdownEmptyCoach: TextView
    private lateinit var tvSessionDrilldownTitle: TextView
    private lateinit var toggleLapChartMode: MaterialButtonToggleGroup
    private lateinit var tabLapCharts: TabLayout
    private lateinit var pagerLapCharts: ViewPager2
    private var lapChartsMediator: TabLayoutMediator? = null
    private var lastLapMetrics: List<StrokeMetrics.LapMetrics> = emptyList()
    private var lapChartMode: LapChartsPagerAdapter.ChartMode = LapChartsPagerAdapter.ChartMode.REGULAR
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
        tvSwimmerSpecialtyCoach = findViewById(R.id.tvSwimmerSpecialtyCoach)
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
        velocityChart = findViewById(R.id.velocityChart)
        heartRateChart = findViewById(R.id.heartRateChart)
        tvStrokeCount = findViewById(R.id.tvStrokeCount)
        tvStrokeLength = findViewById(R.id.tvStrokeLength)
        tvDistance = findViewById(R.id.tvDistance)
        tvDuration = findViewById(R.id.tvDuration)
        tvStrokeIndex = findViewById(R.id.tvStrokeIndex)
        tvLapTime = findViewById(R.id.tvLapTime)
        tvLapBreakdownTitleCoach = findViewById(R.id.tvLapBreakdownTitleCoach)
        tableLapBreakdownCoach = findViewById(R.id.tableLapBreakdownCoach)
        tvLapBreakdownEmptyCoach = findViewById(R.id.tvLapBreakdownEmptyCoach)
        tvSessionDrilldownTitle = findViewById(R.id.tvSessionDrilldownTitle)
        toggleLapChartMode = findViewById(R.id.toggleLapChartMode)
        tabLapCharts = findViewById(R.id.tabLapCharts)
        pagerLapCharts = findViewById(R.id.pagerLapCharts)
        
        exerciseListRecycler = findViewById(R.id.exerciseListRecycler)
        
        tvSwimmerName.text = swimmer.name

        // Set specialty if present
        if (!swimmer.specialty.isNullOrEmpty()) {
            tvSwimmerSpecialtyCoach.text = swimmer.specialty
            tvSwimmerSpecialtyCoach.visibility = View.VISIBLE
        } else {
            tvSwimmerSpecialtyCoach.visibility = View.GONE
        }
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

        toggleLapChartMode.check(R.id.btnLapChartRegular)
        toggleLapChartMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener

            lapChartMode = when (checkedId) {
                R.id.btnLapChartDelta -> LapChartsPagerAdapter.ChartMode.DELTA_VS_LAP1
                else -> LapChartsPagerAdapter.ChartMode.REGULAR
            }

            if (lastLapMetrics.size > 1) {
                setupLapChartsPager(lastLapMetrics)
            }
        }
    }

    private fun loadData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val teamId = AuthManager.currentTeamId(this@CoachSwimmerProfileActivity) ?: return@launch
            
            // Load goal
            currentGoal = db.goalDao().getActiveGoalForSwimmer(swimmer.id, teamId)
            
            // Load sessions for this swimmer (prefer Supabase, fall back to Room)
            sessions = runCatching {
                swimSessionsRepository.getSessionsForSwimmer(swimmer.id.toLong())
            }.getOrElse {
                db.mlResultDao().getResultsForSwimmer(swimmer.id)
            }
            
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
                    goalProgressChart.clear()
                    goalProgressChart.invalidate()
                } else {
                    setupGoalProgressGraph(progressPoints)
                }
            }
        }
    }

    private fun setupGoalProgressGraph(progressPoints: List<GoalProgress>) {
        val axisTextColor = getColor(R.color.text)
        val secondaryTextColor = getColor(R.color.text_secondary)

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
            valueTextColor = axisTextColor
            mode = LineDataSet.Mode.LINEAR // Use linear to show clear month-to-month progression
            
            // Format values as time strings
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val minutes = (value.toInt() / 60)
                    val seconds = value % 60
                    return String.format(Locale.getDefault(), "%d:%05.2f", minutes, seconds)
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
            textColor = secondaryTextColor
            setDrawGridLines(false)
        }
        
        goalProgressChart.axisLeft.apply {
            textSize = 10f
            textColor = secondaryTextColor
            setDrawGridLines(true)
        }
        
        goalProgressChart.description.isEnabled = false
        goalProgressChart.axisRight.isEnabled = false
        goalProgressChart.legend.apply {
            textSize = 12f
            textColor = axisTextColor
        }
        goalProgressChart.setNoDataTextColor(axisTextColor)
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
            val localSwimData = runCatching {
                db.swimDataDao().getSwimDataForSession(session.sessionId)
            }.getOrDefault(emptyList())

            val swimData = if (localSwimData.isNotEmpty()) {
                localSwimData
            } else {
                runCatching {
                    swimSessionsRepository.getSwimDataForSession(session.sessionId)
                }.getOrDefault(emptyList())
            }

            // Try calling the external Python metrics API first
            var lapsFromApi: List<MetricsLapOut>? = null
            var sessionAvgsFromApi: MetricsSessionAveragesOut? = null

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
                    // Use session averages computed by the Python pipeline
                    tvStrokeCount.text = String.format("%.0f", sessionAvgs.avgStrokeCount)
                    tvStrokeLength.text = String.format("%.2f m", sessionAvgs.avgStrokeLengthMeters)
                    // Lap count from number of laps
                    tvDistance.text = lapMetrics.size.toString()
                    // Velocity from session averages
                    tvDuration.text = String.format("%.2f m/s", sessionAvgs.avgVelocityMetersPerSecond)
                    tvStrokeIndex.text = String.format("%.2f", sessionAvgs.avgStrokeIndex)
                    tvLapTime.text = formatTime(sessionAvgs.avgLapTimeSeconds.toFloat())

                    // Populate per-lap breakdown table
                    tableLapBreakdownCoach.removeViews(1, maxOf(0, tableLapBreakdownCoach.childCount - 1))

                    // Compute simple performance bands for heat-style coloring based on lap time
                    val lapTimes = lapMetrics.map { it.lapTimeSeconds }
                    val minLap = lapTimes.minOrNull() ?: 0.0
                    val maxLap = lapTimes.maxOrNull() ?: 0.0

                    lapMetrics.forEachIndexed { index, m ->
                        val row = TableRow(this@CoachSwimmerProfileActivity)

                        fun makeCell(text: String, center: Boolean = true): TextView {
                            return TextView(this@CoachSwimmerProfileActivity).apply {
                                this.text = text
                                textSize = 12f
                                setTextColor(getColor(R.color.text))
                                if (center) {
                                    gravity = Gravity.CENTER
                                }
                            }
                        }

                        row.addView(makeCell("${index + 1}"))
                        row.addView(makeCell(String.format(Locale.getDefault(), "%.2f", m.lapTimeSeconds)))
                        row.addView(makeCell(m.strokeCount.toString()))
                        row.addView(makeCell(String.format(Locale.getDefault(), "%.3f", m.strokeLengthMeters)))
                        row.addView(makeCell(String.format(Locale.getDefault(), "%.3f", m.velocityMetersPerSecond)))
                        row.addView(makeCell(String.format(Locale.getDefault(), "%.3f", m.strokeIndex)))

                        // Apply background color based on lap time relative to session range
                        if (maxLap > minLap) {
                            val normalized = ((m.lapTimeSeconds - minLap) / (maxLap - minLap)).coerceIn(0.0, 1.0)
                            val bgColor = when {
                                normalized < 0.33 -> getColor(R.color.primary) // fastest laps
                                normalized > 0.66 -> getColor(R.color.error)   // slowest laps
                                else -> getColor(R.color.background)          // middle
                            }
                            row.setBackgroundColor(bgColor)
                        }

                        tableLapBreakdownCoach.addView(row)
                    }

                    tvLapBreakdownTitleCoach.visibility = View.VISIBLE
                    tableLapBreakdownCoach.visibility = View.VISIBLE
                    tvLapBreakdownEmptyCoach.visibility = View.GONE

                    if (lapMetrics.size > 1) {
                        tvSessionDrilldownTitle.visibility = View.VISIBLE
                        toggleLapChartMode.visibility = View.VISIBLE
                        tabLapCharts.visibility = View.VISIBLE
                        pagerLapCharts.visibility = View.VISIBLE
                    } else {
                        tvSessionDrilldownTitle.visibility = View.GONE
                        toggleLapChartMode.visibility = View.GONE
                        tabLapCharts.visibility = View.GONE
                        pagerLapCharts.visibility = View.GONE
                    }
                } else {
                    tvStrokeCount.text = "--"
                    tvStrokeLength.text = "--"
                    tvDistance.text = "--"
                    tvStrokeIndex.text = "--"
                    tvLapTime.text = "--"
                    tableLapBreakdownCoach.removeViews(1, maxOf(0, tableLapBreakdownCoach.childCount - 1))
                    tvLapBreakdownTitleCoach.visibility = View.VISIBLE
                    tableLapBreakdownCoach.visibility = View.GONE
                    tvLapBreakdownEmptyCoach.visibility = View.VISIBLE
                    tvSessionDrilldownTitle.visibility = View.GONE
                    toggleLapChartMode.visibility = View.GONE
                    tabLapCharts.visibility = View.GONE
                    pagerLapCharts.visibility = View.GONE
                }

                // Display duration
                val duration = calculateDuration(session.timeStart, session.timeEnd)
                tvDuration.text = duration

                // Use real lap metrics for the performance charts and drilldown chart; clear if none
                if (lapMetrics.isNotEmpty()) {
                    lastLapMetrics = lapMetrics
                    setupPerformanceChart(lapMetrics)
                    setupVelocityChart(lapMetrics)
                    setupLapChartsPager(lapMetrics)
                } else {
                    performanceChart.clear()
                    velocityChart.clear()
                    lapChartsMediator?.detach()
                    lapChartsMediator = null
                    pagerLapCharts.adapter = null
                    lastLapMetrics = emptyList()
                }
                setupHeartRateChart(session)
            }
        }
    }

    private fun setupLapChartsPager(lapMetrics: List<StrokeMetrics.LapMetrics>) {
        if (lapMetrics.size <= 1) {
            lapChartsMediator?.detach()
            lapChartsMediator = null
            pagerLapCharts.adapter = null
            toggleLapChartMode.visibility = View.GONE
            tabLapCharts.visibility = View.GONE
            pagerLapCharts.visibility = View.GONE
            return
        }

        val adapter = LapChartsPagerAdapter(
            context = this,
            lapMetrics = lapMetrics,
            chartMode = lapChartMode
        )
        pagerLapCharts.adapter = adapter

        lapChartsMediator?.detach()
        lapChartsMediator = TabLayoutMediator(tabLapCharts, pagerLapCharts) { tab, position ->
            tab.text = adapter.getTabTitle(position)
        }
        lapChartsMediator?.attach()

        toggleLapChartMode.visibility = View.VISIBLE
        tabLapCharts.visibility = View.VISIBLE
        pagerLapCharts.visibility = View.VISIBLE
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
        val axisTextColor = getColor(R.color.text)
        val secondaryTextColor = getColor(R.color.text_secondary)

        // Plot ONLY lap time on a single-axis chart
        val timeEntries = lapMetrics.mapIndexed { index, m ->
            Entry((index + 1).toFloat(), m.lapTimeSeconds.toFloat())
        }

        val timeDataSet = LineDataSet(timeEntries, "Lap Time (s)").apply {
            color = getColor(R.color.primary)
            setCircleColor(getColor(R.color.primary))
            lineWidth = 2.5f
            circleRadius = 4f
            setDrawValues(false)
            setDrawCircleHole(false)
            axisDependency = YAxis.AxisDependency.LEFT
        }

        performanceChart.data = LineData(timeDataSet)
        performanceChart.description.isEnabled = false
        performanceChart.setTouchEnabled(true)
        performanceChart.isDragEnabled = true
        performanceChart.setScaleEnabled(false)
        performanceChart.legend.apply {
            isEnabled = true
            textSize = 11f
            textColor = axisTextColor
        }

        performanceChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            granularity = 1f
            textSize = 11f
            textColor = secondaryTextColor
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val lapIndex = value.toInt()
                    return if (lapIndex >= 1 && lapIndex <= lapMetrics.size) "Lap $lapIndex" else ""
                }
            }
        }
        performanceChart.axisLeft.apply {
            setDrawGridLines(true)
            granularity = 2f
            textSize = 11f
            textColor = secondaryTextColor
        }
        performanceChart.axisRight.isEnabled = false
        performanceChart.setNoDataTextColor(axisTextColor)
        performanceChart.animateX(800)
        performanceChart.invalidate()
    }

    private fun setupVelocityChart(lapMetrics: List<StrokeMetrics.LapMetrics>) {
        val axisTextColor = getColor(R.color.text)
        val secondaryTextColor = getColor(R.color.text_secondary)

        val velocityEntries = lapMetrics.mapIndexed { index, m ->
            Entry((index + 1).toFloat(), m.velocityMetersPerSecond.toFloat())
        }

        val velocityDataSet = LineDataSet(velocityEntries, "Velocity (m/s)").apply {
            color = getColor(R.color.error)
            setCircleColor(getColor(R.color.error))
            lineWidth = 2.5f
            circleRadius = 4f
            setDrawValues(false)
            setDrawCircleHole(false)
            axisDependency = YAxis.AxisDependency.LEFT
        }

        velocityChart.data = LineData(velocityDataSet)
        velocityChart.description.isEnabled = false
        velocityChart.setTouchEnabled(true)
        velocityChart.isDragEnabled = true
        velocityChart.setScaleEnabled(false)
        velocityChart.legend.apply {
            isEnabled = true
            textSize = 11f
            textColor = axisTextColor
        }

        velocityChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            granularity = 1f
            textSize = 11f
            textColor = secondaryTextColor
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val lapIndex = value.toInt()
                    return if (lapIndex >= 1 && lapIndex <= lapMetrics.size) "Lap $lapIndex" else ""
                }
            }
        }
        velocityChart.axisLeft.apply {
            setDrawGridLines(true)
            granularity = 0.1f
            textSize = 11f
            textColor = secondaryTextColor
        }
        velocityChart.axisRight.isEnabled = false
        velocityChart.setNoDataTextColor(axisTextColor)
        velocityChart.animateX(800)
        velocityChart.invalidate()
    }
    
    private fun setupHeartRateChart(session: MlResult) {
        val axisTextColor = getColor(R.color.text)
        val secondaryTextColor = getColor(R.color.text_secondary)

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
            valueTextColor = axisTextColor
            setDrawValues(true)
        }

        heartRateChart.data = BarData(dataSet).apply {
            barWidth = 0.4f
        }
        heartRateChart.description.isEnabled = false
        heartRateChart.setTouchEnabled(true)
        heartRateChart.setScaleEnabled(false)

        heartRateChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            granularity = 1f
            textSize = 11f
            textColor = secondaryTextColor
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
            granularity = 10f
            textSize = 11f
            setDrawGridLines(true)
            textColor = secondaryTextColor
        }
        heartRateChart.legend.isEnabled = false
        heartRateChart.setNoDataTextColor(axisTextColor)
        heartRateChart.animateY(700)
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

    private fun showSetGoalDialog(existingGoal: Goal?) {
        val teamId = AuthManager.currentTeamId(this) ?: return
        
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
            val teamId = AuthManager.currentTeamId(this@CoachSwimmerProfileActivity) ?: return@launch
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