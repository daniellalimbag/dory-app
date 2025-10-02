package com.thesisapp.utils

import android.view.View

fun View.animateClick(duration: Long = 100) {
    this.animate()
        .scaleX(0.95f)
        .scaleY(0.95f)
        .setDuration(duration)
        .withEndAction {
            this.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(duration)
                .start()
        }
        .start()
}