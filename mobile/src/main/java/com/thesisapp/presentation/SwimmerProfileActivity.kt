package com.thesisapp.presentation

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.Swimmer
import com.thesisapp.data.Team
import com.thesisapp.utils.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SwimmerProfileActivity : AppCompatActivity() {

    private lateinit var swimmer: Swimmer
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var tvTeamSwitcher: TextView
    private lateinit var tvAccount: TextView
    private lateinit var db: AppDatabase

    companion object {
        const val EXTRA_SWIMMER = "extra_swimmer"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_swimmer_profile)

        db = AppDatabase.getInstance(this)

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

        // Set up top row
        tvTeamSwitcher = findViewById(R.id.tvTeamSwitcher)
        tvAccount = findViewById(R.id.tvAccount)

        tvTeamSwitcher.setOnClickListener { showSwitchTeamDialog() }
        tvAccount.setOnClickListener { showAccountMenu() }

        updateTopRow()

        // Set up ViewPager and TabLayout
        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)

        val adapter = SwimmerPagerAdapter(this, swimmer)
        viewPager.adapter = adapter

        // Connect TabLayout with ViewPager2 - STATS first, then Sessions, then Profile
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_stats)      // Stats FIRST (most important)
                1 -> getString(R.string.tab_sessions)   // Sessions second
                2 -> getString(R.string.tab_profile)    // Profile last
                else -> ""
            }
        }.attach()
    }

    override fun onResume() {
        super.onResume()
        updateTopRow()
    }

    private fun updateTopRow() {
        lifecycleScope.launch(Dispatchers.IO) {
            val user = AuthManager.currentUser(this@SwimmerProfileActivity)
            val teamId = AuthManager.currentTeamId(this@SwimmerProfileActivity)
            val teamName = if (teamId != null) db.teamDao().getById(teamId)?.name else null
            withContext(Dispatchers.Main) {
                tvTeamSwitcher.text = (teamName ?: "Select team") + " \u25BC"
                tvAccount.text = user?.email?.let { "$it \u25BC" } ?: "Guest"
            }
        }
    }

    private fun showSwitchTeamDialog() {
        val user = AuthManager.currentUser(this) ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val teamIds = AuthManager.getSwimmerTeams(this@SwimmerProfileActivity, user.email)
            val teams: List<Team> = teamIds.mapNotNull { db.teamDao().getById(it) }
            withContext(Dispatchers.Main) {
                val baseNames = teams.map { it.name }.toMutableList()
                val actions = mutableListOf<() -> Unit>()
                teams.forEach { team ->
                    actions.add {
                        AuthManager.setCurrentTeamId(this@SwimmerProfileActivity, team.id)
                        // Reload the swimmer profile for the new team
                        val swimmerId = AuthManager.getLinkedSwimmerId(this@SwimmerProfileActivity, user.email, team.id)
                        if (swimmerId != null) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                val newSwimmer = db.swimmerDao().getById(swimmerId)
                                withContext(Dispatchers.Main) {
                                    if (newSwimmer != null) {
                                        swimmer = newSwimmer
                                        recreate() // Refresh the activity with new swimmer data
                                    }
                                }
                            }
                        }
                    }
                }
                // Add option to enroll in another team
                baseNames.add("+ Enroll in Another Team")
                actions.add { startActivity(Intent(this@SwimmerProfileActivity, EnrollViaCodeActivity::class.java)) }

                if (baseNames.isEmpty()) {
                    android.widget.Toast.makeText(this@SwimmerProfileActivity, "No teams", android.widget.Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                AlertDialog.Builder(this@SwimmerProfileActivity)
                    .setTitle("Select Team")
                    .setItems(baseNames.toTypedArray()) { _, which -> actions[which].invoke() }
                    .show()
            }
        }
    }

    private fun showAccountMenu() {
        val items = arrayOf("Logout")
        AlertDialog.Builder(this)
            .setTitle("Account")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        AuthManager.logout(this)
                        startActivity(Intent(this, AuthActivity::class.java))
                        finish()
                    }
                }
            }
            .show()
    }
}