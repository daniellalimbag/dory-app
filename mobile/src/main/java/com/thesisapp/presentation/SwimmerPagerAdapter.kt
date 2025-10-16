package com.thesisapp.presentation

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.thesisapp.data.Swimmer

class SwimmerPagerAdapter(
    activity: FragmentActivity,
    private val swimmer: Swimmer
) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> SwimmerStatsFragment.newInstance(swimmer)      // Stats FIRST
            1 -> SwimmerSessionsFragment.newInstance(swimmer)   // Sessions second
            2 -> SwimmerProfileFragment.newInstance(swimmer)    // Profile last
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}