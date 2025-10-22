package com.thesisapp.presentation

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.SwimData
import com.thesisapp.utils.animateClick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class SettingsImportActivity : AppCompatActivity() {

    private lateinit var btnSelectFile: Button
    private lateinit var btnImport: Button
    private lateinit var fileNameTextView: TextView

    private var selectedFileUri: Uri? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedFileUri = it
            fileNameTextView.text = getFileName(it)
            btnImport.isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_import)

        btnSelectFile = findViewById(R.id.btnSelectFile)
        btnImport = findViewById(R.id.btnImport)
        fileNameTextView = findViewById(R.id.fileNameTextView)
        val btnReturn = findViewById<Button>(R.id.btnReturn)

        btnSelectFile.setOnClickListener {
            it.animateClick()
            filePickerLauncher.launch("text/*")
        }

        btnImport.setOnClickListener {
            it.animateClick()
            selectedFileUri?.let { uri ->
                importDataFromCSV(uri)
            } ?: Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show()
        }

        btnReturn.setOnClickListener {
            it.animateClick()
            finish()
        }
    }

    private fun importDataFromCSV(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            val swimDataList = mutableListOf<SwimData>()
            var linesProcessed = 0
            var errorOccurred = false

            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        reader.readLine()

                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val tokens = line!!.split(",")
                            if (tokens.size == 11) {
                                val swimData = SwimData(
                                    sessionId = tokens[0].toInt(),
                                    timestamp = tokens[1].toLong(),
                                    accel_x = tokens[2].toFloat(),
                                    accel_y = tokens[3].toFloat(),
                                    accel_z = tokens[4].toFloat(),
                                    gyro_x = tokens[5].toFloat(),
                                    gyro_y = tokens[6].toFloat(),
                                    gyro_z = tokens[7].toFloat(),
                                    heart_rate = tokens[8].toFloat(),
                                    ppg = tokens[9].toFloat(),
                                    ecg = tokens[10].toFloat()
                                )
                                swimDataList.add(swimData)
                                linesProcessed++
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                errorOccurred = true
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsImportActivity, "Error reading file: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            if (errorOccurred) return@launch

            if (swimDataList.isNotEmpty()) {
                val db = AppDatabase.getInstance(applicationContext)
                db.swimDataDao().insertAll(swimDataList)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsImportActivity, "$linesProcessed records imported successfully.", Toast.LENGTH_LONG).show()
                    finish()
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsImportActivity, "No valid data found in the file.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            cursor.getString(nameIndex)
        } ?: "File"
    }
}