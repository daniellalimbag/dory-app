package com.thesisapp.presentation

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.thesisapp.R
import com.thesisapp.data.Swimmer

class SwimmerStatsFragment : Fragment() {

    private var swimmer: Swimmer? = null

    // Dummy stats data - will be replaced with real data from database later
    private val hasData = true // Toggle this to test no-data state

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

        if (hasData) {
            noDataLayout.visibility = View.GONE
            statsContent.visibility = View.VISIBLE
            populateDummyStats(view)
        } else {
            noDataLayout.visibility = View.VISIBLE
            statsContent.visibility = View.GONE
        }
    }

    private fun populateDummyStats(view: View) {
        // Dummy heart metrics
        view.findViewById<TextView>(R.id.avgHeartRate).text =
            getString(R.string.bpm, 145)
        view.findViewById<TextView>(R.id.heartRateZone).text =
            getString(R.string.zone_label, 3)

        // Dummy swimming metrics
        view.findViewById<TextView>(R.id.swimmingVelocity).text =
            getString(R.string.meters_per_sec, 1.45f)
        view.findViewById<TextView>(R.id.strokeRate).text =
            getString(R.string.strokes_per_min, 32.5f)
        view.findViewById<TextView>(R.id.strokeLength).text =
            getString(R.string.meters_unit, 2.1f)
        view.findViewById<TextView>(R.id.swimmingIndex).text =
            getString(R.string.index_unit, 68.5f)

        // Dummy stroke distribution (percentages should add up to 100)
        setStrokeDistribution(view, R.id.freestylePercent, R.id.freestyleProgress, 45f)
        setStrokeDistribution(view, R.id.backstrokePercent, R.id.backstrokeProgress, 25f)
        setStrokeDistribution(view, R.id.breaststrokePercent, R.id.breaststrokeProgress, 20f)
        setStrokeDistribution(view, R.id.butterflyPercent, R.id.butterflyProgress, 10f)
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
