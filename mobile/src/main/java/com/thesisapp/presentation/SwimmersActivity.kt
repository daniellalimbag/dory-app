package com.thesisapp.presentation

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SwimmersActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var enrollFab: ExtendedFloatingActionButton
    private lateinit var adapter: SwimmersAdapter
    private lateinit var db: AppDatabase

    // Using companion object to persist dummy data across activity restarts
    companion object {
        private val dummySwimmers = mutableListOf(
            Swimmer(
                id = 1,
                name = "Michael Phelps",
                birthday = "1985-06-30",
                height = 193f,
                weight = 91f,
                sex = "Male",
                wingspan = 201f
            ),
            Swimmer(
                id = 2,
                name = "Katie Ledecky",
                birthday = "1997-03-17",
                height = 183f,
                weight = 73f,
                sex = "Female",
                wingspan = 185f
            ),
            Swimmer(
                id = 3,
                name = "Caeleb Dressel",
                birthday = "1996-08-16",
                height = 191f,
                weight = 88f,
                sex = "Male",
                wingspan = 198f
            ),
            Swimmer(
                id = 4,
                name = "Emma McKeon",
                birthday = "1994-05-24",
                height = 180f,
                weight = 68f,
                sex = "Female",
                wingspan = 182f
            ),
            Swimmer(
                id = 5,
                name = "Adam Peaty",
                birthday = "1994-12-28",
                height = 188f,
                weight = 92f,
                sex = "Male",
                wingspan = 195f
            )
        )

        private var nextId = 6
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_swimmers)

        recyclerView = findViewById(R.id.swimmersRecyclerView)
        enrollFab = findViewById(R.id.enrollSwimmerFab)

        // Database instance ready for future use
        db = AppDatabase.getInstance(this)

        // Initialize adapter with callbacks
        adapter = SwimmersAdapter(
            swimmers = dummySwimmers.toMutableList(),
            onEditClick = { swimmer -> showEditDialog(swimmer) },
            onDeleteClick = { swimmer -> showDeleteConfirmation(swimmer) },
            onSwimmerClick = { swimmer -> openSwimmerProfile(swimmer) }
        )
        recyclerView.adapter = adapter

        enrollFab.setOnClickListener {
            // Navigate to enroll swimmer screen
            startActivity(Intent(this, TrackAddSwimmerActivity::class.java))
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
        // Load swimmers from database on background thread
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val dbSwimmers = db.swimmerDao().getAllSwimmers()
                withContext(Dispatchers.Main) {
                    if (dbSwimmers.isNotEmpty()) {
                        // Use database swimmers
                        adapter.updateSwimmers(dbSwimmers.toMutableList())
                    } else {
                        // Use dummy data if database is empty
                        adapter.updateSwimmers(dummySwimmers)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // Fallback to dummy data on error
                    adapter.updateSwimmers(dummySwimmers)
                    Toast.makeText(this@SwimmersActivity, "Using cached data", Toast.LENGTH_SHORT).show()
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
        // Update in dummy data list
        val index = dummySwimmers.indexOfFirst { it.id == swimmer.id }
        if (index != -1) {
            dummySwimmers[index] = swimmer
            adapter.updateSwimmers(dummySwimmers)
            Toast.makeText(this, "Swimmer updated!", Toast.LENGTH_SHORT).show()
        }

        // Database code ready for when you need it:
        // CoroutineScope(Dispatchers.IO).launch {
        //     db.swimmerDao().updateSwimmer(swimmer)
        //     withContext(Dispatchers.Main) {
        //         Toast.makeText(this@SwimmersActivity, "Swimmer updated!", Toast.LENGTH_SHORT).show()
        //     }
        // }
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
        // Remove from dummy data list
        dummySwimmers.remove(swimmer)
        adapter.removeSwimmer(swimmer)
        Toast.makeText(this, "${swimmer.name} deleted", Toast.LENGTH_SHORT).show()

        // Database code ready for when you need it:
        // CoroutineScope(Dispatchers.IO).launch {
        //     db.swimmerDao().deleteSwimmer(swimmer)
        //     withContext(Dispatchers.Main) {
        //         adapter.removeSwimmer(swimmer)
        //         Toast.makeText(this@SwimmersActivity, "${swimmer.name} deleted", Toast.LENGTH_SHORT).show()
        //     }
        // }
    }

    private fun openSwimmerProfile(swimmer: Swimmer) {
        val intent = Intent(this, SwimmerProfileActivity::class.java).apply {
            putExtra(SwimmerProfileActivity.EXTRA_SWIMMER, swimmer)
        }
        startActivity(intent)
    }
}