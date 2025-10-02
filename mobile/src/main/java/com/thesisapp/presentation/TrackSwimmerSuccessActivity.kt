package com.thesisapp.presentation

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.thesisapp.R
import com.thesisapp.utils.animateClick

class TrackSwimmerSuccessActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.track_swimmer_success)

        findViewById<Button>(R.id.btnContinue).setOnClickListener {
            it.animateClick()
            val intent = Intent(this, TrackSwimmerActivity::class.java)
            startActivity(intent)
        }
    }
}