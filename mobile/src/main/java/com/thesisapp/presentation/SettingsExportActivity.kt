package com.thesisapp.presentation

import android.app.DatePickerDialog
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.utils.animateClick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class SettingsExportActivity : AppCompatActivity() {

    private lateinit var btnFromDate: Button
    private lateinit var btnToDate: Button
    private lateinit var btnExport: Button
    private lateinit var sessionCountTextView: TextView

    private var fromDate: Long = 0L
    private var toDate: Long = 0L

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_export)

        btnFromDate = findViewById(R.id.fromDate)
        btnToDate = findViewById(R.id.toDate)
        sessionCountTextView = findViewById(R.id.sessionCount)
        btnExport = findViewById(R.id.btnExport)
        val btnReturn = findViewById<Button>(R.id.btnReturn)

        btnFromDate.setOnClickListener {
            it.animateClick()
            showDatePicker { timestamp ->
                fromDate = timestamp
                btnFromDate.text = dateFormat.format(Date(fromDate))
                updateSessionCount()
            }
        }

        btnToDate.setOnClickListener {
            it.animateClick()
            showDatePicker { timestamp ->
                // 23:59:59.999 of selected date
                val endOfDay = Calendar.getInstance().apply {
                    timeInMillis = timestamp
                    add(Calendar.DATE, 1) // move to next day
                }.timeInMillis - 1

                toDate = endOfDay
                btnToDate.text = dateFormat.format(Date(timestamp))
                updateSessionCount()
            }
        }

        btnExport.setOnClickListener {
            it.animateClick()
            if (fromDate == 0L || toDate == 0L) {
                Toast.makeText(this, "Please select both dates", Toast.LENGTH_SHORT).show()
            } else if (fromDate > toDate) {
                Toast.makeText(this, "From date must be before To date", Toast.LENGTH_SHORT).show()
            } else {
                exportSwimDataToCSV()
            }
        }

        btnReturn.setOnClickListener {
            it.animateClick()
            finish()
        }
    }

    private fun showDatePicker(onDateSelected: (Long) -> Unit) {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(this, { _, y, m, d ->
            val selected = Calendar.getInstance()
            selected.set(y, m, d, 0, 0, 0)
            selected.set(Calendar.MILLISECOND, 0)
            onDateSelected(selected.timeInMillis)
        }, year, month, day).show()
    }

    private fun updateSessionCount() {
        if (fromDate == 0L || toDate == 0L || fromDate > toDate) {
            sessionCountTextView.text = "FOUND ... SESSIONS"
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(applicationContext)
            val sessionCount = db.swimDataDao().countSessionsBetweenDates(fromDate, toDate)

            withContext(Dispatchers.Main) {
                sessionCountTextView.text = "FOUND $sessionCount SESSIONS"
            }
        }
    }

    private fun exportSwimDataToCSV() {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(applicationContext)
            val swimDataList = db.swimDataDao().getSwimsBetweenDates(fromDate, toDate)

            if (swimDataList.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsExportActivity, "No swim data found", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val fileName = "swim_data_export_${System.currentTimeMillis()}.csv"
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)

            FileWriter(file).use { writer ->
                writer.appendLine("session_id,timestamp,accel_x,accel_y,accel_z,gyro_x,gyro_y,gyro_z,heart_rate,ppg,ecg")
                for (data in swimDataList) {
                    writer.appendLine("${data.sessionId},${data.timestamp},${data.accel_x},${data.accel_y},${data.accel_z}," +
                            "${data.gyro_x},${data.gyro_y},${data.gyro_z}," +
                            "${data.heart_rate},${data.ppg},${data.ecg}")
                }
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(this@SettingsExportActivity, "Data exported to ${file.name}", Toast.LENGTH_LONG).show()
            }
        }
    }
}