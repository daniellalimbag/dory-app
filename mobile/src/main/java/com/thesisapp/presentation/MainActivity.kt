package com.thesisapp.presentation

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.card.MaterialCardView
import com.google.android.material.button.MaterialButton
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
    private lateinit var btnSwimmers: MaterialCardView
    private lateinit var btnSessions: MaterialCardView
    private lateinit var btnEnrollSwimmer: MaterialButton
    private lateinit var tvSwimmerCount: TextView
    private lateinit var tvSessionCount: TextView
    private var isSmartwatchConnected = false
    private lateinit var db: AppDatabase

    // Dummy data for UI
    private var swimmerCount = 5
    private val sessionCount = 12

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_dashboard)

        btnConnect = findViewById(R.id.btnConnect)
        btnSwimmers = findViewById(R.id.btnSwimmers)
        btnSessions = findViewById(R.id.btnSessions)
        btnEnrollSwimmer = findViewById(R.id.btnEnrollSwimmer)
        tvSwimmerCount = findViewById(R.id.tvSwimmerCount)
        tvSessionCount = findViewById(R.id.tvSessionCount)

        db = AppDatabase.getInstance(applicationContext)

        // Set dummy data
        updateCounts()

        updateSmartwatchButton()

        btnConnect.setOnClickListener {
            it.animateClick()
            startActivity(Intent(this, ConnectActivity::class.java))
        }

        btnSwimmers.setOnClickListener {
            it.animateClick()
            startActivity(Intent(this, SwimmersActivity::class.java))
        }

        btnSessions.setOnClickListener {
            it.animateClick()
            startActivity(Intent(this, HistoryListActivity::class.java))
        }

        btnEnrollSwimmer.setOnClickListener {
            it.animateClick()
            // Navigate to enrollment screen
            startActivity(Intent(this, TrackAddSwimmerActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh swimmer count when returning to this activity
        loadSwimmerCount()
        // Check smartwatch connection
        checkSmartwatchConnection()
    }

    private fun loadSwimmerCount() {
        lifecycleScope.launch(Dispatchers.IO) {
            val swimmers = db.swimmerDao().getAllSwimmers()
            withContext(Dispatchers.Main) {
                // If database is empty, show dummy data count (5 swimmers)
                swimmerCount = if (swimmers.isEmpty()) 5 else swimmers.size
                updateCounts()
            }
        }
    }

    private fun updateCounts() {
        tvSwimmerCount.text = swimmerCount.toString()
        tvSessionCount.text = sessionCount.toString()
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
            btnConnect.text = "Disconnect"
            btnConnect.backgroundTintList = ContextCompat.getColorStateList(this, R.color.disconnect)
        } else {
            btnConnect.text = "Connect"
            btnConnect.backgroundTintList = ContextCompat.getColorStateList(this, R.color.button)
        }
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