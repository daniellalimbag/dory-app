package com.thesisapp.presentation

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.Wearable
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.utils.animateClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var btnConnect: Button
    private lateinit var btnStartTracking: Button
    private var isSmartwatchConnected = false
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_dashboard)

        btnConnect = findViewById(R.id.btnConnect)
        btnStartTracking = findViewById(R.id.btnStartTracking)
        val btnViewHistory = findViewById<Button>(R.id.btnViewHistory)
        val btnSettings = findViewById<Button>(R.id.btnSettings)

        db = AppDatabase.getInstance(applicationContext)

        updateSmartwatchButton()

        btnConnect.setOnClickListener {
            it.animateClick()
            if (isSmartwatchConnected) {
                val alertDialog = AlertDialog.Builder(this)
                    .setTitle("Manual Disconnect Required")
                    .setMessage("To disconnect your smartwatch, please turn off Bluetooth or open the Pixel Watch app to manage the connection.")
                    .setPositiveButton("Open Pixel Watch App", null)
                    .setNegativeButton("Cancel", null)
                    .create()

                alertDialog.setOnShowListener {
                    val btnOpen = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    val btnCancel = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)

                    btnOpen.setOnClickListener {
                        it.animateClick()
                        val intent = packageManager.getLaunchIntentForPackage("com.google.android.apps.wear.companion")
                        if (intent != null) {
                            startActivity(intent)
                        } else {
                            Toast.makeText(this, "Pixel Watch app not found", Toast.LENGTH_SHORT).show()
                        }
                        alertDialog.dismiss()
                    }

                    btnCancel.setOnClickListener {
                        it.animateClick()
                        alertDialog.dismiss()
                    }
                }

                alertDialog.show()
            } else {
                handleSmartwatchConnection()
            }
        }

        btnStartTracking.setOnClickListener {
            it.animateClick()
            if (isSmartwatchConnected) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val swimmers = db.swimmerDao().getAllSwimmers()

                    withContext(Dispatchers.Main) {
                        val intent = if (swimmers.isNotEmpty()) {
                            Intent(this@MainActivity, TrackSwimmerActivity::class.java)
                        } else {
                            Intent(this@MainActivity, TrackNoSwimmerActivity::class.java)
                        }
                        startActivity(intent)
                    }
                }
            }
        }

        btnViewHistory.setOnClickListener {
            it.animateClick()
            startActivity(Intent(this, HistoryListActivity::class.java))
        }

        btnSettings.setOnClickListener {
            it.animateClick()
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun handleSmartwatchConnection() {
        val pixelWatchPackage = "com.google.android.apps.wear.companion"

        if (isPackageInstalled(pixelWatchPackage)) {
            Wearable.getNodeClient(this).connectedNodes
                .addOnSuccessListener { nodes ->
                    isSmartwatchConnected = nodes.isNotEmpty()
                    updateSmartwatchButton()

                    val connectionType = if (isSmartwatchConnected) {
                        if (BluetoothAdapter.getDefaultAdapter()?.isEnabled == true) {
                            "Smartwatch connected (Bluetooth)"
                        } else {
                            "Smartwatch connected (Cloud)"
                        }
                    } else {
                        "Pixel Watch app found, but no smartwatch connected"
                    }

                    Toast.makeText(this, connectionType, Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to check smartwatch connection", Toast.LENGTH_SHORT).show()
                }
        } else {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=$pixelWatchPackage")
                setPackage("com.android.vending")
            }
            startActivity(intent)
        }
    }

    private fun updateSmartwatchButton() {
        if (isSmartwatchConnected) {
            btnConnect.text = "Disconnect Smartwatch"
            btnConnect.backgroundTintList = ContextCompat.getColorStateList(this, R.color.disconnect)

            btnStartTracking.isEnabled = true
            btnStartTracking.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_swim_on, 0, 0, 0)
            btnStartTracking.setTextColor(ContextCompat.getColor(this, R.color.black))
        } else {
            btnConnect.text = "Connect Smartwatch"
            btnConnect.backgroundTintList = ContextCompat.getColorStateList(this, R.color.connect)

            btnStartTracking.isEnabled = false
            btnStartTracking.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_swim_off, 0, 0, 0)
            btnStartTracking.setTextColor(ContextCompat.getColor(this, R.color.disabled_text))
        }
    }

    override fun onResume() {
        super.onResume()
        checkSmartwatchConnection()
    }

    private fun checkSmartwatchConnection() {
        val pixelWatchPackage = "com.google.android.apps.wear.companion"

        if (isPackageInstalled(pixelWatchPackage)) {
            Wearable.getNodeClient(this).connectedNodes
                .addOnSuccessListener { nodes ->
                    isSmartwatchConnected = nodes.isNotEmpty()
                    updateSmartwatchButton()
                }
                .addOnFailureListener {
                    isSmartwatchConnected = false
                    updateSmartwatchButton()
                    Toast.makeText(this, "Failed to check smartwatch connection", Toast.LENGTH_SHORT).show()
                }
        } else {
            isSmartwatchConnected = false
            updateSmartwatchButton()
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}