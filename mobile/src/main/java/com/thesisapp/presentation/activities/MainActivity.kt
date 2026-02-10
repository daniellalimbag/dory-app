package com.thesisapp.presentation.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ImageSpan
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.non_dao.Team
import com.thesisapp.presentation.adapters.CoachPagerAdapter
import com.thesisapp.presentation.adapters.TeamSwitcherAdapter
import com.thesisapp.data.repository.TeamRepository
import com.thesisapp.utils.AuthManager
import com.thesisapp.utils.UserRole
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var teamRepository: TeamRepository

    private lateinit var db: AppDatabase
    private lateinit var tvTeamSwitcher: TextView
    private lateinit var tvAccount: TextView

    // Coach-specific views
    private var bottomNavigation: BottomNavigationView? = null
    private var viewPager: ViewPager2? = null

    // Empty state views
    private lateinit var emptyContainer: View
    private lateinit var tvEmptyTitle: TextView
    private lateinit var btnEmptyPrimary: MaterialButton
    private lateinit var btnEmptySecondary: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val user = AuthManager.currentUser(this)
        db = AppDatabase.getInstance(applicationContext)

        if (user?.role == UserRole.SWIMMER) {
            val (teamId, swimmerId) = resolveSwimmerContext(user.email)
            if (teamId != null && swimmerId != null) {
                startActivity(
                    Intent(this, SwimmerProfileActivity::class.java).apply {
                        putExtra(SwimmerProfileActivity.EXTRA_SWIMMER_ID, swimmerId)
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                )
            } else {
                startActivity(Intent(this, EnrollViaCodeActivity::class.java))
            }
            finish()
            return
        }

        setContentView(R.layout.activity_main_coach)
        setupCoachView()
    }

    private fun resolveSwimmerContext(email: String): Pair<Int?, Int?> {
        var teamId = AuthManager.currentTeamId(this)
        if (teamId == null) {
            val teams = AuthManager.getSwimmerTeams(this, email)
            if (teams.isNotEmpty()) {
                teamId = teams.first()
                AuthManager.setCurrentTeamId(this, teamId)
            }
        }
        val swimmerId = AuthManager.getLinkedSwimmerId(this, email, teamId)
        return teamId to swimmerId
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

    override fun onResume() {
        super.onResume()
        updateTopRow()
        updateCoachEmptyState()
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

    private fun menuItemWithIcon(
        iconRes: Int,
        label: String,
        tintColor: Int
    ): CharSequence {
        val drawable = ContextCompat.getDrawable(this, iconRes) ?: return label
        val wrapped = DrawableCompat.wrap(drawable.mutate())
        DrawableCompat.setTint(wrapped, tintColor)

        val iconSize = (20 * resources.displayMetrics.density).toInt()
        wrapped.setBounds(0, 0, iconSize, iconSize)

        val text = "  $label"
        val spannable = SpannableString(text)
        spannable.setSpan(
            ImageSpan(wrapped, ImageSpan.ALIGN_BOTTOM),
            0,
            1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return spannable
    }

    private fun showSwitchTeamDialog(withCreateOption: Boolean = false) {
        val user = AuthManager.currentUser(this) ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val teamIds = if (user.role == UserRole.COACH) AuthManager.getCoachTeams(this@MainActivity, user.email) else AuthManager.getSwimmerTeams(this@MainActivity, user.email)
            val teams: List<Team> = teamIds.mapNotNull { db.teamDao().getById(it) }
            withContext(Dispatchers.Main) {
                val iconTint = MaterialColors.getColor(
                    this@MainActivity,
                    com.google.android.material.R.attr.colorOnSurface,
                    0
                )

                if (teams.isEmpty()) {
                    Toast.makeText(this@MainActivity, "No teams", Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                val teamAdapter = TeamSwitcherAdapter(
                    context = this@MainActivity,
                    teams = teams,
                    teamRepository = teamRepository,
                    scope = lifecycleScope
                )

                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Team")
                    .setAdapter(teamAdapter) { _, which ->
                        val selected = teams[which]
                        AuthManager.setCurrentTeamId(this@MainActivity, selected.id)
                        updateTopRow()
                        val currentUser = AuthManager.currentUser(this@MainActivity)
                        if (currentUser?.role == UserRole.COACH) {
                            updateCoachEmptyState()
                        } else {
                            updateCoachEmptyState()
                        }
                    }
                    .setNeutralButton("Actions") { _, _ ->
                        showTeamActionsDialog(iconTint)
                    }
                    .show()
            }
        }
    }

    private fun showTeamActionsDialog(iconTint: Int) {
        val user = AuthManager.currentUser(this) ?: return
        val items = mutableListOf<CharSequence>()
        val actions = mutableListOf<() -> Unit>()

        if (user.role == UserRole.COACH) {
            items.add(menuItemWithIcon(R.drawable.invite, "Invite Swimmer", iconTint))
            actions.add { showInviteSwimmerDialog() }

            items.add(menuItemWithIcon(R.drawable.swimmer, "Invite Coach", iconTint))
            actions.add { showInviteCoachDialog() }

            items.add(menuItemWithIcon(R.drawable.edit, "Edit Team", iconTint))
            actions.add {
                startActivity(Intent(this@MainActivity, EditTeamActivity::class.java))
            }

            items.add("+ Create Team")
            actions.add { startActivity(Intent(this@MainActivity, CreateTeamActivity::class.java)) }

            items.add("+ Join Team via Code")
            actions.add { startActivity(Intent(this@MainActivity, JoinTeamByCodeActivity::class.java)) }
        } else {
            items.add("+ Join Team via Code")
            actions.add { startActivity(Intent(this@MainActivity, EnrollViaCodeActivity::class.java)) }
        }

        AlertDialog.Builder(this@MainActivity)
            .setTitle("Team actions")
            .setItems(items.toTypedArray()) { _, which -> actions[which].invoke() }
            .show()
    }

    private fun showAccountMenu() {
        val iconTint = MaterialColors.getColor(
            this@MainActivity,
            com.google.android.material.R.attr.colorOnSurface,
            0
        )
        val items = arrayOf(
            menuItemWithIcon(R.drawable.settings, "Settings", iconTint),
            menuItemWithIcon(R.drawable.logout, "Logout", iconTint),
            menuItemWithIcon(R.drawable.resource_switch, "Switch User", iconTint)
        )
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

    override fun onStart() {
        super.onStart()
    }
}