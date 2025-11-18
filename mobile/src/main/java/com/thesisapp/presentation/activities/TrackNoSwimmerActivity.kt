package com.thesisapp.presentation.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.thesisapp.R
import com.thesisapp.utils.animateClick

class TrackNoSwimmerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.track_no_swimmer)

        findViewById<Button>(R.id.btnAddSwimmer).setOnClickListener {
            it.animateClick()
            startActivity(Intent(this, TrackAddSwimmerActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnReturn).setOnClickListener {
            it.animateClick()
            finish()
        }
    }
}