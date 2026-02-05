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
import com.thesisapp.utils.LocalUserBootstrapper
import com.thesisapp.utils.animateClick
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class CreateSwimmerProfileActivity : AppCompatActivity() {

    @Inject
    lateinit var supabase: SupabaseClient

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Serializable
    private data class RemoteSwimmerRow(
        val id: Int,
        @SerialName("user_id") val userId: String,
        val name: String,
        val birthday: String,
        val height: Float,
        val weight: Float,
        val sex: String,
        val wingspan: Float,
        val category: String,
        val specialty: String? = null
    )

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

        val user = AuthManager.currentUser(this)

        // If this was launched as part of joining a team, keep the old fast-path behavior.
        if (teamId != -1 && user != null) {
            val existingSwimmerId = AuthManager.getLinkedSwimmerId(this, user.email, teamId)

            if (existingSwimmerId != null) {
                Toast.makeText(this, "✓ Already enrolled in this team!", Toast.LENGTH_SHORT).show()
                AuthManager.setCurrentTeamId(this, teamId)

                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
                return
            }

            val otherTeams = AuthManager.getSwimmerTeams(this, user.email)
            if (otherTeams.isNotEmpty()) {
                val otherTeamId = otherTeams.first()
                val swimmerId = AuthManager.getLinkedSwimmerId(this, user.email, otherTeamId)

                if (swimmerId != null) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val db = AppDatabase.getInstance(this@CreateSwimmerProfileActivity)

                        val userId = LocalUserBootstrapper.ensureRoomUserForAuth(this@CreateSwimmerProfileActivity, db)
                        if (userId != null) {
                            db.swimmerDao().setUserIdForSwimmer(swimmerId = swimmerId, userId = userId)
                        }

                        val resolvedAuthUserId = userId
                        val localSwimmer = db.swimmerDao().getById(swimmerId)

                        val remoteId = if (!resolvedAuthUserId.isNullOrBlank() && localSwimmer != null) {
                            runCatching {
                                upsertRemoteSwimmer(userId = resolvedAuthUserId, swimmer = localSwimmer)
                            }.getOrNull()
                        } else {
                            null
                        }

                        val effectiveSwimmerId = remoteId ?: swimmerId

                        if (remoteId != null && remoteId != swimmerId && localSwimmer != null) {
                            db.swimmerDao().insertSwimmer(localSwimmer.copy(id = remoteId))
                            AuthManager.linkSwimmerToTeam(this@CreateSwimmerProfileActivity, user.email, teamId, remoteId)
                        }

                        val existingMembership = db.teamMembershipDao().getMembership(teamId, effectiveSwimmerId)

                        if (existingMembership == null) {
                            db.teamMembershipDao().insert(
                                TeamMembership(
                                    teamId = teamId,
                                    swimmerId = effectiveSwimmerId
                                )
                            )

                            val remoteUserId = resolvedAuthUserId
                            if (!remoteUserId.isNullOrBlank()) {
                                runCatching {
                                    ensureRemoteMembership(teamId = teamId, swimmerUserId = remoteUserId)
                                }
                            }

                            AuthManager.linkSwimmerToTeam(this@CreateSwimmerProfileActivity, user.email, teamId, effectiveSwimmerId)
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
            val authUser = AuthManager.currentUser(this)
            val initialUserId = if (authUser != null) null else null
            val swimmer = Swimmer(
                userId = initialUserId ?: "",
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
                    val resolvedUserId = if (authUser != null) {
                        LocalUserBootstrapper.ensureRoomUserForAuth(this@CreateSwimmerProfileActivity, db)
                    } else {
                        LocalUserBootstrapper.createStandaloneSwimmerUser(db)
                    }

                    if (authUser != null && resolvedUserId.isNullOrBlank()) {
                        throw IllegalStateException("Unable to resolve local user id")
                    }

                    val finalUserId = resolvedUserId ?: swimmer.userId
                    if (finalUserId.isBlank()) {
                        throw IllegalStateException("Unable to resolve local user id")
                    }

                    // If authenticated, write to Supabase first so Room IDs match remote IDs.
                    val remoteSwimmerId: Int? = if (authUser != null) {
                        upsertRemoteSwimmer(userId = finalUserId, swimmer = swimmer)
                    } else {
                        null
                    }

                    if (authUser != null && remoteSwimmerId == null) {
                        throw IllegalStateException("Unable to create/update swimmer profile in Supabase")
                    }

                    val existing = db.swimmerDao().getByUserId(finalUserId)

                    val resolvedLocalId = remoteSwimmerId ?: existing?.id
                    val newId = if (resolvedLocalId != null) {
                        val upsert = swimmer.copy(id = resolvedLocalId, userId = finalUserId)
                        db.swimmerDao().insertSwimmer(upsert)
                        resolvedLocalId
                    } else {
                        db.swimmerDao().insertSwimmer(swimmer.copy(userId = finalUserId)).toInt()
                    }
                    
                    // If this screen was launched for joining a specific team, create membership.
                    if (teamId != -1) {
                        db.teamMembershipDao().insert(
                            TeamMembership(
                                teamId = teamId,
                                swimmerId = newId
                            )
                        )

                        if (authUser != null) {
                            runCatching {
                                ensureRemoteMembership(teamId = teamId, swimmerUserId = finalUserId)
                            }
                        }
                    }
                    
                    // Link swimmer to user account
                    val user = AuthManager.currentUser(this@CreateSwimmerProfileActivity)
                    if (user != null && teamId != -1) {
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

    private suspend fun upsertRemoteSwimmer(userId: String, swimmer: Swimmer): Int? {
        // Try to find existing swimmer row for this auth user
        val existingJson = supabase.from("swimmers").select {
            filter { eq("user_id", userId) }
            limit(1)
        }.data

        val existing = runCatching {
            json.decodeFromString<List<RemoteSwimmerRow>>(existingJson).firstOrNull()
        }.getOrNull()

        val payload = buildJsonObject {
            put("user_id", userId)
            put("name", swimmer.name)
            put("birthday", swimmer.birthday)
            put("height", swimmer.height)
            put("weight", swimmer.weight)
            put("sex", swimmer.sex)
            put("wingspan", swimmer.wingspan)
            put("category", swimmer.category.name)
            swimmer.specialty?.let { put("specialty", it) }
        }

        if (existing == null) {
            supabase.from("swimmers").insert(payload)
        } else {
            supabase.from("swimmers").update(payload) {
                filter { eq("id", existing.id) }
            }
        }

        val updatedJson = supabase.from("swimmers").select {
            filter { eq("user_id", userId) }
            limit(1)
        }.data

        return json.decodeFromString<List<RemoteSwimmerRow>>(updatedJson).firstOrNull()?.id
    }

    private suspend fun ensureRemoteMembership(teamId: Int, swimmerUserId: String) {
        val existingJson = supabase.from("team_memberships").select {
            filter {
                eq("team_id", teamId)
                eq("user_id", swimmerUserId)
            }
            limit(1)
        }.data

        val exists = runCatching { existingJson.trim().startsWith("[") && existingJson != "[]" }
            .getOrDefault(false)

        if (!exists) {
            val payload = buildJsonObject {
                put("team_id", teamId)
                put("user_id", swimmerUserId)
                put("role", "swimmer")
            }
            supabase.from("team_memberships").insert(payload)
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
