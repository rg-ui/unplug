package com.brainunplug.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.brainunplug.app.BlockActivity
import com.brainunplug.app.util.PreferenceManager

class ReelAccessibilityService : AccessibilityService() {

    private lateinit var prefs: PreferenceManager
    private var lastScrollTime: Long = 0
    private var currentPackage: String = ""

    companion object {
        private const val SCROLL_DEBOUNCE_MS = 1000L
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PreferenceManager(this)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_SCROLLED or
                    AccessibilityEvent.TYPE_VIEW_SELECTED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 80
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkgName = event.packageName?.toString() ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowStateChanged(pkgName)
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                handleScrollOrPageChange(pkgName, event)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (isTargetPackage(pkgName) && prefs.isOverlayEnabled()) {
                    FloatingOverlayService.show(this)
                }
            }
        }
    }

    private fun isTargetPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower.contains("youtube") ||
                lower.contains("instagram") ||
                lower.contains("snapchat") ||
                lower.contains("facebook")
    }

    private fun isSystemWindow(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower.contains("systemui") ||
                lower.contains("inputmethod") ||
                lower.contains("keyboard") ||
                lower.contains("coloros.gamespace") ||
                lower.contains("oplus.cosa") ||
                lower.contains("permissioncontroller")
    }

    private fun handleWindowStateChanged(pkgName: String) {
        if (isSystemWindow(pkgName) || pkgName.startsWith("com.brainunplug.app")) {
            // Don't hide pill for system dialogs, keyboard, or our own app
            return
        }

        currentPackage = pkgName
        if (isTargetPackage(pkgName)) {
            // User is in YouTube or Instagram
            if (prefs.isOverlayEnabled()) {
                FloatingOverlayService.show(this)
                FloatingOverlayService.updateCount(this, prefs.getTodayReels())
            }
        } else {
            // User switched to another app
            FloatingOverlayService.hide(this)
        }
    }

    private fun handleScrollOrPageChange(pkgName: String, event: AccessibilityEvent) {
        if (!isTargetPackage(pkgName)) return

        // Ensure floating pill is showing
        try {
            if (prefs.isOverlayEnabled()) {
                FloatingOverlayService.show(this)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val now = SystemClock.uptimeMillis()
        if (now - lastScrollTime < SCROLL_DEBOUNCE_MS) {
            return
        }

        // Count reel or short!
        lastScrollTime = now
        val newCount = prefs.incrementReel()

        // Update floating Dynamic Island pill live
        try {
            FloatingOverlayService.updateCount(this, newCount)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Check if limit hit
        val limit = prefs.getDailyLimit()
        if (newCount >= limit) {
            triggerBlockScreen()
        }
    }

    private fun triggerBlockScreen() {
        try {
            performGlobalAction(GLOBAL_ACTION_HOME)
            val intent = Intent(this, BlockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onInterrupt() {
        FloatingOverlayService.hide(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        FloatingOverlayService.hide(this)
    }
}
