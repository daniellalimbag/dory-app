package com.thesisapp.presentation.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.non_dao.Swimmer
import com.thesisapp.data.non_dao.Team
import com.thesisapp.data.non_dao.MlResult
import com.thesisapp.presentation.adapters.SwimmerPagerAdapter
import com.thesisapp.utils.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SwimmerProfileActivity : AppCompatActivity() {

    private lateinit var swimmer: Swimmer
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var tvTeamSwitcher: TextView
    private lateinit var tvAccount: TextView
    private lateinit var db: AppDatabase

    companion object {
        const val EXTRA_SWIMMER = "extra_swimmer"
        const val EXTRA_SWIMMER_ID = "extra_swimmer_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_swimmer_profile)

        db = AppDatabase.getInstance(this)

        // Get swimmer from intent (handling deprecated API)
        val swimmerFromExtra = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_SWIMMER, Swimmer::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_SWIMMER)
        }

        if (swimmerFromExtra != null) {
            swimmer = swimmerFromExtra
            initUi()
            return
        }

        val swimmerId = intent.getIntExtra(EXTRA_SWIMMER_ID, -1)
        if (swimmerId <= 0) {
            finish()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val loaded = db.swimmerDao().getById(swimmerId)
            withContext(Dispatchers.Main) {
                if (loaded == null) {
                    finish()
                    return@withContext
                }
                swimmer = loaded
                initUi()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::tvTeamSwitcher.isInitialized) {
            updateTopRow()
        }
    }

    private fun initUi() {
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

        // Connect TabLayout with ViewPager2 - Home, Exercise Library, Profile
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Home"                             // Home (core loop)
                1 -> "Exercises"                        // Exercise Library
                2 -> getString(R.string.tab_profile)    // Profile
                else -> ""
            }
        }.attach()
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
                                        val intent = Intent(this@SwimmerProfileActivity, SwimmerProfileActivity::class.java).apply {
                                            putExtra(EXTRA_SWIMMER_ID, newSwimmer.id)
                                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                        }
                                        startActivity(intent)
                                        finish()
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
                    Toast.makeText(this@SwimmerProfileActivity, "No teams", Toast.LENGTH_SHORT).show()
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
        val items = arrayOf("Create Test Session", "Logout")
        AlertDialog.Builder(this)
            .setTitle("Account")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        // Create test session
                        createTestSession()
                    }
                    1 -> {
                        AuthManager.logout(this)
                        startActivity(Intent(this, AuthActivity::class.java))
                        finish()
                    }
                }
            }
            .show()
    }

    private fun createTestSession() {
        lifecycleScope.launch(Dispatchers.IO) {
            val currentTime = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val timeNow = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            
            val testSession = MlResult(
                sessionId = 0,
                swimmerId = swimmer.id,
                exerciseId = null,
                date = currentTime,
                timeStart = timeNow,
                timeEnd = timeNow,
                exerciseName = "Uncategorized Session",
                distance = 100,
                sets = 4,
                reps = 1,
                effortLevel = "Moderate",
                strokeCount = 40,
                avgStrokeLength = 2.5f,
                strokeIndex = 3.75f,
                avgLapTime = 30.0f,
                totalDistance = 100,
                heartRateBefore = 70,
                heartRateAfter = 140,
                avgHeartRate = 130,
                maxHeartRate = 150,
                backstroke = 0f,
                breaststroke = 0f,
                butterfly = 0f,
                freestyle = 100f,
                notes = "Auto-generated test session"
            )
            db.mlResultDao().insert(testSession)
            
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@SwimmerProfileActivity,
                    "Test session created! Check Home tab.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}