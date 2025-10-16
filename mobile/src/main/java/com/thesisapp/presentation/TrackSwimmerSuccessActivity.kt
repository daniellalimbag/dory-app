package com.thesisapp.presentation

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.thesisapp.R
import com.thesisapp.utils.animateClick

class TrackSwimmerSuccessActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.track_swimmer_success)

        val code = intent.getStringExtra("SWIMMER_CODE") ?: ""
        findViewById<TextView>(R.id.tvSwimmerCode)?.text = code

        findViewById<android.widget.ImageButton>(R.id.btnCopyCode).setOnClickListener {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Swimmer Code", code)
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(this, "Code copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnContinue).setOnClickListener {
            it.animateClick()
            finish()
        }
    }
}