package com.thesisapp.presentation.activities

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.utils.AuthManager
import com.thesisapp.utils.UserRole
import com.thesisapp.utils.animateClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter

class SettingsProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_profile)

        val txtName = findViewById<TextView>(R.id.txtName)
        val txtBirthday = findViewById<TextView>(R.id.txtBirthday)
        val txtHeight = findViewById<TextView>(R.id.txtHeight)
        val txtWeight = findViewById<TextView>(R.id.txtWeight)
        val txtWingspan = findViewById<TextView>(R.id.txtWingspan)
        val txtSex = findViewById<TextView>(R.id.txtSex)

        val btnReturn = findViewById<ImageButton>(R.id.btnReturn)
        btnReturn.setOnClickListener {
            it.animateClick()
            finish()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(applicationContext)
            val user = AuthManager.currentUser(this@SettingsProfileActivity)
            val teamId = AuthManager.currentTeamId(this@SettingsProfileActivity)
            val swimmer = when (user?.role) {
                UserRole.SWIMMER -> {
                    val swimmerId = AuthManager.getLinkedSwimmerId(this@SettingsProfileActivity, user.email, teamId)
                    swimmerId?.let { db.swimmerDao().getById(it) }
                }
                UserRole.COACH -> db.swimmerDao().getAllSwimmers().firstOrNull()
                else -> null
            }

            withContext(Dispatchers.Main) {
                if (swimmer != null) {
                    txtName.text = swimmer.name
                    txtBirthday.text = "${swimmer.birthday} (Age: ${calculateAge(swimmer.birthday)})"
                    txtHeight.text = "${swimmer.height} cm"
                    txtWeight.text = "${swimmer.weight} kg"
                    txtWingspan.text = "${swimmer.wingspan} cm"
                    txtSex.text = swimmer.sex
                } else {
                    txtName.text = "No swimmer profile found"
                    txtBirthday.text = ""
                    txtHeight.text = ""
                    txtWeight.text = ""
                    txtWingspan.text = ""
                    txtSex.text = ""
                }
            }
        }
    }

    private fun calculateAge(birthday: String): Int {
        return try {
            val birthDate = LocalDate.parse(birthday, DateTimeFormatter.ISO_LOCAL_DATE)
            val currentDate = LocalDate.now()
            Period.between(birthDate, currentDate).years
        } catch (e: Exception) {
            0
        }
    }
}