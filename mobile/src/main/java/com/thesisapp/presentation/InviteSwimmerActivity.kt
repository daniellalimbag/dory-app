package com.thesisapp.presentation

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.InvitationRole
import com.thesisapp.data.TeamInvitation
import com.thesisapp.utils.AuthManager
import com.thesisapp.utils.CodeGenerator
import com.thesisapp.utils.animateClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InviteSwimmerActivity : AppCompatActivity() {

    private lateinit var inputSwimmerName: TextInputEditText
    private lateinit var inputSwimmerEmail: TextInputEditText
    private lateinit var btnGenerateInvite: Button
    private lateinit var btnShare: Button
    private lateinit var tvInviteCode: TextView
    private lateinit var tvInviteMessage: TextView
    private lateinit var btnReturn: ImageButton

    private var currentInviteCode: String? = null
    private var currentTeamName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_invite_swimmer)

        inputSwimmerName = findViewById(R.id.inputSwimmerName)
        inputSwimmerEmail = findViewById(R.id.inputSwimmerEmail)
        btnGenerateInvite = findViewById(R.id.btnGenerateInvite)
        btnShare = findViewById(R.id.btnShare)
        tvInviteCode = findViewById(R.id.tvInviteCode)
        tvInviteMessage = findViewById(R.id.tvInviteMessage)
        btnReturn = findViewById(R.id.btnReturn)

        val db = AppDatabase.getInstance(this)
        val teamId = AuthManager.currentTeamId(this)
        val user = AuthManager.currentUser(this)

        if (teamId == null || user == null) {
            Toast.makeText(this, "No team selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Load team name
        lifecycleScope.launch(Dispatchers.IO) {
            val team = db.teamDao().getById(teamId)
            withContext(Dispatchers.Main) {
                currentTeamName = team?.name ?: "Your Team"
            }
        }

        btnReturn.setOnClickListener {
            it.animateClick()
            finish()
        }

        btnGenerateInvite.setOnClickListener {
            it.animateClick()
            
            val swimmerName = inputSwimmerName.text.toString().trim()
            val swimmerEmail = inputSwimmerEmail.text.toString().trim().ifEmpty { null }

            if (swimmerName.isEmpty()) {
                Toast.makeText(this, "Please enter swimmer's name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Generate invitation
            lifecycleScope.launch(Dispatchers.IO) {
                val inviteCode = CodeGenerator.code(6)
                
                val invitation = TeamInvitation(
                    teamId = teamId,
                    inviteCode = inviteCode,
                    invitedEmail = swimmerEmail,
                    role = InvitationRole.SWIMMER,
                    createdBy = user.email
                )

                db.teamInvitationDao().insert(invitation)

                withContext(Dispatchers.Main) {
                    currentInviteCode = inviteCode
                    tvInviteCode.text = inviteCode
                    
                    val message = if (swimmerEmail != null) {
                        "Invitation created for $swimmerName ($swimmerEmail)"
                    } else {
                        "Invitation created for $swimmerName"
                    }
                    tvInviteMessage.text = message
                    
                    btnShare.isEnabled = true
                    Toast.makeText(this@InviteSwimmerActivity, "Invitation code generated!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnShare.setOnClickListener {
            it.animateClick()
            
            if (currentInviteCode == null) {
                Toast.makeText(this, "Please generate an invite first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val swimmerName = inputSwimmerName.text.toString().trim()
            val shareMessage = """
                🏊 You're invited to join $currentTeamName!
                
                Swimmer: $swimmerName
                Invitation Code: $currentInviteCode
                
                Download the app and use this code to join the team.
            """.trimIndent()

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Swimming Team Invitation")
                putExtra(Intent.EXTRA_TEXT, shareMessage)
            }

            startActivity(Intent.createChooser(shareIntent, "Share invitation via..."))
        }

        // Initially disable share button
        btnShare.isEnabled = false
    }
}
