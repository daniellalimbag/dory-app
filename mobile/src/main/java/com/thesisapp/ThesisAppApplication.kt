package com.thesisapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import com.thesisapp.utils.ThemeManager

@HiltAndroidApp
class ThesisAppApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeManager.applySavedTheme(this)
    }
}
