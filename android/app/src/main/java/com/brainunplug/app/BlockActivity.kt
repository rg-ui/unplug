package com.brainunplug.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.brainunplug.app.util.PreferenceManager

class BlockActivity : Activity() {

    private lateinit var prefs: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_block)

        prefs = PreferenceManager(this)

        val tvCount = findViewById<TextView>(R.id.tvBlockCount)
        val btnExit = findViewById<Button>(R.id.btnExitApp)
        val btnSnooze = findViewById<TextView>(R.id.btnSnooze)

        val count = prefs.getTodayReels()
        tvCount.text = "$count Reels Watched Today"

        btnExit.setOnClickListener {
            // Send user to Android Home screen
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            finish()
        }

        btnSnooze.setOnClickListener {
            // Increase limit by 5 for emergency snooze
            val currentLimit = prefs.getDailyLimit()
            prefs.setDailyLimit(currentLimit + 5)
            finish()
        }
    }

    override fun onBackPressed() {
        // Prevent back press from returning directly to reels; go home instead
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}
