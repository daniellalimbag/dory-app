package com.thesisapp.presentation

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.ScatterChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.ScatterData
import com.github.mikephil.charting.data.ScatterDataSet
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.MlResult
import com.thesisapp.data.SwimData
import com.thesisapp.utils.StrokeMetrics
import com.thesisapp.utils.animateClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistorySessionActivity : AppCompatActivity() {

    private lateinit var btnReturn: ImageButton

    private lateinit var txtDate: TextView
    private lateinit var txtDuration: TextView
    private lateinit var txtStart: TextView
    private lateinit var txtEnd: TextView
    private lateinit var txtSwimmingVelocity: TextView
    private lateinit var txtStrokeRate: TextView
    private lateinit var txtStrokeLength: TextView
    private lateinit var txtStrokeIndex: TextView
    private lateinit var inputNotes: EditText
    private lateinit var chartEfficiencySrSl: ScatterChart
    private lateinit var chartLapTimeTrend: LineChart

    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.history_session)

        // Initialize all views and the database instance
        initializeViews()

        btnReturn.setOnClickListener {
            it.animateClick()
            finish()
        }

        // 1. Get Session data from the Intent
        val sessionDate = intent.getStringExtra("EXTRA_SESSION_DATE")
        val sessionTime = intent.getStringExtra("EXTRA_SESSION_TIME")
        val sessionFileName = intent.getStringExtra("EXTRA_SESSION_FILENAME")

        if (sessionDate == null || sessionTime == null || sessionFileName == null) {
            Toast.makeText(this, "Error: Incomplete session data", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // 2. Immediately display the data from the Session object
        displaySessionInfo(sessionDate, sessionTime)

        // 3. Compute kinematic metrics from stored sensor data
        val sessionId = sessionFileName.removeSuffix(".csv").split("_").lastOrNull()?.toIntOrNull()
        if (sessionId != null) {
            computeAndDisplayKinematicMetrics(sessionId)
        }

        // 4. Load the MlResult from the database specifically for dynamic data (notes)
        if (sessionId != null) {
            loadDynamicData(sessionId)
        } else {
            Toast.makeText(this, "Error: Invalid session ID format", Toast.LENGTH_LONG).show()
        }
    }

    private fun initializeViews() {
        btnReturn = findViewById(R.id.btnReturn)
        txtDate = findViewById(R.id.txtDate)
        txtDuration = findViewById(R.id.txtDuration)
        txtStart = findViewById(R.id.txtStart)
        txtEnd = findViewById(R.id.txtEnd)
        txtSwimmingVelocity = findViewById(R.id.txtSwimmingVelocity)
        txtStrokeRate = findViewById(R.id.txtStrokeRate)
        txtStrokeLength = findViewById(R.id.txtStrokeLength)
        txtStrokeIndex = findViewById(R.id.txtStrokeIndex)
        inputNotes = findViewById(R.id.inputNotes)
        chartEfficiencySrSl = findViewById(R.id.chartEfficiencySrSl)
        chartLapTimeTrend = findViewById(R.id.chartLapTimeTrend)
        db = AppDatabase.getInstance(this)
    }

    private fun displaySessionInfo(date: String, time: String) {
        txtDate.text = date
        val timeParts = time.split(" - ")
        if (timeParts.size == 2) {
            val startTime = timeParts[0]
            val endTime = timeParts[1]
            txtStart.text = startTime
            txtEnd.text = endTime
            txtDuration.text = calculateDuration(startTime, endTime)
        } else {
            txtStart.text = time
            txtEnd.text = "-"
            txtDuration.text = "-" // Set a default duration if format is unexpected
        }
    }

    private fun computeAndDisplayKinematicMetrics(sessionId: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val swimData: List<SwimData> = db.swimDataDao().getSwimDataForSession(sessionId)
                if (swimData.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        txtSwimmingVelocity.text = "-"
                        txtStrokeRate.text = "-"
                        txtStrokeLength.text = "-"
                        txtStrokeIndex.text = "-"
                    }
                    return@launch
                }

                // Per-lap metrics using StrokeMetrics pipeline
                val lapMetricsRaw = StrokeMetrics.computeLapMetrics(swimData)

                // Derive session-level totals from lap metrics when available
                val poolLengthMeters = 50.0
                val totalDistanceMeters: Double
                val totalTimeSeconds: Double

                if (lapMetricsRaw.isNotEmpty()) {
                    val lapCount = lapMetricsRaw.size.toDouble()
                    totalDistanceMeters = poolLengthMeters * lapCount
                    totalTimeSeconds = lapMetricsRaw.sumOf { it.lapTimeSeconds }
                } else {
                    // Fallback: use timestamps if lap detection fails
                    val firstTs = swimData.first().timestamp
                    val lastTs = swimData.last().timestamp
                    totalTimeSeconds = (lastTs - firstTs).coerceAtLeast(1L) / 1000.0
                    totalDistanceMeters = poolLengthMeters
                }

                val strokeCount = StrokeMetrics.computeStrokeCount(swimData).toDouble()
                val swimmingVelocity = if (totalTimeSeconds > 0.0) totalDistanceMeters / totalTimeSeconds else 0.0 // m/s
                // Stroke rate in strokes/min, matching stroke_metric_test
                val strokeRate = if (totalTimeSeconds > 0.0) (strokeCount / totalTimeSeconds) * 60.0 else 0.0
                val strokeLength = if (strokeCount > 0.0) totalDistanceMeters / strokeCount else 0.0 // m/stroke

                val strokeIndex = swimmingVelocity * strokeLength

                // For charts: if lap detection failed, approximate a single lap from session metrics
                val lapMetrics = if (lapMetricsRaw.isNotEmpty()) {
                    lapMetricsRaw
                } else {
                    listOf(
                        StrokeMetrics.LapMetrics(
                            lapTimeSeconds = totalTimeSeconds,
                            strokeCount = strokeCount.toInt(),
                            strokeRateSpm = strokeRate,
                            strokeLengthMeters = strokeLength
                        )
                    )
                }

                // Prepare chart data from lap metrics
                val srSlEntries = ArrayList<com.github.mikephil.charting.data.Entry>()
                val lapTimeEntries = ArrayList<com.github.mikephil.charting.data.Entry>()
                val lapTimeSmoothedEntries = ArrayList<com.github.mikephil.charting.data.Entry>()

                if (lapMetrics.isNotEmpty()) {
                    lapMetrics.forEachIndexed { index, m ->
                        val lapIdx = index + 1f
                        // SR vs SL scatter: x = SR (spm), y = SL (m/stroke)
                        srSlEntries.add(Entry(m.strokeRateSpm.toFloat(), m.strokeLengthMeters.toFloat()))

                        // Lap time trend: x = lap index, y = lap time seconds
                        lapTimeEntries.add(Entry(lapIdx, m.lapTimeSeconds.toFloat()))
                    }

                    // Simple rolling average (window 3) for lap times
                    val window = 3
                    val n = lapMetrics.size
                    for (i in 0 until n) {
                        val start = maxOf(0, i - (window - 1))
                        val end = i
                        var sum = 0.0
                        var count = 0
                        for (j in start..end) {
                            sum += lapMetrics[j].lapTimeSeconds
                            count++
                        }
                        val avg = if (count > 0) sum / count else lapMetrics[i].lapTimeSeconds
                        val lapIdx = (i + 1).toFloat()
                        lapTimeSmoothedEntries.add(Entry(lapIdx, avg.toFloat()))
                    }
                }

                withContext(Dispatchers.Main) {
                    // Header metrics
                    txtSwimmingVelocity.text = String.format("%.2f m/s", swimmingVelocity)
                    txtStrokeRate.text = String.format("%.2f strokes/min", strokeRate)
                    txtStrokeLength.text = String.format("%.2f m/stroke", strokeLength)
                    txtStrokeIndex.text = String.format("%.2f", strokeIndex)

                    // SR vs SL scatter chart
                    if (srSlEntries.isNotEmpty()) {
                        val ds = ScatterDataSet(srSlEntries, "Laps").apply {
                            color = resources.getColor(R.color.primary, null)
                            setScatterShape(ScatterChart.ScatterShape.CIRCLE)
                            scatterShapeSize = 8f
                        }
                        chartEfficiencySrSl.data = ScatterData(ds)
                        chartEfficiencySrSl.xAxis.apply {
                            axisMinimum = 0f
                        }
                        chartEfficiencySrSl.axisLeft.axisMinimum = 0f
                        chartEfficiencySrSl.axisRight.isEnabled = false
                        chartEfficiencySrSl.description = Description().apply { text = "" }
                        chartEfficiencySrSl.legend.verticalAlignment = Legend.LegendVerticalAlignment.TOP
                        chartEfficiencySrSl.legend.horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
                        chartEfficiencySrSl.invalidate()
                    } else {
                        chartEfficiencySrSl.clear()
                    }

                    // Lap time trend chart
                    if (lapTimeEntries.isNotEmpty()) {
                        val lapDs = LineDataSet(lapTimeEntries, "Lap Time (s)").apply {
                            color = resources.getColor(R.color.primary, null)
                            lineWidth = 2f
                            setDrawCircles(true)
                            circleRadius = 3f
                            setDrawValues(false)
                        }

                        val smoothDs = LineDataSet(lapTimeSmoothedEntries, "Rolling Avg").apply {
                            color = resources.getColor(R.color.details, null)
                            lineWidth = 2f
                            setDrawCircles(false)
                            setDrawValues(false)
                        }

                        chartLapTimeTrend.data = LineData(lapDs, smoothDs)
                        chartLapTimeTrend.axisLeft.axisMinimum = 0f
                        chartLapTimeTrend.axisRight.isEnabled = false
                        chartLapTimeTrend.xAxis.granularity = 1f
                        chartLapTimeTrend.description = Description().apply { text = "" }
                        chartLapTimeTrend.legend.verticalAlignment = Legend.LegendVerticalAlignment.TOP
                        chartLapTimeTrend.legend.horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
                        chartLapTimeTrend.invalidate()
                    } else {
                        chartLapTimeTrend.clear()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@HistorySessionActivity, "Failed to compute metrics: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadDynamicData(sessionId: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = db.mlResultDao().getBySessionId(sessionId)
            result?.let { mlResult ->
                withContext(Dispatchers.Main) {
                    // --- MODIFICATION: Only update the notes field ---
                    inputNotes.setText(mlResult.notes)

                    // Set up the listener to save notes when focus is lost
                    inputNotes.setOnFocusChangeListener { _, hasFocus ->
                        if (!hasFocus) {
                            val newNotes = inputNotes.text.toString()
                            if (newNotes != mlResult.notes) {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    db.mlResultDao().update(mlResult.copy(notes = newNotes))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun calculateDuration(start: String, end: String): String {
        // This function now correctly calculates duration based on the provided start and end times
        return try {
            val format = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            val startTime = format.parse(start)!!
            val endTime = format.parse(end)!!
            val diff = endTime.time - startTime.time

            val minutes = (diff / 60000).toInt()
            val seconds = (diff % 60000 / 1000).toInt()
            String.format("%02d:%02d", minutes, seconds)
        } catch (e: Exception) {
            "-"
        }
    }
}