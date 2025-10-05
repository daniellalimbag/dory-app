package com.thesisapp.presentation

import android.os.Build
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.thesisapp.R
import com.thesisapp.data.Swimmer

class SwimmerProfileActivity : AppCompatActivity() {

    private lateinit var swimmer: Swimmer
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout

    companion object {
        const val EXTRA_SWIMMER = "extra_swimmer"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_swimmer_profile)

        // Get swimmer from intent (handling deprecated API)
        swimmer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_SWIMMER, Swimmer::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_SWIMMER)
        } ?: run {
            finish()
            return
        }

        // Set up header
        findViewById<TextView>(R.id.tvSwimmerName).text = swimmer.name
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // Set up ViewPager and TabLayout
        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)

        val adapter = SwimmerPagerAdapter(this, swimmer)
        viewPager.adapter = adapter

        // Connect TabLayout with ViewPager2
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_profile)
                1 -> getString(R.string.tab_stats)
                2 -> getString(R.string.tab_sessions)
                else -> ""
            }
        }.attach()
    }
}
