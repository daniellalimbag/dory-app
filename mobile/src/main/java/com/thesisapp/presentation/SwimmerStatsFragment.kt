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

        // Until real stats are implemented, show the no-data state
        noDataLayout.visibility = View.VISIBLE
        statsContent.visibility = View.GONE
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
