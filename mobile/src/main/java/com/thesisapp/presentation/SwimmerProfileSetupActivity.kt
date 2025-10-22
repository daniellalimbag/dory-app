package com.thesisapp.presentation

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.NumberPicker
import android.widget.RadioButton
import android.widget.RadioGroup
import com.google.android.material.textfield.TextInputEditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.thesisapp.R
import com.thesisapp.utils.AuthManager
import com.thesisapp.utils.animateClick
import java.util.Calendar

class SwimmerProfileSetupActivity : AppCompatActivity() {

    private lateinit var inputName: TextInputEditText
    private lateinit var pickerMonth: NumberPicker
    private lateinit var pickerDay: NumberPicker
    private lateinit var pickerYear: NumberPicker
    private lateinit var inputHeight: TextInputEditText
    private lateinit var inputWeight: TextInputEditText
    private lateinit var inputWingspan: TextInputEditText
    private lateinit var radioGroupSex: RadioGroup
    private lateinit var btnSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_swimmer_profile_setup)

        inputName = findViewById(R.id.inputName)
        pickerMonth = findViewById(R.id.pickerMonth)
        pickerDay = findViewById(R.id.pickerDay)
        pickerYear = findViewById(R.id.pickerYear)
        inputHeight = findViewById(R.id.inputHeight)
        inputWeight = findViewById(R.id.inputWeight)
        inputWingspan = findViewById(R.id.inputWingspan)
        radioGroupSex = findViewById(R.id.radioGroupSex)
        btnSave = findViewById(R.id.btnSave)
        findViewById<ImageButton>(R.id.btnBack)?.setOnClickListener { finish() }

        setupBirthdayPickers()
        loadExistingProfileIfAny()

        btnSave.setOnClickListener {
            it.animateClick()
            if (saveProfile()) {
                Toast.makeText(this, "Profile saved", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun prefs() = getSharedPreferences("profile_prefs", Context.MODE_PRIVATE)

    private fun loadExistingProfileIfAny() {
        val user = AuthManager.currentUser(this) ?: return
        val p = prefs()
        inputName.setText(p.getString("${user.email}.name", ""))
        inputHeight.setText(p.getFloat("${user.email}.height", 0f).takeIf { it > 0f }?.toString() ?: "")
        inputWeight.setText(p.getFloat("${user.email}.weight", 0f).takeIf { it > 0f }?.toString() ?: "")
        inputWingspan.setText(p.getFloat("${user.email}.wingspan", 0f).takeIf { it > 0f }?.toString() ?: "")
        val birthday = p.getString("${user.email}.birthday", "2000-01-01") ?: "2000-01-01"
        val parts = birthday.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: 2000
        val month = parts.getOrNull(1)?.toIntOrNull() ?: 1
        val day = parts.getOrNull(2)?.toIntOrNull() ?: 1
        pickerYear.value = year
        pickerMonth.value = month
        pickerDay.value = day
        val sex = p.getString("${user.email}.sex", "Unknown")
        val radioId = when (sex) { "Male" -> R.id.radioMale; "Female" -> R.id.radioFemale; else -> R.id.radioUnknown }
        radioGroupSex.check(radioId)
    }

    private fun saveProfile(): Boolean {
        val name = inputName.text?.toString()?.trim().orEmpty()
        val height = inputHeight.text?.toString()?.toFloatOrNull()
        val weight = inputWeight.text?.toString()?.toFloatOrNull()
        val wingspan = inputWingspan.text?.toString()?.toFloatOrNull()
        val sex = when (radioGroupSex.checkedRadioButtonId) {
            R.id.radioMale -> "Male"
            R.id.radioFemale -> "Female"
            else -> "Unknown"
        }
        val birthday = String.format("%04d-%02d-%02d", pickerYear.value, pickerMonth.value, pickerDay.value)

        if (name.isEmpty() || height == null || weight == null || wingspan == null) {
            Toast.makeText(this, "Please complete all fields", Toast.LENGTH_SHORT).show()
            return false
        }
        val user = AuthManager.currentUser(this)
        if (user == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            return false
        }
        prefs().edit()
            .putString("${user.email}.name", name)
            .putFloat("${user.email}.height", height)
            .putFloat("${user.email}.weight", weight)
            .putFloat("${user.email}.wingspan", wingspan)
            .putString("${user.email}.sex", sex)
            .putString("${user.email}.birthday", birthday)
            .apply()
        return true
    }

    private fun setupBirthdayPickers() {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val months = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        pickerMonth.minValue = 1
        pickerMonth.maxValue = 12
        pickerMonth.displayedValues = months
        pickerMonth.value = 1
        pickerMonth.wrapSelectorWheel = true

        pickerDay.minValue = 1
        pickerDay.maxValue = 31
        pickerDay.value = 1
        pickerDay.wrapSelectorWheel = true

        pickerYear.minValue = 1950
        pickerYear.maxValue = currentYear
        pickerYear.value = 2000
        pickerYear.wrapSelectorWheel = false

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
        if (currentDay > daysInMonth) pickerDay.value = daysInMonth
    }

    private fun isLeapYear(year: Int): Boolean {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }
}
