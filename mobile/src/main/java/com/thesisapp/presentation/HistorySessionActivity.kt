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
    private lateinit var txtStrokeBack: TextView
    private lateinit var txtStrokeBreast: TextView
    private lateinit var txtStrokeFly: TextView
    private lateinit var txtStrokeFree: TextView
    private lateinit var txtSwimmingVelocity: TextView
    private lateinit var txtStrokeRate: TextView
    private lateinit var txtStrokeLength: TextView
    private lateinit var txtStrokeIndex: TextView
    private lateinit var chartEfficiencySrSl: ScatterChart
    private lateinit var chartLapTimeTrend: LineChart
    private lateinit var inputNotes: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.history_session)

        btnReturn = findViewById(R.id.btnReturn)
        txtDate = findViewById(R.id.txtDate)
        txtDuration = findViewById(R.id.txtDuration)
        txtStart = findViewById(R.id.txtStart)
        txtEnd = findViewById(R.id.txtEnd)
        txtStrokeBack = findViewById(R.id.txtStrokeBack)
        txtStrokeBreast = findViewById(R.id.txtStrokeBreast)
        txtStrokeFly = findViewById(R.id.txtStrokeFly)
        txtStrokeFree = findViewById(R.id.txtStrokeFree)
        txtSwimmingVelocity = findViewById(R.id.txtSwimmingVelocity)
        txtStrokeRate = findViewById(R.id.txtStrokeRate)
        txtStrokeLength = findViewById(R.id.txtStrokeLength)
        txtStrokeIndex = findViewById(R.id.txtStrokeIndex)
        chartEfficiencySrSl = findViewById(R.id.chartEfficiencySrSl)
        chartLapTimeTrend = findViewById(R.id.chartLapTimeTrend)
        inputNotes = findViewById(R.id.inputNotes)

        btnReturn.setOnClickListener {
            it.animateClick()
            finish()
        }

        val sessionId = intent.getIntExtra("sessionId", -1)
        if (sessionId != -1) {
            loadSession(sessionId)
        }
    }

    private fun loadSession(sessionId: Int) {
        val db = AppDatabase.getInstance(this)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val mlResult = db.mlResultDao().getBySessionId(sessionId)
                val swimData: List<SwimData> = db.swimDataDao().getSwimDataForSession(sessionId)

                if (mlResult == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@HistorySessionActivity, "Session not found", Toast.LENGTH_LONG).show()
                        finish()
                    }
                    return@launch
                }

                // Compute kinematic metrics if we have swim data
                var swimmingVelocity = 0.0
                var strokeRate = 0.0
                var strokeLength = 0.0
                var strokeIndex = 0.0
                val srSlEntries = ArrayList<Entry>()
                val lapTimeEntries = ArrayList<Entry>()
                val lapTimeSmoothedEntries = ArrayList<Entry>()

                if (swimData.isNotEmpty()) {
                    val lapMetricsRaw = StrokeMetrics.computeLapMetrics(swimData)
                    val poolLengthMeters = 50.0
                    val totalDistanceMeters: Double
                    val totalTimeSeconds: Double

                    if (lapMetricsRaw.isNotEmpty()) {
                        val lapCount = lapMetricsRaw.size.toDouble()
                        totalDistanceMeters = poolLengthMeters * lapCount
                        totalTimeSeconds = lapMetricsRaw.sumOf { it.lapTimeSeconds }
                    } else {
                        val firstTs = swimData.first().timestamp
                        val lastTs = swimData.last().timestamp
                        totalTimeSeconds = (lastTs - firstTs).coerceAtLeast(1L) / 1000.0
                        totalDistanceMeters = poolLengthMeters
                    }

                    val strokeCount = StrokeMetrics.computeStrokeCount(swimData).toDouble()
                    swimmingVelocity = if (totalTimeSeconds > 0.0) totalDistanceMeters / totalTimeSeconds else 0.0
                    strokeRate = if (totalTimeSeconds > 0.0) (strokeCount / totalTimeSeconds) * 60.0 else 0.0
                    strokeLength = if (strokeCount > 0.0) totalDistanceMeters / strokeCount else 0.0
                    strokeIndex = swimmingVelocity * strokeLength

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

                    if (lapMetrics.isNotEmpty()) {
                        lapMetrics.forEachIndexed { index, m ->
                            val lapIdx = index + 1f
                            srSlEntries.add(Entry(m.strokeRateSpm.toFloat(), m.strokeLengthMeters.toFloat()))
                            lapTimeEntries.add(Entry(lapIdx, m.lapTimeSeconds.toFloat()))
                        }

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
                }

                withContext(Dispatchers.Main) {
                    txtDate.text = mlResult.date
                    txtStart.text = mlResult.timeStart
                    txtEnd.text = mlResult.timeEnd

                    txtDuration.text = calculateDuration(mlResult.timeStart, mlResult.timeEnd)

                    txtStrokeBack.text = "${mlResult.backstroke}%"
                    txtStrokeBreast.text = "${mlResult.breaststroke}%"
                    txtStrokeFly.text = "${mlResult.butterfly}%"
                    txtStrokeFree.text = "${mlResult.freestyle}%"

                    if (swimData.isNotEmpty()) {
                        txtSwimmingVelocity.text = String.format("%.2f m/s", swimmingVelocity)
                        txtStrokeRate.text = String.format("%.2f strokes/min", strokeRate)
                        txtStrokeLength.text = String.format("%.2f m/stroke", strokeLength)
                        txtStrokeIndex.text = String.format("%.2f", strokeIndex)
                    } else {
                        txtSwimmingVelocity.text = "-"
                        txtStrokeRate.text = "-"
                        txtStrokeLength.text = "-"
                        txtStrokeIndex.text = "-"
                    }

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

                    inputNotes.setText(mlResult.notes)
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
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@HistorySessionActivity, "Failed to load session: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun calculateDuration(start: String, end: String): String {
        try {
            val format = java.text.SimpleDateFormat("HH:mm:ss")
            val startTime = format.parse(start)
            val endTime = format.parse(end)
            val diff = endTime.time - startTime.time

            val minutes = (diff / 60000).toInt()
            val seconds = (diff % 60000 / 1000).toInt()
            return String.format("%02d:%02d", minutes, seconds)
        } catch (e: Exception) {
            return "-"
        }
    }
}
