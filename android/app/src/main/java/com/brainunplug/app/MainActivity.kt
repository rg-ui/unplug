package com.brainunplug.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.brainunplug.app.model.BrainState
import com.brainunplug.app.service.FloatingOverlayService
import com.brainunplug.app.service.ReelAccessibilityService
import com.brainunplug.app.util.PreferenceManager

class MainActivity : Activity() {

    private lateinit var prefs: PreferenceManager

    private lateinit var imgMascot: ImageView
    private lateinit var tvReelCount: TextView
    private lateinit var tvStateLabel: TextView
    private lateinit var pbProgress: ProgressBar
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvOverlayStatusBtn: TextView
    private lateinit var tvCurrentLimit: TextView

    companion object {
        private const val REQ_OVERLAY_PERMISSION = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PreferenceManager(this)

        initViews()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updateDashboard()
        checkPermissions()
    }

    private fun initViews() {
        imgMascot = findViewById(R.id.imgMainMascot)
        tvReelCount = findViewById(R.id.tvMainReelCount)
        tvStateLabel = findViewById(R.id.tvMainStateLabel)
        pbProgress = findViewById(R.id.pbReelProgress)
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        tvOverlayStatusBtn = findViewById(R.id.tvOverlayStatusBtn)
        tvCurrentLimit = findViewById(R.id.tvCurrentLimit)
    }

    private fun setupListeners() {
        // Accessibility Permission Click
        findViewById<View>(R.id.btnAccessibilityPerm).setOnClickListener {
            if (!isAccessibilityServiceEnabled()) {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
                Toast.makeText(this, "Enable 'Unplug' in Accessibility settings", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Accessibility Service is already Active! ✅", Toast.LENGTH_SHORT).show()
            }
        }

        // Overlay Permission Click
        findViewById<View>(R.id.btnOverlayPerm).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, REQ_OVERLAY_PERMISSION)
            } else {
                Toast.makeText(this, "Overlay Permission is already Granted! ✅", Toast.LENGTH_SHORT).show()
                // Show test overlay
                FloatingOverlayService.show(this)
                FloatingOverlayService.updateCount(this, prefs.getTodayReels())
            }
        }

        // Daily Limit selector
        findViewById<View>(R.id.tvCurrentLimit).setOnClickListener {
            val limits = listOf(25, 50, 75, 100, 150)
            val current = prefs.getDailyLimit()
            val nextIndex = (limits.indexOf(current) + 1) % limits.size
            val nextLimit = limits[nextIndex]
            prefs.setDailyLimit(nextLimit)
            updateDashboard()
            Toast.makeText(this, "Limit set to $nextLimit reels", Toast.LENGTH_SHORT).show()
        }

        // Reset Counter for Testing
        findViewById<Button>(R.id.btnResetToday).setOnClickListener {
            prefs.resetToday()
            updateDashboard()
            FloatingOverlayService.updateCount(this, 0)
            Toast.makeText(this, "Reel counter reset to 0!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateDashboard() {
        val count = prefs.getTodayReels()
        val limit = prefs.getDailyLimit()
        val state = BrainState.fromCount(count)

        tvReelCount.text = "$count / $limit"
        tvStateLabel.text = "● ${state.label}"
        tvStateLabel.setTextColor(ContextCompat.getColor(this, state.colorRes))
        imgMascot.setImageResource(state.drawableRes)

        pbProgress.max = limit
        pbProgress.progress = count.coerceAtMost(limit)
        pbProgress.progressTintList = ContextCompat.getColorStateList(this, state.colorRes)

        tvCurrentLimit.text = "$limit reels ▾"
    }

    private fun checkPermissions() {
        // Check Accessibility
        val isA11yOn = isAccessibilityServiceEnabled()
        if (isA11yOn) {
            tvAccessibilityStatus.text = "Active ✓"
            tvAccessibilityStatus.setBackgroundResource(R.drawable.bg_glass_btn_secondary)
            tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, R.color.ios_green))
        } else {
            tvAccessibilityStatus.text = "Enable"
            tvAccessibilityStatus.setBackgroundResource(R.drawable.bg_glass_btn_primary)
            tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, R.color.black))
        }

        // Check Overlay
        val isOverlayOn = Settings.canDrawOverlays(this)
        if (isOverlayOn) {
            tvOverlayStatusBtn.text = "Active ✓"
            tvOverlayStatusBtn.setBackgroundResource(R.drawable.bg_glass_btn_secondary)
            tvOverlayStatusBtn.setTextColor(ContextCompat.getColor(this, R.color.ios_green))
        } else {
            tvOverlayStatusBtn.text = "Enable"
            tvOverlayStatusBtn.setBackgroundResource(R.drawable.bg_glass_btn_primary)
            tvOverlayStatusBtn.setTextColor(ContextCompat.getColor(this, R.color.black))
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedServiceName = "$packageName/${ReelAccessibilityService::class.java.canonicalName}"
        val enabledServicesSetting = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)

        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedServiceName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
