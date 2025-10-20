package com.thesisapp.presentation

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.card.MaterialCardView
import com.google.android.material.button.MaterialButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.Wearable
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.Team
import com.thesisapp.utils.AuthManager
import com.thesisapp.utils.UserRole
import com.thesisapp.utils.animateClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var btnConnect: Button
    private lateinit var btnSwimmers: MaterialCardView
    private lateinit var btnExercises: MaterialCardView
    private lateinit var btnSessions: MaterialCardView
    private lateinit var btnEnrollSwimmer: MaterialButton
    private lateinit var tvSwimmerCount: TextView
    private lateinit var tvSessionCount: TextView
    private var isSmartwatchConnected = false
    private lateinit var db: AppDatabase
    private lateinit var tvTeamSwitcher: TextView
    private lateinit var tvAccount: TextView

    // Empty state views
    private lateinit var emptyContainer: View
    private lateinit var tvEmptyTitle: TextView
    private lateinit var btnEmptyPrimary: MaterialButton
    private lateinit var btnEmptySecondary: MaterialButton

    // Dummy data for UI
    private var swimmerCount = 5
    private val sessionCount = 12

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_dashboard)

        tvTeamSwitcher = findViewById(R.id.tvTeamSwitcher)
        tvAccount = findViewById(R.id.tvAccount)

        // Empty state
        emptyContainer = findViewById(R.id.emptyStateContainer)
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle)
        btnEmptyPrimary = findViewById(R.id.btnEmptyPrimary)
        btnEmptySecondary = findViewById(R.id.btnEmptySecondary)

        btnConnect = findViewById(R.id.btnConnect)
        btnSwimmers = findViewById(R.id.btnSwimmers)
        btnExercises = findViewById(R.id.btnExercises)
        btnSessions = findViewById(R.id.btnSessions)
        btnEnrollSwimmer = findViewById(R.id.btnEnrollSwimmer)
        tvSwimmerCount = findViewById(R.id.tvSwimmerCount)
        tvSessionCount = findViewById(R.id.tvSessionCount)

        db = AppDatabase.getInstance(applicationContext)

        // Set dummy data
        updateCounts()

        updateSmartwatchButton()

        tvTeamSwitcher.setOnClickListener { showSwitchTeamDialog(withCreateOption = true) }
        tvAccount.setOnClickListener { showAccountMenu() }

        btnConnect.setOnClickListener {
            it.animateClick()
            startActivity(Intent(this, ConnectActivity::class.java))
        }

        btnSwimmers.setOnClickListener {
            it.animateClick()
            val user = AuthManager.currentUser(this)
            if (user?.role == UserRole.SWIMMER) {
                // Swimmer: open Coaches list
                startActivity(Intent(this, com.thesisapp.presentation.swimmer.CoachesActivity::class.java))
            } else {
                startActivity(Intent(this, SwimmersActivity::class.java))
            }
        }

        btnExercises.setOnClickListener {
            it.animateClick()
            startActivity(Intent(this, com.thesisapp.presentation.exercises.ExercisesActivity::class.java))
        }

        btnSessions.setOnClickListener {
            it.animateClick()
            startActivity(Intent(this, HistoryListActivity::class.java))
        }

        btnEnrollSwimmer.setOnClickListener {
            it.animateClick()
            val role = AuthManager.currentUser(this)?.role
            if (role == UserRole.COACH) {
                startActivity(Intent(this, TrackAddSwimmerActivity::class.java))
            } else {
                startActivity(Intent(this, EnrollViaCodeActivity::class.java))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh swimmer count when returning to this activity
        loadSwimmerCount()
        // Check smartwatch connection
        checkSmartwatchConnection()
        updateTopRow()
        updateEmptyState()
        updateButtonText()
    }

    private fun updateButtonText() {
        val role = AuthManager.currentUser(this)?.role
        if (role == UserRole.COACH) {
            btnEnrollSwimmer.text = getString(R.string.enroll_swimmer)
        } else {
            btnEnrollSwimmer.text = getString(R.string.enroll_in_team)
        }
    }

    private fun updateEmptyState() {
        val user = AuthManager.currentUser(this)
        if (user == null) return
        val hasTeams = when (user.role) {
            UserRole.COACH -> AuthManager.getCoachTeams(this, user.email).isNotEmpty()
            UserRole.SWIMMER -> AuthManager.currentTeamId(this) != null || AuthManager.getSwimmerTeams(this, user.email).isNotEmpty()
        }
        if (hasTeams) {
            // Show dashboard for both roles; swimmers get a tailored view (no add/enroll, 'Coaches' card)
            // For coaches, show normal dashboard
            emptyContainer.visibility = View.GONE
            // Show main content
            findViewById<View>(R.id.logo).visibility = View.VISIBLE
            btnConnect.visibility = View.VISIBLE
            btnExercises.visibility = View.VISIBLE
            btnSessions.visibility = View.VISIBLE

            // Swimmer dashboard tweaks
            if (user.role == UserRole.COACH) {
                btnSwimmers.visibility = View.VISIBLE
                btnEnrollSwimmer.visibility = View.VISIBLE
                btnConnect.visibility = View.GONE // Hide connect button for coaches
                // Title stays 'Swimmers' for coaches
                findViewById<TextView>(R.id.tvSwimmersTitle)?.text = "Swimmers"
            } else {
                // For swimmers: show 'Coaches' card instead of 'Swimmers'
                btnSwimmers.visibility = View.VISIBLE
                btnEnrollSwimmer.visibility = View.GONE
                btnConnect.visibility = View.VISIBLE // Show connect button for swimmers
                findViewById<TextView>(R.id.tvSwimmersTitle)?.text = "Coaches"
            }
            return
        }
        // Show empty state
        emptyContainer.visibility = View.VISIBLE
        findViewById<View>(R.id.logo).visibility = View.GONE
        btnConnect.visibility = View.GONE
        btnSwimmers.visibility = View.GONE
        btnExercises.visibility = View.GONE
        btnSessions.visibility = View.GONE
        btnEnrollSwimmer.visibility = View.GONE

        if (user.role == UserRole.COACH) {
            tvEmptyTitle.text = getString(R.string.no_classes_coach)
            btnEmptyPrimary.text = getString(R.string.create_class)
            btnEmptySecondary.text = getString(R.string.join_class_via_code)
            btnEmptyPrimary.setOnClickListener { startActivity(Intent(this, CreateTeamActivity::class.java)) }
            btnEmptySecondary.setOnClickListener { startActivity(Intent(this, JoinTeamByCodeActivity::class.java)) }
            btnEmptySecondary.visibility = View.VISIBLE
        } else {
            // Swimmer with no teams: show only ONE button to enroll
            tvEmptyTitle.text = getString(R.string.no_classes_swimmer)
            btnEmptyPrimary.text = getString(R.string.enroll_in_team)
            btnEmptyPrimary.setOnClickListener { startActivity(Intent(this, EnrollViaCodeActivity::class.java)) }
            btnEmptySecondary.visibility = View.GONE // Hide second button for swimmers
        }
    }

    private fun updateTopRow() {
        lifecycleScope.launch(Dispatchers.IO) {
            val user = AuthManager.currentUser(this@MainActivity)
            val teamId = AuthManager.currentTeamId(this@MainActivity)
            val teamName = if (teamId != null) db.teamDao().getById(teamId)?.name else null
            withContext(Dispatchers.Main) {
                tvTeamSwitcher.text = (teamName ?: "Select team") + " \u25BC"
                tvAccount.text = user?.let { "${it.email} (${it.role.name.lowercase().replaceFirstChar { c -> c.uppercase() }})" } ?: "Guest"
            }
        }
    }

    private fun loadSwimmerCount() {
        lifecycleScope.launch(Dispatchers.IO) {
            val user = AuthManager.currentUser(this@MainActivity)
            val teamId = AuthManager.currentTeamId(this@MainActivity)
            var count = 0
            if (user?.role == UserRole.COACH) {
                if (teamId != null) count = db.swimmerDao().getSwimmersForTeam(teamId).size else count = 0
            } else if (user?.role == UserRole.SWIMMER) {
                val linked = AuthManager.getLinkedSwimmerId(this@MainActivity, user.email, teamId)
                count = if (linked != null) 1 else 0
            }
            withContext(Dispatchers.Main) {
                swimmerCount = count
                updateCounts()
            }
        }
    }

    private fun updateCounts() {
        tvSwimmerCount.text = swimmerCount.toString()
        tvSessionCount.text = sessionCount.toString()
    }

    private fun handleSmartwatchConnection() {
        val pixelWatchPackage = "com.google.android.apps.wear.companion"

        if (isPackageInstalled(pixelWatchPackage)) {
            Wearable.getNodeClient(this).connectedNodes
                .addOnSuccessListener { nodes ->
                    isSmartwatchConnected = nodes.isNotEmpty()
                    updateSmartwatchButton()

                    val connectionType = if (isSmartwatchConnected) {
                        if (BluetoothAdapter.getDefaultAdapter()?.isEnabled == true) {
                            "Smartwatch connected (Bluetooth)"
                        } else {
                            "Smartwatch connected (Cloud)"
                        }
                    } else {
                        "Pixel Watch app found, but no smartwatch connected"
                    }

                    Toast.makeText(this, connectionType, Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to check smartwatch connection", Toast.LENGTH_SHORT).show()
                }
        } else {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=$pixelWatchPackage")
                setPackage("com.android.vending")
            }
            startActivity(intent)
        }
    }

    private fun updateSmartwatchButton() {
        if (isSmartwatchConnected) {
            btnConnect.text = "Disconnect"
            btnConnect.backgroundTintList = ContextCompat.getColorStateList(this, R.color.disconnect)
        } else {
            btnConnect.text = "Connect"
            btnConnect.backgroundTintList = ContextCompat.getColorStateList(this, R.color.button)
        }
    }


    private fun checkSmartwatchConnection() {
        val pixelWatchPackage = "com.google.android.apps.wear.companion"

        if (isPackageInstalled(pixelWatchPackage)) {
            Wearable.getNodeClient(this).connectedNodes
                .addOnSuccessListener { nodes ->
                    isSmartwatchConnected = nodes.isNotEmpty()
                    updateSmartwatchButton()
                }
                .addOnFailureListener {
                    isSmartwatchConnected = false
                    updateSmartwatchButton()
                    Toast.makeText(this, "Failed to check smartwatch connection", Toast.LENGTH_SHORT).show()
                }
        } else {
            isSmartwatchConnected = false
            updateSmartwatchButton()
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        val role = AuthManager.currentUser(this)?.role
        val hasTeam = AuthManager.currentTeamId(this) != null
        menu?.findItem(R.id.action_create_team)?.isVisible = role == UserRole.COACH
        menu?.findItem(R.id.action_add_team_via_code)?.isVisible = role == UserRole.SWIMMER
        menu?.findItem(R.id.action_manage_coaches)?.isVisible = role == UserRole.COACH && hasTeam
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_switch_team -> { showSwitchTeamDialog(withCreateOption = true); true }
            R.id.action_create_team -> { startActivity(Intent(this, CreateTeamActivity::class.java)); true }
            R.id.action_add_team_via_code -> { startActivity(Intent(this, EnrollViaCodeActivity::class.java)); true }
            R.id.action_manage_coaches -> { startActivity(Intent(this, ManageCoachesActivity::class.java)); true }
            R.id.action_logout, R.id.action_switch_user -> { AuthManager.logout(this); startActivity(Intent(this, AuthActivity::class.java)); finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSwitchTeamDialog(withCreateOption: Boolean = false) {
        val user = AuthManager.currentUser(this) ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val teamIds = if (user.role == UserRole.COACH) AuthManager.getCoachTeams(this@MainActivity, user.email) else AuthManager.getSwimmerTeams(this@MainActivity, user.email)
            val teams: List<Team> = teamIds.mapNotNull { db.teamDao().getById(it) }
            withContext(Dispatchers.Main) {
                val baseNames = teams.map { it.name }.toMutableList()
                val actions = mutableListOf<() -> Unit>()
                teams.forEach { team ->
                    actions.add {
                        AuthManager.setCurrentTeamId(this@MainActivity, team.id)
                        updateTopRow()
                        loadSwimmerCount()
                        updateEmptyState()
                        invalidateOptionsMenu()
                    }
                }
                if (withCreateOption) {
                    if (user.role == UserRole.COACH) {
                        baseNames.add("+ Create Team")
                        actions.add { startActivity(Intent(this@MainActivity, CreateTeamActivity::class.java)) }
                        baseNames.add("+ Join Team via Code")
                        actions.add { startActivity(Intent(this@MainActivity, JoinTeamByCodeActivity::class.java)) }
                    } else {
                        baseNames.add("+ Add Team via Code")
                        actions.add { startActivity(Intent(this@MainActivity, EnrollViaCodeActivity::class.java)) }
                    }
                }
                if (baseNames.isEmpty()) {
                    Toast.makeText(this@MainActivity, "No teams", Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Select Team")
                    .setItems(baseNames.toTypedArray()) { _, which -> actions[which].invoke() }
                    .show()
            }
        }
    }

    private fun showAccountMenu() {
        val user = AuthManager.currentUser(this)
        if (user?.role == UserRole.COACH) {
            val items = arrayOf("View Team Code", "Manage Coaches", "Logout")
            AlertDialog.Builder(this)
                .setTitle("Account")
                .setItems(items) { _, which ->
                    when (which) {
                        0 -> showTeamCode()
                        1 -> startActivity(Intent(this, ManageCoachesActivity::class.java))
                        2 -> { AuthManager.logout(this); startActivity(Intent(this, AuthActivity::class.java)); finish() }
                    }
                }
                .show()
        } else {
            val items = arrayOf("Logout")
            AlertDialog.Builder(this)
                .setTitle("Account")
                .setItems(items) { _, which ->
                    when (which) {
                        0 -> { AuthManager.logout(this); startActivity(Intent(this, AuthActivity::class.java)); finish() }
                    }
                }
                .show()
        }
    }

    private fun showTeamCode() {
        val teamId = AuthManager.currentTeamId(this)
        if (teamId == null) {
            Toast.makeText(this, "No team selected", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val team = db.teamDao().getById(teamId)
            withContext(Dispatchers.Main) {
                if (team != null) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("${team.name} - Team Code")
                        .setMessage("Share this code with swimmers to join your team:\n\n${team.joinCode}")
                        .setPositiveButton("Copy Code") { _, _ ->
                            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Team Code", team.joinCode)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(this@MainActivity, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Close", null)
                        .show()
                } else {
                    Toast.makeText(this@MainActivity, "Team not found", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ...existing smartwatch helpers (handleSmartwatchConnection, updateSmartwatchButton, checkSmartwatchConnection, isPackageInstalled)...
}