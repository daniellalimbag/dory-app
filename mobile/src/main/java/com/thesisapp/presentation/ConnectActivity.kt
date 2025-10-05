package com.thesisapp.presentation

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.Wearable
import com.google.android.material.button.MaterialButton
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.utils.animateClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConnectActivity : AppCompatActivity() {
    private lateinit var btnConnect: MaterialButton
    private lateinit var btnTrack: MaterialButton
    private lateinit var tvStatus: TextView
    private lateinit var tvHint: TextView
    private lateinit var progress: ProgressBar

    private var isSmartwatchConnected = false
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connect)

        btnConnect = findViewById(R.id.btnConnectNow)
        btnTrack = findViewById(R.id.btnTrackSwimmer)
        tvStatus = findViewById(R.id.tvStatus)
        progress = findViewById(R.id.progressPair)
        tvHint = findViewById(R.id.tvHint)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        btnBack.setOnClickListener {
            it.animateClick()
            finish()
        }

        db = AppDatabase.getInstance(applicationContext)

        updateUI(isChecking = true)
        checkSmartwatchConnection()

        btnConnect.setOnClickListener {
            it.animateClick()
            if (isSmartwatchConnected) {
                showManualDisconnectDialog()
            } else {
                handleSmartwatchConnection()
            }
        }

        btnTrack.setOnClickListener {
            it.animateClick()
            if (isSmartwatchConnected) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val swimmers = db.swimmerDao().getAllSwimmers()
                    withContext(Dispatchers.Main) {
                        val intent = if (swimmers.isNotEmpty()) {
                            Intent(this@ConnectActivity, TrackSwimmerActivity::class.java)
                        } else {
                            Intent(this@ConnectActivity, TrackNoSwimmerActivity::class.java)
                        }
                        startActivity(intent)
                    }
                }
            }
        }
    }

    private fun showManualDisconnectDialog() {
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
    }

    private fun handleSmartwatchConnection() {
        val pixelWatchPackage = "com.google.android.apps.wear.companion"

        if (isPackageInstalled(pixelWatchPackage)) {
            updateUI(isChecking = true)
            Wearable.getNodeClient(this).connectedNodes
                .addOnSuccessListener { nodes ->
                    isSmartwatchConnected = nodes.isNotEmpty()
                    updateUI()

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
                    updateUI()
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

    private fun checkSmartwatchConnection() {
        val pixelWatchPackage = "com.google.android.apps.wear.companion"
        if (isPackageInstalled(pixelWatchPackage)) {
            Wearable.getNodeClient(this).connectedNodes
                .addOnSuccessListener { nodes ->
                    isSmartwatchConnected = nodes.isNotEmpty()
                    updateUI()
                }
                .addOnFailureListener {
                    isSmartwatchConnected = false
                    updateUI()
                    Toast.makeText(this, "Failed to check smartwatch connection", Toast.LENGTH_SHORT).show()
                }
        } else {
            isSmartwatchConnected = false
            updateUI()
        }
    }

    private fun updateUI(isChecking: Boolean = false) {
        progress.visibility = if (isChecking) View.VISIBLE else View.GONE
        btnConnect.isEnabled = !isChecking
        tvStatus.text = when {
            isChecking -> "Connecting…"
            isSmartwatchConnected -> "Smartwatch connected"
            else -> "Not connected"
        }
        btnConnect.text = if (isSmartwatchConnected) "Disconnect Smartwatch" else "Connect Smartwatch"
        btnConnect.backgroundTintList = if (isSmartwatchConnected)
            ContextCompat.getColorStateList(this, R.color.disconnect)
        else ContextCompat.getColorStateList(this, R.color.button)

        btnTrack.isEnabled = isSmartwatchConnected && !isChecking
        tvHint.visibility = if (isSmartwatchConnected) View.GONE else View.VISIBLE
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