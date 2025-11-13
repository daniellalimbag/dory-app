package com.thesisapp.presentation

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.Swimmer
import com.thesisapp.utils.AuthManager
import com.thesisapp.utils.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SwimmersActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var enrollFab: ExtendedFloatingActionButton
    private lateinit var adapter: SwimmersAdapter
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_swimmers)

        recyclerView = findViewById(R.id.swimmersRecyclerView)
        enrollFab = findViewById(R.id.enrollSwimmerFab)

        // Database instance ready for future use
        db = AppDatabase.getInstance(this)

        val isCoach = AuthManager.currentUser(this)?.role == UserRole.COACH
        val teamId = AuthManager.currentTeamId(this) ?: -1

        // Initialize adapter with callbacks
        adapter = SwimmersAdapter(
            swimmers = mutableListOf(),
            teamId = teamId,
            onEditClick = { swimmer -> if (isCoach) showEditDialog(swimmer) },
            onDeleteClick = { swimmer -> if (isCoach) showDeleteConfirmation(swimmer) },
            onSwimmerClick = { swimmer -> openSwimmerProfile(swimmer) },
            isCoach = isCoach
        )
        recyclerView.adapter = adapter

        // Update FAB text based on role
        val role = AuthManager.currentUser(this)?.role
        if (role == UserRole.COACH) {
            enrollFab.text = "Share Team Code"
        } else {
            enrollFab.text = "Join Team"
        }

        enrollFab.setOnClickListener {
            if (role == UserRole.COACH) {
                // Coaches share the team join code
                showTeamCodeDialog()
            } else {
                // Swimmers can enroll via invitation code
                startActivity(Intent(this, EnrollViaCodeActivity::class.java))
            }
        }

        // Add back button functionality
        findViewById<ImageButton>(R.id.btnBack)?.setOnClickListener {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh the list when returning to this activity
        loadSwimmers()
    }

    private fun loadSwimmers() {
        val user = AuthManager.currentUser(this)
        val teamId = AuthManager.currentTeamId(this)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val swimmers: List<Swimmer> = when (user?.role) {
                    UserRole.COACH -> if (teamId != null) db.teamMembershipDao().getSwimmersForTeam(teamId) else emptyList()
                    UserRole.SWIMMER -> {
                        val linked = AuthManager.getLinkedSwimmerId(this@SwimmersActivity, user.email, teamId)
                        if (linked != null) listOfNotNull(db.swimmerDao().getById(linked)) else emptyList()
                    }
                    else -> emptyList()
                }
                withContext(Dispatchers.Main) {
                    adapter.updateSwimmers(swimmers)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    adapter.updateSwimmers(emptyList())
                    Toast.makeText(this@SwimmersActivity, "Unable to load swimmers", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showEditDialog(swimmer: Swimmer) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_swimmer, null)

        val editName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editSwimmerName)
        val editPickerMonth = dialogView.findViewById<android.widget.NumberPicker>(R.id.editPickerMonth)
        val editPickerDay = dialogView.findViewById<android.widget.NumberPicker>(R.id.editPickerDay)
        val editPickerYear = dialogView.findViewById<android.widget.NumberPicker>(R.id.editPickerYear)
        val editHeight = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editSwimmerHeight)
        val editWeight = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editSwimmerWeight)
        val editRadioGroupSex = dialogView.findViewById<android.widget.RadioGroup>(R.id.editRadioGroupSex)
        val editRadioMale = dialogView.findViewById<android.widget.RadioButton>(R.id.editRadioMale)
        val editRadioFemale = dialogView.findViewById<android.widget.RadioButton>(R.id.editRadioFemale)
        val editWingspan = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editSwimmerWingspan)

        // Pre-fill with current values
        editName.setText(swimmer.name)
        editHeight.setText(swimmer.height.toString())
        editWeight.setText(swimmer.weight.toString())
        editWingspan.setText(swimmer.wingspan.toString())

        // Set sex radio button
        if (swimmer.sex.equals("Male", ignoreCase = true)) {
            editRadioMale.isChecked = true
        } else {
            editRadioFemale.isChecked = true
        }

        // Setup birthday pickers
        setupBirthdayPickers(editPickerMonth, editPickerDay, editPickerYear, swimmer.birthday)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                // Get birthday from pickers
                val month = editPickerMonth.value
                val day = editPickerDay.value
                val year = editPickerYear.value
                val birthday = String.format("%04d-%02d-%02d", year, month, day)

                // Get sex from radio buttons
                val sex = when (editRadioGroupSex.checkedRadioButtonId) {
                    R.id.editRadioMale -> "Male"
                    R.id.editRadioFemale -> "Female"
                    else -> swimmer.sex // fallback to original if nothing selected
                }

                val updatedSwimmer = swimmer.copy(
                    name = editName.text.toString(),
                    birthday = birthday,
                    height = editHeight.text.toString().toFloatOrNull() ?: swimmer.height,
                    weight = editWeight.text.toString().toFloatOrNull() ?: swimmer.weight,
                    sex = sex,
                    wingspan = editWingspan.text.toString().toFloatOrNull() ?: swimmer.wingspan
                )
                updateSwimmer(updatedSwimmer)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupBirthdayPickers(
        pickerMonth: android.widget.NumberPicker,
        pickerDay: android.widget.NumberPicker,
        pickerYear: android.widget.NumberPicker,
        currentBirthday: String
    ) {
        val calendar = java.util.Calendar.getInstance()
        val currentYear = calendar.get(java.util.Calendar.YEAR)

        // Parse current birthday
        val dateParts = currentBirthday.split("-")
        val birthYear = dateParts.getOrNull(0)?.toIntOrNull() ?: 2000
        val birthMonth = dateParts.getOrNull(1)?.toIntOrNull() ?: 1
        val birthDay = dateParts.getOrNull(2)?.toIntOrNull() ?: 1

        // Month picker (1-12)
        val months = arrayOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        )
        pickerMonth.minValue = 1
        pickerMonth.maxValue = 12
        pickerMonth.displayedValues = months
        pickerMonth.value = birthMonth
        pickerMonth.wrapSelectorWheel = true

        // Day picker (1-31)
        pickerDay.minValue = 1
        pickerDay.maxValue = 31
        pickerDay.value = birthDay
        pickerDay.wrapSelectorWheel = true

        // Year picker (1950 to current year)
        pickerYear.minValue = 1950
        pickerYear.maxValue = currentYear
        pickerYear.value = birthYear
        pickerYear.wrapSelectorWheel = false

        // Update day picker when month/year changes
        pickerMonth.setOnValueChangedListener { _, _, newMonth ->
            updateDayPickerForEdit(pickerDay, newMonth, pickerYear.value)
        }

        pickerYear.setOnValueChangedListener { _, _, newYear ->
            updateDayPickerForEdit(pickerDay, pickerMonth.value, newYear)
        }
    }

    private fun updateDayPickerForEdit(pickerDay: android.widget.NumberPicker, month: Int, year: Int) {
        val daysInMonth = when (month) {
            2 -> if (isLeapYear(year)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }

        val currentDay = pickerDay.value
        pickerDay.maxValue = daysInMonth

        if (currentDay > daysInMonth) {
            pickerDay.value = daysInMonth
        }
    }

    private fun isLeapYear(year: Int): Boolean {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }

    private fun updateSwimmer(swimmer: Swimmer) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                db.swimmerDao().updateSwimmer(swimmer)
                withContext(Dispatchers.Main) {
                    loadSwimmers()
                    Toast.makeText(this@SwimmersActivity, "Swimmer updated!", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SwimmersActivity, "Update failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showDeleteConfirmation(swimmer: Swimmer) {
        AlertDialog.Builder(this)
            .setTitle("Delete Swimmer")
            .setMessage("Are you sure you want to delete ${swimmer.name}?")
            .setPositiveButton("Delete") { _, _ ->
                deleteSwimmer(swimmer)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSwimmer(swimmer: Swimmer) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                db.swimmerDao().deleteSwimmer(swimmer)
                withContext(Dispatchers.Main) {
                    loadSwimmers()
                    Toast.makeText(this@SwimmersActivity, "${swimmer.name} deleted", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SwimmersActivity, "Delete failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openSwimmerProfile(swimmer: Swimmer) {
        val intent = Intent(this, SwimmerProfileActivity::class.java).apply {
            putExtra(SwimmerProfileActivity.EXTRA_SWIMMER, swimmer)
        }
        startActivity(intent)
    }

    private fun showTeamCodeDialog() {
        val teamId = AuthManager.currentTeamId(this)
        if (teamId == null) {
            Toast.makeText(this, "No team selected", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val team = db.teamDao().getById(teamId)
            withContext(Dispatchers.Main) {
                if (team == null) {
                    Toast.makeText(this@SwimmersActivity, "Team not found", Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                val message = "Share this code with swimmers to join ${team.name}:\n\n${team.joinCode}\n\nSwimmers should:\n1. Open the app\n2. Click 'Join Team'\n3. Enter this code\n4. Fill out their profile"

                AlertDialog.Builder(this@SwimmersActivity)
                    .setTitle("Team Invitation Code")
                    .setMessage(message)
                    .setPositiveButton("Copy Code") { _, _ ->
                        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Team Code", team.joinCode)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this@SwimmersActivity, "Code copied!", Toast.LENGTH_SHORT).show()
                    }
                    .setNeutralButton("Share") { _, _ ->
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Join ${team.name} on DORY")
                            putExtra(Intent.EXTRA_TEXT, "Join my swimming team on DORY!\n\nTeam: ${team.name}\nCode: ${team.joinCode}\n\nDownload DORY and enter this code to join.")
                        }
                        startActivity(Intent.createChooser(shareIntent, "Share team code"))
                    }
                    .setNegativeButton("Close", null)
                    .show()
            }
        }
    }
}