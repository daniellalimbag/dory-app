package com.thesisapp.presentation.activities

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.repository.TeamRepository
import com.thesisapp.utils.AuthManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import javax.inject.Inject

@AndroidEntryPoint
class EditTeamActivity : AppCompatActivity() {

    @Inject lateinit var teamRepository: TeamRepository

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
        setContentView(R.layout.activity_edit_team)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val inputName = findViewById<EditText>(R.id.inputTeamName)
        val imgLogo = findViewById<ImageView>(R.id.imgTeamLogo)
        val btnPickLogo = findViewById<Button>(R.id.btnPickLogo)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val progress = findViewById<ProgressBar>(R.id.progressSave)

        btnBack.setOnClickListener { finish() }

        val teamId = AuthManager.currentTeamId(this)
        if (teamId == null) {
            Toast.makeText(this, "No team selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(this@EditTeamActivity)
            val team = db.teamDao().getById(teamId)
            withContext(Dispatchers.Main) {
                if (team == null) {
                    Toast.makeText(this@EditTeamActivity, "Team not found", Toast.LENGTH_SHORT).show()
                    finish()
                    return@withContext
                }

                inputName.setText(team.name)

                val logoPath = team.logoPath
                if (!logoPath.isNullOrBlank()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        runCatching {
                            val signedUrl = teamRepository.getTeamLogoSignedUrl(logoPath)
                            val bytes = URL(signedUrl).openStream().use { it.readBytes() }
                            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            withContext(Dispatchers.Main) { imgLogo.setImageBitmap(bmp) }
                        }
                    }
                }
            }
        }

        btnPickLogo.setOnClickListener {
            pickImage.launch("image/*")
        }

        btnSave.setOnClickListener {
            val newName = inputName.text.toString().trim()
            if (newName.isBlank()) {
                Toast.makeText(this, "Enter team name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progress.visibility = android.view.View.VISIBLE
            btnSave.isEnabled = false
            btnPickLogo.isEnabled = false
            btnBack.isEnabled = false

            lifecycleScope.launch {
                runCatching {
                    teamRepository.updateTeamName(teamId = teamId.toLong(), newName = newName)

                    var newLogoPath: String? = null
                    val logoBytes = selectedLogoBytes
                    if (logoBytes != null) {
                        newLogoPath = teamRepository.uploadTeamLogo(teamId = teamId.toLong(), byteArray = logoBytes)
                    }

                    withContext(Dispatchers.IO) {
                        val db = AppDatabase.getInstance(this@EditTeamActivity)
                        val existing = db.teamDao().getById(teamId)
                        if (existing != null) {
                            db.teamDao().update(
                                existing.copy(
                                    name = newName,
                                    logoPath = newLogoPath ?: existing.logoPath
                                )
                            )
                        }
                    }
                }.onSuccess {
                    Toast.makeText(this@EditTeamActivity, "Team updated", Toast.LENGTH_SHORT).show()
                    finish()
                }.onFailure { e ->
                    Toast.makeText(this@EditTeamActivity, e.message ?: "Failed to update team", Toast.LENGTH_LONG).show()
                }

                progress.visibility = android.view.View.GONE
                btnSave.isEnabled = true
                btnPickLogo.isEnabled = true
                btnBack.isEnabled = true
            }
        }
    }

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
