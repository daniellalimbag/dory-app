package com.thesisapp.presentation.activities

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.NumberPicker
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.non_dao.ExerciseCategory
import com.thesisapp.data.non_dao.Swimmer
import com.thesisapp.data.non_dao.TeamMembership
import com.thesisapp.utils.AuthManager
import com.thesisapp.utils.animateClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class CreateSwimmerProfileActivity : AppCompatActivity() {

    private lateinit var inputName: TextInputEditText
    private lateinit var inputSpecialty: TextInputEditText
    private lateinit var pickerMonth: NumberPicker
    private lateinit var pickerDay: NumberPicker
    private lateinit var pickerYear: NumberPicker
    private lateinit var inputHeight: TextInputEditText
    private lateinit var inputWeight: TextInputEditText
    private lateinit var inputWingspan: TextInputEditText
    private lateinit var radioGroupSex: RadioGroup
    private lateinit var btnSave: Button
    private lateinit var btnReturn: ImageButton

    private var teamId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_swimmer_profile)

        teamId = intent.getIntExtra("TEAM_ID", -1)

        if (teamId == -1) {
            Toast.makeText(this, "Invalid team data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val user = AuthManager.currentUser(this)
        
        // Check if swimmer already has a profile for this team
        if (user != null) {
            val existingSwimmerId = AuthManager.getLinkedSwimmerId(this, user.email, teamId)
            
            if (existingSwimmerId != null) {
                // User already has a profile for this team, go directly to MainActivity
                Toast.makeText(this, "✓ Already enrolled in this team!", Toast.LENGTH_SHORT).show()
                AuthManager.setCurrentTeamId(this, teamId)
                
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
                return
            }
            
            // Check if user has a profile in other teams - reuse it
            val otherTeams = AuthManager.getSwimmerTeams(this, user.email)
            if (otherTeams.isNotEmpty()) {
                val otherTeamId = otherTeams.first()
                val swimmerId = AuthManager.getLinkedSwimmerId(this, user.email, otherTeamId)
                
                if (swimmerId != null) {
                    // Reuse existing swimmer profile for new team
                    lifecycleScope.launch(Dispatchers.IO) {
                        val db = AppDatabase.getInstance(this@CreateSwimmerProfileActivity)
                        
                        // Check if already a member
                        val existingMembership = db.teamMembershipDao().getMembership(teamId, swimmerId)
                        
                        if (existingMembership == null) {
                            // Create team membership
                            db.teamMembershipDao().insert(
                                TeamMembership(
                                    teamId = teamId,
                                    swimmerId = swimmerId
                                )
                            )
                            AuthManager.linkSwimmerToTeam(this@CreateSwimmerProfileActivity, user.email, teamId, swimmerId)
                        }
                        
                        AuthManager.setCurrentTeamId(this@CreateSwimmerProfileActivity, teamId)
                        
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@CreateSwimmerProfileActivity,
                                "✓ Joined team successfully!",
                                Toast.LENGTH_LONG
                            ).show()
                            
                            val intent = Intent(this@CreateSwimmerProfileActivity, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                            finish()
                        }
                    }
                    return
                }
            }
        }

        // Initialize views
        inputName = findViewById(R.id.inputSwimmerName)
        inputSpecialty = findViewById(R.id.inputSpecialty)
        pickerMonth = findViewById(R.id.pickerMonth)
        pickerDay = findViewById(R.id.pickerDay)
        pickerYear = findViewById(R.id.pickerYear)
        inputHeight = findViewById(R.id.inputSwimmerHeight)
        inputWeight = findViewById(R.id.inputSwimmerWeight)
        inputWingspan = findViewById(R.id.inputSwimmerWingspan)
        radioGroupSex = findViewById(R.id.radioGroupSex)
        btnSave = findViewById(R.id.btnSave)
        btnReturn = findViewById(R.id.btnReturn)

        setupBirthdayPickers()

        val db = AppDatabase.getInstance(this)

        btnReturn.setOnClickListener {
            it.animateClick()
            finish()
        }

        btnSave.setOnClickListener {
            it.animateClick()

            val name = inputName.text.toString().trim()
            val specialty = inputSpecialty.text.toString().trim().takeIf { it.isNotEmpty() }
            val heightStr = inputHeight.text.toString().trim()
            val weightStr = inputWeight.text.toString().trim()
            val wingspanStr = inputWingspan.text.toString().trim()

            // Get birthday from pickers
            val month = pickerMonth.value
            val day = pickerDay.value
            val year = pickerYear.value
            val birthday = String.format("%04d-%02d-%02d", year, month, day)

            // Get sex from radio buttons
            val sex = when (radioGroupSex.checkedRadioButtonId) {
                R.id.radioMale -> "Male"
                R.id.radioFemale -> "Female"
                else -> ""
            }

            // Get swimmer type from radio buttons
            val radioGroupSwimmerType = findViewById<RadioGroup>(R.id.radioGroupSwimmerType)
            val swimmerType = when (radioGroupSwimmerType.checkedRadioButtonId) {
                R.id.radioSprint -> ExerciseCategory.SPRINT
                R.id.radioDistance -> ExerciseCategory.DISTANCE
                else -> ExerciseCategory.SPRINT
            }

            // Validation
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (sex.isEmpty()) {
                Toast.makeText(this, "Please select sex", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (heightStr.isEmpty() || weightStr.isEmpty() || wingspanStr.isEmpty()) {
                Toast.makeText(this, "Please fill in all measurements", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val height = heightStr.toFloatOrNull()
            val weight = weightStr.toFloatOrNull()
            val wingspan = wingspanStr.toFloatOrNull()

            if (height == null || weight == null || wingspan == null) {
                Toast.makeText(this, "Please enter valid numbers", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validate ranges
            if (height < 50 || height > 250) {
                Toast.makeText(this, "Height must be between 50-250 cm", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (weight < 20 || weight > 200) {
                Toast.makeText(this, "Weight must be between 20-200 kg", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (wingspan < 50 || wingspan > 300) {
                Toast.makeText(this, "Wingspan must be between 50-300 cm", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Create swimmer profile (team-independent)
            val swimmer = Swimmer(
                name = name,
                birthday = birthday,
                height = height,
                weight = weight,
                sex = sex,
                wingspan = wingspan,
                category = swimmerType,
                specialty = specialty
            )

            // Save to database
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val newId = db.swimmerDao().insertSwimmer(swimmer).toInt()
                    
                    // Create team membership (junction table entry)
                    db.teamMembershipDao().insert(
                        TeamMembership(
                            teamId = teamId,
                            swimmerId = newId
                        )
                    )
                    
                    // Link swimmer to user account
                    val user = AuthManager.currentUser(this@CreateSwimmerProfileActivity)
                    if (user != null) {
                        AuthManager.linkSwimmerToTeam(this@CreateSwimmerProfileActivity, user.email, teamId, newId)
                        AuthManager.setCurrentTeamId(this@CreateSwimmerProfileActivity, teamId)
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@CreateSwimmerProfileActivity,
                            "✓ Profile created! Welcome to the team!",
                            Toast.LENGTH_LONG
                        ).show()
                        
                        // Navigate to main activity
                        val intent = Intent(this@CreateSwimmerProfileActivity, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                        finish()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@CreateSwimmerProfileActivity,
                            "Error creating profile: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun setupBirthdayPickers() {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)

        // Month picker (1-12)
        val months = arrayOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        )
        pickerMonth.minValue = 1
        pickerMonth.maxValue = 12
        pickerMonth.displayedValues = months
        pickerMonth.value = 1
        pickerMonth.wrapSelectorWheel = true

        // Day picker (1-31)
        pickerDay.minValue = 1
        pickerDay.maxValue = 31
        pickerDay.value = 1
        pickerDay.wrapSelectorWheel = true

        // Year picker (1900 - current year)
        pickerYear.minValue = 1900
        pickerYear.maxValue = currentYear
        pickerYear.value = currentYear - 18 // Default to 18 years ago
        pickerYear.wrapSelectorWheel = false
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
