package com.thesisapp.presentation.activities

import android.bluetooth.BluetoothAdapter
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.button.MaterialButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.gms.wearable.Wearable
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.non_dao.Team
import com.thesisapp.presentation.adapters.CoachPagerAdapter
import com.thesisapp.utils.AuthManager
import com.thesisapp.utils.UserRole
import com.thesisapp.utils.animateClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var tvTeamSwitcher: TextView
    private lateinit var tvAccount: TextView

    // Coach-specific views
    private var bottomNavigation: BottomNavigationView? = null
    private var viewPager: ViewPager2? = null
    
    // Swimmer-specific views
    private var btnConnect: Button? = null
    private var btnSwimmers: MaterialCardView? = null
    private var btnSessions: MaterialCardView? = null
    private var btnEnrollSwimmer: MaterialButton? = null
    private var tvSwimmerCount: TextView? = null
    private var tvSessionCount: TextView? = null
    private var tvSessionsTitle: TextView? = null
    private var isSmartwatchConnected = false

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

        val user = AuthManager.currentUser(this)
        db = AppDatabase.getInstance(applicationContext)

        if (user?.role == UserRole.SWIMMER) {
            var teamId = AuthManager.currentTeamId(this)
            if (teamId == null) {
                val teams = AuthManager.getSwimmerTeams(this, user.email)
                if (teams.isNotEmpty()) {
                    teamId = teams.first()
                    AuthManager.setCurrentTeamId(this, teamId)
                }
            }
            val swimmerId = AuthManager.getLinkedSwimmerId(this, user.email, teamId)
            if (teamId != null && swimmerId != null) {
                val intent = Intent(this, SwimmerProfileActivity::class.java).apply {
                    putExtra(SwimmerProfileActivity.EXTRA_SWIMMER_ID, swimmerId)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                finish()
                return
            }
        }

        // Set different layouts based on role
        if (user?.role == UserRole.COACH) {
            setContentView(R.layout.activity_main_coach)
            setupCoachView()
        } else {
            setContentView(R.layout.activity_main_dashboard)
            setupSwimmerView()
        }
    }

    private fun setupCoachView() {
        tvTeamSwitcher = findViewById(R.id.tvTeamSwitcher)
        tvAccount = findViewById(R.id.tvAccount)
        emptyContainer = findViewById(R.id.emptyStateContainer)
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle)
        btnEmptyPrimary = findViewById(R.id.btnEmptyPrimary)
        btnEmptySecondary = findViewById(R.id.btnEmptySecondary)
        
        bottomNavigation = findViewById(R.id.bottomNavigation)
        viewPager = findViewById(R.id.viewPager)

        tvTeamSwitcher.setOnClickListener { showSwitchTeamDialog(withCreateOption = true) }
        tvAccount.setOnClickListener { showAccountMenu() }

        // Set up ViewPager with bottom navigation
        val adapter = CoachPagerAdapter(this)
        viewPager?.adapter = adapter
        viewPager?.isUserInputEnabled = true // Allow swipe between pages
        
        // Connect bottom navigation with ViewPager
        bottomNavigation?.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_swimmers -> {
                    viewPager?.currentItem = 0
                    true
                }
                R.id.nav_exercises -> {
                    viewPager?.currentItem = 1
                    true
                }
                else -> false
            }
        }
        
        // Update bottom navigation when ViewPager page changes
        viewPager?.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                bottomNavigation?.menu?.getItem(position)?.isChecked = true
            }
        })

        // Empty state buttons
        btnEmptyPrimary.setOnClickListener { 
            startActivity(Intent(this, CreateTeamActivity::class.java)) 
        }
        btnEmptySecondary.setOnClickListener { 
            startActivity(Intent(this, JoinTeamByCodeActivity::class.java)) 
        }
    }

    private fun setupSwimmerView() {
        tvTeamSwitcher = findViewById(R.id.tvTeamSwitcher)
        tvAccount = findViewById(R.id.tvAccount)

        // Empty state
        emptyContainer = findViewById(R.id.emptyStateContainer)
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle)
        btnEmptyPrimary = findViewById(R.id.btnEmptyPrimary)
        btnEmptySecondary = findViewById(R.id.btnEmptySecondary)

        btnConnect = findViewById(R.id.btnConnect)
        btnSwimmers = findViewById(R.id.btnSwimmers)
        btnSessions = findViewById(R.id.btnSessions)
        btnEnrollSwimmer = findViewById(R.id.btnEnrollSwimmer)
        tvSwimmerCount = findViewById(R.id.tvSwimmerCount)
        tvSessionCount = findViewById(R.id.tvSessionCount)
        tvSessionsTitle = findViewById(R.id.tvSessionsTitle)

        db = AppDatabase.getInstance(applicationContext)

        // Set dummy data
        updateCounts()

        updateSmartwatchButton()

        tvTeamSwitcher.setOnClickListener { showSwitchTeamDialog(withCreateOption = true) }
        tvAccount.setOnClickListener { showAccountMenu() }

        btnConnect?.setOnClickListener {
            it.animateClick()
            startActivity(Intent(this, ConnectActivity::class.java))
        }

        btnSwimmers?.setOnClickListener {
            it.animateClick()
            val user = AuthManager.currentUser(this)
            if (user?.role == UserRole.SWIMMER) {
                val teamId = AuthManager.currentTeamId(this)
                val swimmerId = AuthManager.getLinkedSwimmerId(this, user.email, teamId)
                if (teamId == null || swimmerId == null) {
                    startActivity(Intent(this, EnrollViaCodeActivity::class.java))
                } else {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val swimmer = db.swimmerDao().getById(swimmerId)
                        withContext(Dispatchers.Main) {
                            if (swimmer != null) {
                                val intent = Intent(this@MainActivity, SwimmerProfileActivity::class.java).apply {
                                    putExtra(SwimmerProfileActivity.EXTRA_SWIMMER, swimmer)
                                }
                                startActivity(intent)
                            } else {
                                Toast.makeText(this@MainActivity, "No swimmer linked", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            } else {
                startActivity(Intent(this, SwimmersActivity::class.java))
            }
        }

        btnSessions?.setOnClickListener {
            it.animateClick()
            // For coaches: Exercise Library, for swimmers: History
            val role = AuthManager.currentUser(this)?.role
            if (role == UserRole.COACH) {
                startActivity(Intent(this, ExerciseLibraryActivity::class.java))
            } else {
                startActivity(Intent(this, HistoryListActivity::class.java))
            }
        }

        // Only show enroll button for swimmers (coaches use Team dropdown)
        val role = AuthManager.currentUser(this)?.role
        if (role == UserRole.COACH) {
            btnEnrollSwimmer?.visibility = View.GONE
        } else {
            btnEnrollSwimmer?.setOnClickListener {
                it.animateClick()
                startActivity(Intent(this, EnrollViaCodeActivity::class.java))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val user = AuthManager.currentUser(this)
        
        if (user?.role == UserRole.COACH) {
            // Coach view - just update UI state
            updateTopRow()
            updateCoachEmptyState()
        } else {
            // Swimmer view - refresh data and check smartwatch
            loadSwimmerCount()
            checkSmartwatchConnection()
            updateTopRow()
            updateSwimmerEmptyState()
            updateButtonText()
        }
    }

    private fun updateButtonText() {
        val role = AuthManager.currentUser(this)?.role
        if (role == UserRole.COACH) {
            btnEnrollSwimmer?.text = getString(R.string.enroll_swimmer)
        } else {
            btnEnrollSwimmer?.text = getString(R.string.enroll_in_team)
        }
    }

    private fun updateCoachEmptyState() {
        val user = AuthManager.currentUser(this) ?: return
        val hasTeams = AuthManager.getCoachTeams(this, user.email).isNotEmpty()
        
        if (hasTeams) {
            emptyContainer.visibility = View.GONE
            bottomNavigation?.visibility = View.VISIBLE
            viewPager?.visibility = View.VISIBLE
        } else {
            emptyContainer.visibility = View.VISIBLE
            bottomNavigation?.visibility = View.GONE
            viewPager?.visibility = View.GONE
            
            tvEmptyTitle.text = getString(R.string.no_classes_coach)
            btnEmptyPrimary.text = getString(R.string.create_class)
            btnEmptySecondary.text = getString(R.string.join_class_via_code)
        }
    }

    private fun updateSwimmerEmptyState() {
        val user = AuthManager.currentUser(this)
        if (user == null) return
        
        val hasTeams = AuthManager.getSwimmerTeams(this, user.email).isNotEmpty()
        
        if (hasTeams) {
            // Swimmers should go directly to their profile/stats view
            val teamId = AuthManager.currentTeamId(this)
            val swimmerId = AuthManager.getLinkedSwimmerId(this, user.email, teamId)
            if (swimmerId != null) {
                // Redirect to swimmer profile with tabs (Stats, Sessions, Profile)
                lifecycleScope.launch(Dispatchers.IO) {
                    val swimmer = db.swimmerDao().getById(swimmerId)
                    withContext(Dispatchers.Main) {
                        if (swimmer != null) {
                            val intent = Intent(this@MainActivity, SwimmerProfileActivity::class.java).apply {
                                putExtra(SwimmerProfileActivity.EXTRA_SWIMMER, swimmer)
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                            startActivity(intent)
                            finish()
                        }
                    }
                }
                return
            }
            
            // Show normal dashboard for swimmers
            emptyContainer.visibility = View.GONE
            findViewById<View>(R.id.logo)?.visibility = View.VISIBLE
            btnConnect?.visibility = View.VISIBLE
            btnSessions?.visibility = View.VISIBLE
            btnSwimmers?.visibility = View.GONE
            btnEnrollSwimmer?.visibility = View.GONE
            tvSessionsTitle?.text = "Sessions"
            tvSessionCount?.text = "$sessionCount sessions"
            return
        }
        
        // Show empty state for swimmer with no teams
        emptyContainer.visibility = View.VISIBLE
        findViewById<View>(R.id.logo)?.visibility = View.GONE
        btnConnect?.visibility = View.GONE
        btnSwimmers?.visibility = View.GONE
        btnSessions?.visibility = View.GONE
        btnEnrollSwimmer?.visibility = View.GONE
        
        tvEmptyTitle.text = getString(R.string.no_classes_swimmer)
        btnEmptyPrimary.text = getString(R.string.enroll_in_team)
        btnEmptyPrimary.setOnClickListener { startActivity(Intent(this, EnrollViaCodeActivity::class.java)) }
        btnEmptySecondary.visibility = View.GONE
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
                if (teamId != null) count = db.teamMembershipDao().getSwimmerCountForTeam(teamId) else count = 0
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
        tvSwimmerCount?.text = swimmerCount.toString()
        tvSessionCount?.text = sessionCount.toString()
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
            btnConnect?.text = "Disconnect"
            btnConnect?.backgroundTintList = ContextCompat.getColorStateList(this, R.color.disconnect)
        } else {
            btnConnect?.text = "Connect"
            btnConnect?.backgroundTintList = ContextCompat.getColorStateList(this, R.color.button)
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

    // Menu removed - all actions now in Team and Account dropdowns
    // Team dropdown: Switch team, Invite swimmer/coach, Edit team, Create/Join team
    // Account dropdown: View profile, Settings, Logout, Switch user

    private fun showSwitchTeamDialog(withCreateOption: Boolean = false) {
        val user = AuthManager.currentUser(this) ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val teamIds = if (user.role == UserRole.COACH) AuthManager.getCoachTeams(this@MainActivity, user.email) else AuthManager.getSwimmerTeams(this@MainActivity, user.email)
            val teams: List<Team> = teamIds.mapNotNull { db.teamDao().getById(it) }
            withContext(Dispatchers.Main) {
                val items = mutableListOf<String>()
                val actions = mutableListOf<() -> Unit>()
                
                // Add existing teams
                teams.forEach { team ->
                    items.add(team.name)
                    actions.add {
                        AuthManager.setCurrentTeamId(this@MainActivity, team.id)
                        updateTopRow()
                        loadSwimmerCount()
                        val currentUser = AuthManager.currentUser(this@MainActivity)
                        if (currentUser?.role == UserRole.COACH) {
                            updateCoachEmptyState()
                        } else {
                            updateSwimmerEmptyState()
                        }
                    }
                }
                
                // Divider
                if (teams.isNotEmpty()) {
                    items.add("─────────────────")
                    actions.add { /* No-op divider */ }
                }
                
                // Team actions (only for coaches)
                if (user.role == UserRole.COACH) {
                    items.add("✉️ Invite Swimmer")
                    actions.add { showInviteSwimmerDialog() }
                    
                    items.add("👥 Invite Coach")
                    actions.add { showInviteCoachDialog() }
                    
                    items.add("⚙️ Edit Team")
                    actions.add { 
                        // TODO: Navigate to EditTeamActivity when created
                        Toast.makeText(this@MainActivity, "Edit team coming soon", Toast.LENGTH_SHORT).show()
                    }
                    
                    items.add("─────────────────")
                    actions.add { /* No-op divider */ }
                }
                
                // Create/Join team options
                if (user.role == UserRole.COACH) {
                    items.add("+ Create Team")
                    actions.add { startActivity(Intent(this@MainActivity, CreateTeamActivity::class.java)) }
                    
                    items.add("+ Join Team via Code")
                    actions.add { startActivity(Intent(this@MainActivity, JoinTeamByCodeActivity::class.java)) }
                } else {
                    items.add("+ Join Team via Code")
                    actions.add { startActivity(Intent(this@MainActivity, EnrollViaCodeActivity::class.java)) }
                }
                
                if (items.isEmpty()) {
                    Toast.makeText(this@MainActivity, "No teams", Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Team")
                    .setItems(items.toTypedArray()) { _, which -> actions[which].invoke() }
                    .show()
            }
        }
    }

    private fun showAccountMenu() {
        val items = arrayOf("⚙️ Settings", "🚪 Logout", "🔄 Switch User")
        AlertDialog.Builder(this)
            .setTitle("Account")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(this, SettingsActivity::class.java))
                    1 -> { AuthManager.logout(this); startActivity(Intent(this, AuthActivity::class.java)); finish() }
                    2 -> { AuthManager.logout(this); startActivity(Intent(this, AuthActivity::class.java)); finish() }
                }
            }
            .show()
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
                    val message = "Share this code with swimmers to join ${team.name}:\n\n${team.joinCode}\n\nSwimmers should:\n1. Open DORY app\n2. Click 'Join Team'\n3. Enter this code\n4. Fill out their profile"
                    
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Team Invitation Code")
                        .setMessage(message)
                        .setPositiveButton("Copy Code") { _, _ ->
                            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Team Code", team.joinCode)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(this@MainActivity, "Code copied!", Toast.LENGTH_SHORT).show()
                        }
                        .setNeutralButton("Share") { _, _ ->
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Join ${team.name} on DORY")
                                putExtra(Intent.EXTRA_TEXT, "Join my swimming team on DORY!\n\nTeam: ${team.name}\nCode: ${team.joinCode}\n\nDownload DORY and enter this code to join.")
                            }
                            startActivity(Intent.createChooser(shareIntent, "Share team code"))
                        }
                        .setNegativeButton("Close", null)
                        .show()
                } else {
                    Toast.makeText(this@MainActivity, "Team not found", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showInviteSwimmerDialog() {
        val teamId = AuthManager.currentTeamId(this)
        if (teamId == null) {
            Toast.makeText(this, "No team selected", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val team = db.teamDao().getById(teamId)
            withContext(Dispatchers.Main) {
                if (team != null) {
                    val message = "Invite swimmers to join ${team.name}\n\nTeam Code: ${team.joinCode}\n\nShare this code with swimmers. They should:\n1. Open DORY app\n2. Tap 'Join Team'\n3. Enter this code\n4. Complete their swimmer profile"
                    
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Invite Swimmer")
                        .setMessage(message)
                        .setPositiveButton("Copy Code") { _, _ ->
                            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Team Code", team.joinCode)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(this@MainActivity, "Code copied!", Toast.LENGTH_SHORT).show()
                        }
                        .setNeutralButton("Share") { _, _ ->
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Join ${team.name} as a Swimmer")
                                putExtra(Intent.EXTRA_TEXT, "Join my swimming team on DORY!\n\nTeam: ${team.name}\nCode: ${team.joinCode}\n\nDownload DORY, tap 'Join Team', and enter this code to create your swimmer profile.")
                            }
                            startActivity(Intent.createChooser(shareIntent, "Invite Swimmer"))
                        }
                        .setNegativeButton("Close", null)
                        .show()
                } else {
                    Toast.makeText(this@MainActivity, "Team not found", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showInviteCoachDialog() {
        val teamId = AuthManager.currentTeamId(this)
        if (teamId == null) {
            Toast.makeText(this, "No team selected", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val team = db.teamDao().getById(teamId)
            withContext(Dispatchers.Main) {
                if (team != null) {
                    val message = "Invite coaches to join ${team.name}\n\nTeam Code: ${team.joinCode}\n\nShare this code with other coaches. They should:\n1. Open DORY app\n2. Tap 'Join Team via Code' in the menu\n3. Enter this code"
                    
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Invite Coach")
                        .setMessage(message)
                        .setPositiveButton("Copy Code") { _, _ ->
                            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Team Code", team.joinCode)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(this@MainActivity, "Code copied!", Toast.LENGTH_SHORT).show()
                        }
                        .setNeutralButton("Share") { _, _ ->
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Join ${team.name} as a Coach")
                                putExtra(Intent.EXTRA_TEXT, "Join my swimming team on DORY as a coach!\n\nTeam: ${team.name}\nCode: ${team.joinCode}\n\nDownload DORY, go to Menu > Join Team via Code, and enter this code.")
                            }
                            startActivity(Intent.createChooser(shareIntent, "Invite Coach"))
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