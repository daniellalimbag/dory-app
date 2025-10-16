package com.thesisapp.presentation

import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.NumberPicker
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.Swimmer
import com.thesisapp.utils.AuthManager
import com.thesisapp.utils.CodeGenerator
import com.thesisapp.utils.animateClick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class TrackAddSwimmerActivity : AppCompatActivity() {

    private lateinit var inputName: TextInputEditText
    private lateinit var pickerMonth: NumberPicker
    private lateinit var pickerDay: NumberPicker
    private lateinit var pickerYear: NumberPicker
    private lateinit var inputHeight: TextInputEditText
    private lateinit var inputWeight: TextInputEditText
    private lateinit var inputWingspan: TextInputEditText
    private lateinit var radioGroupSex: RadioGroup
    private lateinit var btnSave: Button
    private lateinit var btnReturn: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.track_add_swimmer)

        // Initialize views
        inputName = findViewById(R.id.inputSwimmerName)
        pickerMonth = findViewById(R.id.pickerMonth)
        pickerDay = findViewById(R.id.pickerDay)
        pickerYear = findViewById(R.id.pickerYear)
        inputHeight = findViewById(R.id.inputSwimmerHeight)
        inputWeight = findViewById(R.id.inputSwimmerWeight)
        inputWingspan = findViewById(R.id.inputSwimmerWingspan)
        radioGroupSex = findViewById(R.id.radioGroupSex)
        btnSave = findViewById(R.id.btnSave)
        btnReturn = findViewById(R.id.btnReturn)

        // Setup NumberPickers (scrolling like combination lock)
        setupBirthdayPickers()

        val db = AppDatabase.getInstance(this)
        val teamId = AuthManager.currentTeamId(this)
        if (teamId == null) {
            Toast.makeText(this, "No team selected. Create or select a team first.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        btnSave.setOnClickListener {
            it.animateClick()

            val name = inputName.text.toString().trim()
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

            // Validation
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter swimmer's name", Toast.LENGTH_SHORT).show()
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

            // Parse numeric values
            val height = heightStr.toFloatOrNull()
            val weight = weightStr.toFloatOrNull()
            val wingspan = wingspanStr.toFloatOrNull()

            if (height == null || weight == null || wingspan == null) {
                Toast.makeText(this, "Please enter valid numbers for measurements", Toast.LENGTH_SHORT).show()
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

            val code = CodeGenerator.code(6)

            // Create swimmer object
            val swimmer = Swimmer(
                name = name,
                teamId = teamId,
                birthday = birthday,
                height = height,
                weight = weight,
                sex = sex,
                wingspan = wingspan,
                code = code
            )

            // Save to database
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val newId = db.swimmerDao().insertSwimmer(swimmer).toInt()

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@TrackAddSwimmerActivity,
                            "✓ ${swimmer.name} enrolled successfully!",
                            Toast.LENGTH_LONG
                        ).show()

                        val intent = android.content.Intent(this@TrackAddSwimmerActivity, TrackSwimmerSuccessActivity::class.java)
                        intent.putExtra("SWIMMER_CODE", code)
                        startActivity(intent)
                        finish()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@TrackAddSwimmerActivity,
                            "Error saving swimmer: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        btnReturn.setOnClickListener {
            it.animateClick()
            finish()
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

        // Year picker (1950 to current year)
        pickerYear.minValue = 1950
        pickerYear.maxValue = currentYear
        pickerYear.value = 2000
        pickerYear.wrapSelectorWheel = false

        // Update day picker when month changes (handle different month lengths)
        pickerMonth.setOnValueChangedListener { _, _, newMonth ->
            updateDayPicker(newMonth, pickerYear.value)
        }

        pickerYear.setOnValueChangedListener { _, _, newYear ->
            updateDayPicker(pickerMonth.value, newYear)
        }
    }

    private fun updateDayPicker(month: Int, year: Int) {
        val daysInMonth = when (month) {
            2 -> if (isLeapYear(year)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }

        val currentDay = pickerDay.value
        pickerDay.maxValue = daysInMonth

        // Adjust current day if it exceeds the new max
        if (currentDay > daysInMonth) {
            pickerDay.value = daysInMonth
        }
    }

    private fun isLeapYear(year: Int): Boolean {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }
}