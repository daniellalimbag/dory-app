package com.thesisapp.presentation.activities

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.non_dao.TeamMembership
import com.thesisapp.data.repository.TeamRepository
import com.thesisapp.utils.AuthManager
import com.thesisapp.utils.LocalUserBootstrapper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class EnrollViaCodeActivity : AppCompatActivity() {

    @Inject
    lateinit var teamRepository: TeamRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enroll_via_code)

        val inputCode = findViewById<EditText>(R.id.inputSwimmerCode)
        val btnEnroll = findViewById<Button>(R.id.btnEnroll)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        btnBack.setOnClickListener { finish() }

        btnEnroll.setOnClickListener {
            val code = inputCode.text.toString().trim().uppercase()
            if (code.isEmpty()) {
                Toast.makeText(this, "Enter team code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            lifecycleScope.launch(Dispatchers.IO) {
                val db = AppDatabase.getInstance(this@EnrollViaCodeActivity)
                val team = db.teamDao().getByJoinCode(code)
                
                withContext(Dispatchers.Main) {
                    if (team == null) {
                        Toast.makeText(this@EnrollViaCodeActivity, "Invalid team code", Toast.LENGTH_SHORT).show()
                        return@withContext
                    }
                    
                    val user = AuthManager.currentUser(this@EnrollViaCodeActivity)
                    if (user == null) {
                        Toast.makeText(this@EnrollViaCodeActivity, "Please login first", Toast.LENGTH_SHORT).show()
                        return@withContext
                    }
                    
                    // Show confirmation dialog
                    AlertDialog.Builder(this@EnrollViaCodeActivity)
                        .setTitle("Join ${team.name}?")
                        .setMessage("You're about to join ${team.name}.")
                        .setPositiveButton("Continue") { _, _ ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                val db2 = AppDatabase.getInstance(this@EnrollViaCodeActivity)

                                val resolvedUserId = LocalUserBootstrapper.ensureRoomUserForAuth(this@EnrollViaCodeActivity, db2)
                                val existingSwimmer = if (!resolvedUserId.isNullOrBlank()) {
                                    db2.swimmerDao().getByUserId(resolvedUserId)
                                } else {
                                    null
                                }

                                if (resolvedUserId.isNullOrBlank() || existingSwimmer == null) {
                                    withContext(Dispatchers.Main) {
                                        val intent = Intent(this@EnrollViaCodeActivity, CreateSwimmerProfileActivity::class.java)
                                        intent.putExtra("TEAM_ID", team.id)
                                        startActivity(intent)
                                        finish()
                                    }
                                    return@launch
                                }

                                val swimmerId = existingSwimmer.id

                                Log.d("DEBUG", "joinTeam(teamId=${team.id}, swimmerId=$swimmerId)")

                                // Insert membership locally
                                db2.teamMembershipDao().insert(TeamMembership(teamId = team.id, swimmerId = swimmerId))

                                // Insert+verify membership in Supabase. On failure, do NOT navigate.
                                try {
                                    teamRepository.joinTeam(teamId = team.id, swimmerId = swimmerId)
                                } catch (e: Exception) {
                                    Log.d("DEBUG", "Supabase joinTeam failed", e)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            this@EnrollViaCodeActivity,
                                            e.message ?: "Failed to join team (Supabase)",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    return@launch
                                }

                                AuthManager.linkSwimmerToTeam(this@EnrollViaCodeActivity, user.email, team.id, swimmerId)
                                AuthManager.setCurrentTeamId(this@EnrollViaCodeActivity, team.id)

                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@EnrollViaCodeActivity, "✓ Joined team successfully!", Toast.LENGTH_LONG).show()
                                    startActivity(Intent(this@EnrollViaCodeActivity, MainActivity::class.java))
                                    finish()
                                }
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }
    }
    
    // Hide keyboard when touching outside of EditText
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val view = currentFocus
            if (view != null) {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
                view.clearFocus()
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}

