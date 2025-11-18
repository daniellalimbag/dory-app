package com.thesisapp.presentation.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.thesisapp.data.non_dao.Swimmer
import com.thesisapp.presentation.fragments.SwimmerExerciseLibraryFragment
import com.thesisapp.presentation.fragments.SwimmerHomeFragment
import com.thesisapp.presentation.fragments.SwimmerProfileFragment

class SwimmerPagerAdapter(
    activity: FragmentActivity,
    private val swimmer: Swimmer
) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> SwimmerHomeFragment.Companion.newInstance(swimmer)        // Home (core loop)
            1 -> SwimmerExerciseLibraryFragment.Companion.newInstance(swimmer) // Exercise Library
            2 -> SwimmerProfileFragment.Companion.newInstance(swimmer)     // Profile
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}