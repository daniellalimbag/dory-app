package com.thesisapp.presentation

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.Swimmer
import com.thesisapp.utils.StrokeMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class SwimmerStatsFragment : Fragment() {

    private var swimmer: Swimmer? = null

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

        val noDataLayout = view.findViewById<LinearLayout>(R.id.noDataLayout)
        val statsContent = view.findViewById<LinearLayout>(R.id.statsContent)
        val btnImport = view.findViewById<Button>(R.id.btnImportFromStats)
        val btnExercises = view.findViewById<Button>(R.id.btnExercisesFromStats)

        val swimmerLocal = swimmer
        if (swimmerLocal == null) {
            noDataLayout.visibility = View.VISIBLE
            statsContent.visibility = View.GONE
            btnImport.visibility = View.GONE
            btnExercises.visibility = View.GONE
            return
        }

        btnImport.setOnClickListener {
            val ctx = requireContext()
            val intent = Intent(ctx, SettingsImportActivity::class.java).apply {
                putExtra(SettingsImportActivity.EXTRA_SWIMMER_ID, swimmerLocal.id)
            }
            startActivity(intent)
        }

        btnExercises.setOnClickListener {
            val ctx = requireContext()
            val intent = Intent(ctx, ExerciseLibraryActivity::class.java)
            startActivity(intent)
        }

        val db = AppDatabase.getInstance(requireContext())

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val results = db.mlResultDao().getResultsForSwimmer(swimmerLocal.id)

                if (results.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        noDataLayout.visibility = View.VISIBLE
                        statsContent.visibility = View.GONE
                    }
                    return@launch
                }

                val twoMonthsAgoMillis = System.currentTimeMillis() - 60L * 24L * 60L * 60L * 1000L
                val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.ENGLISH)

                val recentResults = results.filter { ml ->
                    try {
                        val d = dateFormat.parse(ml.date)
                        d != null && d.time >= twoMonthsAgoMillis
                    } catch (_: Exception) {
                        true
                    }
                }

                if (recentResults.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        noDataLayout.visibility = View.VISIBLE
                        statsContent.visibility = View.GONE
                    }
                    return@launch
                }

                var hrSum = 0.0
                var hrCount = 0

                var sumVelocity = 0.0
                var sumStrokeRatePerSec = 0.0
                var sumStrokeLength = 0.0
                var sumStrokeIndex = 0.0
                var sessionCount = 0

                var sumFree = 0.0
                var sumBack = 0.0
                var sumBreast = 0.0
                var sumFly = 0.0

                for (ml in recentResults) {
                    val swimData = db.swimDataDao().getSwimDataForSession(ml.sessionId)
                    if (swimData.isEmpty()) continue

                    val lapMetricsRaw = StrokeMetrics.computeLapMetrics(swimData)
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

                    if (lapMetrics.isEmpty()) continue

                    val sessionAvgs = StrokeMetrics.computeSessionAverages(lapMetrics)

                    sumVelocity += sessionAvgs.avgVelocityMetersPerSecond
                    sumStrokeRatePerSec += sessionAvgs.avgStrokeRatePerSecond
                    sumStrokeLength += sessionAvgs.avgStrokeLengthMeters
                    sumStrokeIndex += sessionAvgs.avgStrokeIndex
                    sessionCount++

                    // Heart rate from SwimData samples
                    for (s in swimData) {
                        val hr = s.heart_rate
                        if (hr != null && hr > 0f) {
                            hrSum += hr.toDouble()
                            hrCount++
                        }
                    }

                    sumBack += ml.backstroke.toDouble()
                    sumBreast += ml.breaststroke.toDouble()
                    sumFly += ml.butterfly.toDouble()
                    sumFree += ml.freestyle.toDouble()
                }

                withContext(Dispatchers.Main) {
                    if (sessionCount == 0) {
                        noDataLayout.visibility = View.VISIBLE
                        statsContent.visibility = View.GONE
                        return@withContext
                    }

                    noDataLayout.visibility = View.GONE
                    statsContent.visibility = View.VISIBLE

                    val avgVelocity = sumVelocity / sessionCount
                    val avgStrokeRateSpm = (sumStrokeRatePerSec / sessionCount) * 60.0
                    val avgStrokeLength = sumStrokeLength / sessionCount
                    val avgStrokeIndex = sumStrokeIndex / sessionCount

                    val avgHeart = if (hrCount > 0) hrSum / hrCount else 0.0

                    val avgHeartRateView = view.findViewById<TextView>(R.id.avgHeartRate)
                    val heartZoneView = view.findViewById<TextView>(R.id.heartRateZone)
                    val swimVelView = view.findViewById<TextView>(R.id.swimmingVelocity)
                    val strokeRateView = view.findViewById<TextView>(R.id.strokeRate)
                    val strokeLengthView = view.findViewById<TextView>(R.id.strokeLength)
                    val swimIndexView = view.findViewById<TextView>(R.id.swimmingIndex)

                    if (avgHeart > 0.0) {
                        avgHeartRateView.text = getString(R.string.bpm, avgHeart.toInt())
                        val zoneLabel = when {
                            avgHeart < 120 -> getString(R.string.zone_label, 1)
                            avgHeart < 140 -> getString(R.string.zone_label, 2)
                            avgHeart < 160 -> getString(R.string.zone_label, 3)
                            avgHeart < 180 -> getString(R.string.zone_label, 4)
                            else -> getString(R.string.zone_label, 5)
                        }
                        heartZoneView.text = zoneLabel
                    } else {
                        avgHeartRateView.text = "--"
                        heartZoneView.text = "--"
                    }

                    swimVelView.text = getString(R.string.meters_per_sec, avgVelocity.toFloat())
                    strokeRateView.text = getString(R.string.strokes_per_min, avgStrokeRateSpm.toFloat())
                    strokeLengthView.text = getString(R.string.meters_unit, avgStrokeLength.toFloat())
                    swimIndexView.text = getString(R.string.index_unit, avgStrokeIndex.toFloat())

                    val totalStrokePercent = sumFree + sumBack + sumBreast + sumFly
                    val freePct = if (totalStrokePercent > 0) (sumFree / totalStrokePercent * 100.0) else 0.0
                    val backPct = if (totalStrokePercent > 0) (sumBack / totalStrokePercent * 100.0) else 0.0
                    val breastPct = if (totalStrokePercent > 0) (sumBreast / totalStrokePercent * 100.0) else 0.0
                    val flyPct = if (totalStrokePercent > 0) (sumFly / totalStrokePercent * 100.0) else 0.0

                    setStrokeDistribution(view, R.id.freestylePercent, R.id.freestyleProgress, freePct.toFloat())
                    setStrokeDistribution(view, R.id.backstrokePercent, R.id.backstrokeProgress, backPct.toFloat())
                    setStrokeDistribution(view, R.id.breaststrokePercent, R.id.breaststrokeProgress, breastPct.toFloat())
                    setStrokeDistribution(view, R.id.butterflyPercent, R.id.butterflyProgress, flyPct.toFloat())
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    noDataLayout.visibility = View.VISIBLE
                    statsContent.visibility = View.GONE
                }
            }
        }
    }

    private fun setStrokeDistribution(view: View, textId: Int, progressId: Int, percentage: Float) {
        view.findViewById<TextView>(textId).text = "${percentage.toInt()}%"
        view.findViewById<ProgressBar>(progressId).progress = percentage.toInt()
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
