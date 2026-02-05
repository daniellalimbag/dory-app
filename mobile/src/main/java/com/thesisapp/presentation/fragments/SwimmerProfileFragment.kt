package com.thesisapp.presentation.fragments

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.thesisapp.R
import com.thesisapp.data.non_dao.Swimmer
import com.thesisapp.utils.AuthManager
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter

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
            view.findViewById<ImageView>(R.id.profileImage).setImageResource(R.drawable.profile)

            view.findViewById<TextView>(R.id.swimmerName).text = swimmer.name

            // Display specialty if available
            val tvSpecialty = view.findViewById<TextView>(R.id.swimmerSpecialty)
            if (!swimmer.specialty.isNullOrEmpty()) {
                tvSpecialty.text = swimmer.specialty
                tvSpecialty.visibility = View.VISIBLE
            } else {
                tvSpecialty.visibility = View.GONE
            }

            view.findViewById<TextView>(R.id.swimmerAge).text =
                getString(R.string.years_old, calculateAge(swimmer.birthday))
            view.findViewById<TextView>(R.id.swimmerHeight).text =
                getString(R.string.swimmer_height, swimmer.height)
            view.findViewById<TextView>(R.id.swimmerWeight).text =
                getString(R.string.swimmer_weight, swimmer.weight)
            view.findViewById<TextView>(R.id.swimmerSex).text = swimmer.sex
            view.findViewById<TextView>(R.id.swimmerWingspan).text =
                getString(R.string.swimmer_wingspan, swimmer.wingspan)
            view.findViewById<TextView>(R.id.swimmerCategory).text =
                "Swimmer Type: ${swimmer.category.name}"
        }

        // Logout button
        view.findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            AuthManager.logout(requireContext())
            requireActivity().finish()
        }
    }

    private fun calculateAge(birthday: String): Int {
        return try {
            val birthDate = LocalDate.parse(birthday, DateTimeFormatter.ISO_LOCAL_DATE)
            val currentDate = LocalDate.now()
            Period.between(birthDate, currentDate).years
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