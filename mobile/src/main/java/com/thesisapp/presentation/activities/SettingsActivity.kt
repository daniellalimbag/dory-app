package com.thesisapp.presentation.activities

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.utils.ThemeManager
import com.thesisapp.utils.animateClick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var btnReturn: ImageButton
    private lateinit var btnProfile: LinearLayout
    private lateinit var btnPersonalBests: LinearLayout
    private lateinit var btnImport: LinearLayout
    private lateinit var btnExport: LinearLayout
    private lateinit var btnClearAllData: LinearLayout
    private lateinit var btnClearAll: LinearLayout
    private lateinit var rowDarkMode: LinearLayout
    private lateinit var switchDarkMode: SwitchMaterial

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings)

        btnReturn = findViewById(R.id.btnReturn)
        btnProfile = findViewById(R.id.btnProfile)
        btnPersonalBests = findViewById(R.id.btnPersonalBests)
        btnImport = findViewById(R.id.btnImport)
        btnExport = findViewById(R.id.btnExport)
        btnClearAllData = findViewById(R.id.btnClearAllData)
        btnClearAll = findViewById(R.id.btnClearAll)
        rowDarkMode = findViewById(R.id.rowDarkMode)
        switchDarkMode = findViewById(R.id.switchDarkMode)

        switchDarkMode.isChecked = ThemeManager.isDarkModeEnabled(this)
        rowDarkMode.setOnClickListener {
            it.animateClick()
            switchDarkMode.isChecked = !switchDarkMode.isChecked
        }
        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            ThemeManager.setDarkModeEnabled(this, isChecked)
        }

        btnProfile.setOnClickListener {
            it.animateClick()
            val intent = Intent(this, SettingsProfileActivity::class.java)
            startActivity(intent)
        }

        btnPersonalBests.setOnClickListener {
            it.animateClick()
            // Get current swimmer ID
            lifecycleScope.launch(Dispatchers.IO) {
                val db = AppDatabase.getInstance(this@SettingsActivity)
                val user = com.thesisapp.utils.AuthManager.currentUser(this@SettingsActivity)
                val teamId = com.thesisapp.utils.AuthManager.currentTeamId(this@SettingsActivity)
                val swimmerId = when (user?.role) {
                    com.thesisapp.utils.UserRole.SWIMMER -> {
                        com.thesisapp.utils.AuthManager.getLinkedSwimmerId(this@SettingsActivity, user.email, teamId)
                    }
                    com.thesisapp.utils.UserRole.COACH -> db.swimmerDao().getAllSwimmers().firstOrNull()?.id
                    else -> null
                }
                
                withContext(Dispatchers.Main) {
                    if (swimmerId != null) {
                        val intent = Intent(this@SettingsActivity, PersonalBestsActivity::class.java)
                        intent.putExtra("SWIMMER_ID", swimmerId)
                        startActivity(intent)
                    } else {
                        Toast.makeText(this@SettingsActivity, "No swimmer profile found", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnImport.setOnClickListener {
            it.animateClick()
            val intent = Intent(this, SettingsImportActivity::class.java)
            startActivity(intent)
        }

        btnExport.setOnClickListener {
            it.animateClick()
            val intent = Intent(this, SettingsExportActivity::class.java)
            startActivity(intent)
        }

        btnClearAllData.setOnClickListener {
            it.animateClick()
            showClearDataPopup()
        }

        btnClearAll.setOnClickListener {
            it.animateClick()
            showClearAllPopup()
        }

        btnReturn.setOnClickListener {
            it.animateClick()
            finish()
        }
    }

    private fun showClearDataPopup() {
        val alertDialog = AlertDialog.Builder(this)
            .setTitle("Confirm Clear All SWIMMING Data")
            .setMessage("This will clear ALL SWIM DATA from the database.")
            .setCancelable(false)
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("OK") { dialog, _ ->
                val db = AppDatabase.getInstance(this)

                CoroutineScope(Dispatchers.IO).launch {
                    db.swimDataDao().clearAll()

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SettingsActivity, "Swim data cleared", Toast.LENGTH_SHORT).show()
                    }
                }

                dialog.dismiss()
            }
            .create()

        alertDialog.setOnShowListener {
            // Access buttons AFTER dialog is shown
            val btnCancel = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            val btnOK = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)

            btnCancel.setOnClickListener {
                it.animateClick()
                alertDialog.dismiss()
            }

            btnOK.setOnClickListener {
                it.animateClick()
                val db = AppDatabase.getInstance(this)

                CoroutineScope(Dispatchers.IO).launch {
                    db.swimDataDao().clearAll()
                    db.mlResultDao().clearAll()

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SettingsActivity, "Swim data cleared", Toast.LENGTH_SHORT).show()
                    }
                }

                alertDialog.dismiss()
            }
        }

        alertDialog.show()
    }

    private fun showClearAllPopup() {
        val alertDialog = AlertDialog.Builder(this)
            .setTitle("Confirm Clear ALL Data")
            .setMessage("This will clear ALL DATA (swimmer's information and swim data) from the database.")
            .setCancelable(false)
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("OK") { dialog, _ ->
                val db = AppDatabase.getInstance(this)

                CoroutineScope(Dispatchers.IO).launch {
                    db.swimmerDao().clearAll()
                    db.swimDataDao().clearAll()
                    db.mlResultDao().clearAll()

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SettingsActivity, "All data cleared", Toast.LENGTH_SHORT).show()
                    }
                }

                dialog.dismiss()
            }
            .create()

        alertDialog.setOnShowListener {
            // Access buttons AFTER dialog is shown
            val btnCancel = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            val btnOK = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)

            btnCancel.setOnClickListener {
                it.animateClick()
                alertDialog.dismiss()
            }

            btnOK.setOnClickListener {
                it.animateClick()
                val db = AppDatabase.getInstance(this)

                CoroutineScope(Dispatchers.IO).launch {
                    db.swimmerDao().clearAll()
                    db.swimDataDao().clearAll()
                    db.mlResultDao().clearAll()

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SettingsActivity, "All data cleared", Toast.LENGTH_SHORT).show()
                    }
                }

                alertDialog.dismiss()
            }
        }

        alertDialog.show()
    }
}