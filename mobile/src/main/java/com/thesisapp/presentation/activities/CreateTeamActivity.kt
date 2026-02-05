package com.thesisapp.presentation.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.thesisapp.R
import com.thesisapp.data.repository.TeamRepository
import com.thesisapp.utils.AuthManager

import com.thesisapp.utils.animateClick
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CreateTeamActivity : AppCompatActivity() {

    @Inject lateinit var teamRepository: TeamRepository
    @Inject lateinit var supabase: SupabaseClient

    private var selectedLogoBytes: ByteArray? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.onSuccess { bytes ->
            if (bytes != null) {
                selectedLogoBytes = bytes
                findViewById<ImageView>(R.id.imgTeamLogo).setImageURI(uri)
            }
        }.onFailure {
            Toast.makeText(this, "Unable to read image", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_team)

        val inputName = findViewById<EditText>(R.id.inputTeamName)
        val txtCode = findViewById<TextView>(R.id.txtTeamCode)
        val btnGenerate = findViewById<Button>(R.id.btnGenerateCode)
        val btnCreate = findViewById<Button>(R.id.btnCreateTeam)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnSkip = findViewById<Button>(R.id.btnSkip)
        val btnCopyCode = findViewById<ImageButton>(R.id.btnCopyCode)
        val btnPickLogo = findViewById<Button>(R.id.btnPickLogo)
        val progress = findViewById<ProgressBar>(R.id.progressCreateTeam)

        var currentCode = ""
        txtCode.text = ""
        btnGenerate.isEnabled = false
        btnCopyCode.isEnabled = false

        btnCopyCode.setOnClickListener {
            it.animateClick()
            if (currentCode.isBlank()) return@setOnClickListener
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Team Code", currentCode)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        btnGenerate.setOnClickListener {
            it.animateClick()
            Toast.makeText(this, "Code will be generated after team creation", Toast.LENGTH_SHORT).show()
        }

        btnBack.setOnClickListener { finish() }

        btnPickLogo.setOnClickListener {
            it.animateClick()
            pickImage.launch("image/*")
        }

        btnSkip.setOnClickListener {
            // Skip team creation and go to main activity
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        btnCreate.setOnClickListener {
            it.animateClick()
            val name = inputName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Enter team name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progress.visibility = android.view.View.VISIBLE
            btnCreate.isEnabled = false
            btnSkip.isEnabled = false
            btnBack.isEnabled = false

            lifecycleScope.launch {
                val coachId = supabase.auth.currentUserOrNull()?.id
                if (coachId.isNullOrBlank()) {
                    progress.visibility = android.view.View.GONE
                    btnCreate.isEnabled = true
                    btnSkip.isEnabled = true
                    btnBack.isEnabled = true
                    Toast.makeText(this@CreateTeamActivity, "Not authenticated", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val result = teamRepository.createTeam(name = name, coachId = coachId)
                progress.visibility = android.view.View.GONE
                btnCreate.isEnabled = true
                btnSkip.isEnabled = true
                btnBack.isEnabled = true

                result
                    .onSuccess { teamIdStr ->
                        val teamId = teamIdStr.toIntOrNull()
                        if (teamId == null) {
                            Toast.makeText(this@CreateTeamActivity, "Team created but invalid team id", Toast.LENGTH_LONG).show()
                            return@onSuccess
                        }

                        val logoBytes = selectedLogoBytes
                        if (logoBytes != null) {
                            runCatching {
                                teamRepository.uploadTeamLogo(teamId = teamId.toLong(), byteArray = logoBytes)
                            }.onFailure { e ->
                                Toast.makeText(
                                    this@CreateTeamActivity,
                                    e.message ?: "Logo upload failed",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }

                        val user = AuthManager.currentUser(this@CreateTeamActivity)
                        if (user != null) {
                            AuthManager.addCoachTeam(this@CreateTeamActivity, user.email, teamId)
                            AuthManager.addCoachToTeam(this@CreateTeamActivity, teamId, user.email)
                            AuthManager.setCurrentTeamId(this@CreateTeamActivity, teamId)
                        }

                        Toast.makeText(this@CreateTeamActivity, "Team created", Toast.LENGTH_LONG).show()
                        startActivity(Intent(this@CreateTeamActivity, ManageCoachesActivity::class.java))
                        finish()
                    }
                    .onFailure { e ->
                        Toast.makeText(this@CreateTeamActivity, "Error creating team: ${e.message}", Toast.LENGTH_LONG).show()
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