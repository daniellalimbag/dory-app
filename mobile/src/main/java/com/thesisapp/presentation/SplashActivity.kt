package com.thesisapp.presentation

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.thesisapp.R
import com.thesisapp.utils.AuthManager

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            val next = if (AuthManager.isLoggedIn(this)) MainActivity::class.java else AuthActivity::class.java
            val intent = Intent(this, next)
            startActivity(intent)
            finish()
        }, 1000)
    }
}