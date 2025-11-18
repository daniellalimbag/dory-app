package com.thesisapp.presentation.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.thesisapp.presentation.fragments.CoachExercisesFragment
import com.thesisapp.presentation.fragments.CoachSwimmersFragment

class CoachPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> CoachSwimmersFragment()
            1 -> CoachExercisesFragment()
            else -> CoachSwimmersFragment()
        }
    }
}