package com.thesisapp.presentation

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.utils.animateClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_profile)

        val txtName = findViewById<TextView>(R.id.txtName)
        val txtAge = findViewById<TextView>(R.id.txtAge)
        val txtWingspan = findViewById<TextView>(R.id.txtWingspan)
        val txtCategory = findViewById<TextView>(R.id.txtCategory)

        val btnReturn = findViewById<ImageButton>(R.id.btnReturn)
        btnReturn.setOnClickListener {
            it.animateClick()
            finish()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val swimmer = AppDatabase.getInstance(applicationContext).swimmerDao().getAllSwimmers().firstOrNull()

            withContext(Dispatchers.Main) {
                if (swimmer != null) {
                    txtName.text = "${swimmer.name}"
                    txtAge.text = "${swimmer.age}"
                    txtWingspan.text = "${swimmer.wingspan}"
                    txtCategory.text = "${swimmer.category}"
                } else {
                    txtName.text = "No swimmer profile found"
                    txtAge.text = ""
                    txtWingspan.text = ""
                    txtCategory.text = ""
                }
            }
        }
    }
}