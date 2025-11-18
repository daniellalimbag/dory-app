package com.thesisapp.presentation.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.non_dao.Team
import com.thesisapp.utils.AuthManager
import com.thesisapp.utils.CodeGenerator
import com.thesisapp.utils.animateClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CreateTeamActivity : AppCompatActivity() {

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

        var currentCode = CodeGenerator.code(6)
        txtCode.text = currentCode

        btnCopyCode.setOnClickListener {
            it.animateClick()
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Team Code", currentCode)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        btnGenerate.setOnClickListener {
            it.animateClick()
            currentCode = CodeGenerator.code(6)
            txtCode.text = currentCode
        }

        btnBack.setOnClickListener { finish() }

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
            lifecycleScope.launch(Dispatchers.IO) {
                val db = AppDatabase.getInstance(this@CreateTeamActivity)
                val id = db.teamDao().insert(Team(name = name, joinCode = currentCode)).toInt()
                withContext(Dispatchers.Main) {
                    val user = AuthManager.currentUser(this@CreateTeamActivity)
                    if (user != null) {
                        AuthManager.addCoachTeam(this@CreateTeamActivity, user.email, id)
                        AuthManager.addCoachToTeam(this@CreateTeamActivity, id, user.email)
                        AuthManager.setCurrentTeamId(this@CreateTeamActivity, id)
                    }
                    Toast.makeText(this@CreateTeamActivity, "Team created. Code: $currentCode", Toast.LENGTH_LONG).show()
                    // Start ManageCoachesActivity after team creation
                    val intent = Intent(this@CreateTeamActivity, ManageCoachesActivity::class.java)
                    startActivity(intent)
                    finish()
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