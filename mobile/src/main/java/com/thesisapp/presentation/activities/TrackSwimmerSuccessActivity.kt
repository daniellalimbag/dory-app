package com.thesisapp.presentation.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.thesisapp.R
import com.thesisapp.utils.animateClick

class TrackSwimmerSuccessActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.track_swimmer_success)

        val code = intent.getStringExtra("SWIMMER_CODE") ?: ""
        findViewById<TextView>(R.id.tvSwimmerCode)?.text = code

        findViewById<ImageButton>(R.id.btnCopyCode).setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Swimmer Code", code)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnContinue).setOnClickListener {
            it.animateClick()
            finish()
        }
    }
}