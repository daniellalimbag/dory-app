package com.thesisapp.presentation

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.thesisapp.R
import com.thesisapp.data.Swimmer

class SwimmerProfileFragment : Fragment() {

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
        return inflater.inflate(R.layout.fragment_swimmer_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        swimmer?.let { swimmer ->
            view.findViewById<ImageView>(R.id.profileImage).setImageResource(
                if (swimmer.sex.equals("Male", ignoreCase = true))
                    R.drawable.ic_profile_male
                else
                    R.drawable.ic_profile_female
            )

            view.findViewById<TextView>(R.id.swimmerName).text = swimmer.name
            view.findViewById<TextView>(R.id.swimmerAge).text =
                getString(R.string.years_old, calculateAge(swimmer.birthday))
            view.findViewById<TextView>(R.id.swimmerHeight).text =
                getString(R.string.swimmer_height, swimmer.height)
            view.findViewById<TextView>(R.id.swimmerWeight).text =
                getString(R.string.swimmer_weight, swimmer.weight)
            view.findViewById<TextView>(R.id.swimmerSex).text = swimmer.sex
            view.findViewById<TextView>(R.id.swimmerWingspan).text =
                getString(R.string.swimmer_wingspan, swimmer.wingspan)
        }
    }

    private fun calculateAge(birthday: String): Int {
        return try {
            val birthDate = java.time.LocalDate.parse(birthday, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
            val currentDate = java.time.LocalDate.now()
            java.time.Period.between(birthDate, currentDate).years
        } catch (e: Exception) {
            0
        }
    }

    companion object {
        private const val ARG_SWIMMER = "swimmer"

        fun newInstance(swimmer: Swimmer) = SwimmerProfileFragment().apply {
            arguments = Bundle().apply {
                putParcelable(ARG_SWIMMER, swimmer)
            }
        }
    }
}
