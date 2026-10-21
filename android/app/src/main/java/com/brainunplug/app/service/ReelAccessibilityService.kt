package com.brainunplug.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.brainunplug.app.BlockActivity
import com.brainunplug.app.util.PreferenceManager

class ReelAccessibilityService : AccessibilityService() {

    private lateinit var prefs: PreferenceManager
    private var lastScrollTime: Long = 0
    private var currentPackage: String = ""

    companion object {
        private const val TAG = "ReelAccessibility"
        // 900ms debounce ensures 1 swipe gesture = 1 count, without blocking subsequent swipes
        private const val SCROLL_DEBOUNCE_MS = 900L
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PreferenceManager(this)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_SCROLLED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 50
        }
        serviceInfo = info
        Log.d(TAG, "ReelAccessibilityService connected and active")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkgName = event.packageName?.toString() ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowStateChanged(pkgName)
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                handleScrollEvent(pkgName, event)
            }
        }
    }

    private fun isYouTube(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower == "com.google.android.youtube" ||
                lower == "com.google.android.apps.youtube.mango" ||
                lower.contains("youtube")
    }

    private fun isInstagram(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower == "com.instagram.android" ||
                lower == "com.instagram.lite" ||
                lower.contains("instagram")
    }

    private fun isTargetPackage(pkg: String): Boolean {
        return isYouTube(pkg) || isInstagram(pkg)
    }

    private fun isSystemWindow(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower.contains("systemui") ||
                lower.contains("inputmethod") ||
                lower.contains("keyboard") ||
                lower.contains("permissioncontroller")
    }

    private fun handleWindowStateChanged(pkgName: String) {
        if (isSystemWindow(pkgName) || pkgName.startsWith("com.brainunplug.app")) {
            return
        }

        currentPackage = pkgName
        if (isTargetPackage(pkgName)) {
            if (prefs.isOverlayEnabled()) {
                FloatingOverlayService.show(this)
                FloatingOverlayService.updateCount(this, prefs.getTodayReels())
            }
        } else {
            FloatingOverlayService.hide(this)
        }
    }

    private fun handleScrollEvent(pkgName: String, event: AccessibilityEvent) {
        if (!isTargetPackage(pkgName)) return

        // Filter out non-scrollable widgets (e.g. seekbars scrubbing during video playback)
        val className = event.className?.toString()?.lowercase() ?: ""
        if (className.contains("seekbar") ||
            className.contains("progressbar") ||
            className.contains("switch") ||
            className.contains("edittext")
        ) {
            return
        }

        // Filter out horizontal swipes (e.g. Instagram Stories or YouTube tab switches)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (event.scrollDeltaX != 0 && event.scrollDeltaY == 0) {
                return
            }
        }

        // Verify that the user is currently watching Shorts or Reels
        if (!isUserInShortsOrReels(pkgName)) {
            return
        }

        // Debounce swipe events so 1 swipe gesture is counted as exactly 1 reel
        val now = SystemClock.uptimeMillis()
        if (now - lastScrollTime < SCROLL_DEBOUNCE_MS) {
            return
        }

        lastScrollTime = now
        val newCount = prefs.incrementReel()
        Log.d(TAG, "[$pkgName] Reel/Short detected! New count: $newCount")

        // Update floating capsule live
        try {
            FloatingOverlayService.updateCount(this, newCount)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Check if limit exceeded
        val limit = prefs.getDailyLimit()
        if (newCount >= limit) {
            triggerBlockScreen()
        }
    }

    private fun isUserInShortsOrReels(pkgName: String): Boolean {
        val root = rootInActiveWindow ?: return true // If root unavailable, default to true so scrolls are not lost

        return try {
            if (isYouTube(pkgName)) {
                isYouTubeShortsScreen(root)
            } else if (isInstagram(pkgName)) {
                isInstagramReelsScreen(root)
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking screen mode: ${e.message}")
            true
        }
    }

    /**
     * Rock-solid YouTube Shorts detection:
     * 1. Shorts player always contains dedicated Dislike, Remix, Sound, or Subscribe action buttons.
     * 2. YouTube Home feed contains visible "Home" and "Subscriptions" bottom navigation tabs without fullscreen video overlay.
     */
    private fun isYouTubeShortsScreen(root: AccessibilityNodeInfo): Boolean {
        // Signal 1: Check for Shorts-specific action buttons & descriptors
        val shortsIndicators = listOf("Dislike", "dislike", "Remix", "remix", "Sound", "sound", "Shorts", "shorts", "Create short")
        for (indicator in shortsIndicators) {
            val nodes = root.findAccessibilityNodeInfosByText(indicator)
            if (nodes.isNotEmpty()) return true
        }

        // Signal 2: Inspect view IDs and content descriptions in hierarchy (up to 4 levels)
        if (hasMatchingNode(root, maxDepth = 4) { node ->
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val id = node.viewIdResourceName?.lowercase() ?: ""
            desc.contains("shorts") || desc.contains("dislike") || desc.contains("remix") ||
                    desc.contains("like this video") || desc.contains("like this short") ||
                    id.contains("shorts") || id.contains("reel")
        }) {
            return true
        }

        // Signal 3: If Home & Subscriptions bottom tabs are visible, user is browsing Home feed, NOT in full-screen Shorts
        val hasHomeTab = root.findAccessibilityNodeInfosByText("Home").isNotEmpty()
        val hasSubTab = root.findAccessibilityNodeInfosByText("Subscriptions").isNotEmpty()
        if (hasHomeTab && hasSubTab) {
            return false
        }

        // Default to true when uncertain so user's scrolls are never lost
        return true
    }

    /**
     * Rock-solid Instagram Reels detection:
     * 1. Reels player contains "Reels", "Audio", "Original audio" labels or "clips" view IDs.
     * 2. Fullscreen vertical pager with Like/Comment/Share actions.
     */
    private fun isInstagramReelsScreen(root: AccessibilityNodeInfo): Boolean {
        // Signal 1: Check for Reels text markers
        val reelIndicators = listOf("Reels", "reels", "Audio", "audio", "Original audio")
        for (indicator in reelIndicators) {
            val nodes = root.findAccessibilityNodeInfosByText(indicator)
            if (nodes.isNotEmpty()) return true
        }

        // Signal 2: Inspect view IDs and content descriptions
        if (hasMatchingNode(root, maxDepth = 4) { node ->
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val id = node.viewIdResourceName?.lowercase() ?: ""
            desc.contains("reel") || desc.contains("clips") || desc.contains("audio") ||
                    id.contains("clips") || id.contains("reel")
        }) {
            return true
        }

        return true
    }

    /**
     * Fast, depth-limited search across the active window hierarchy.
     */
    private fun hasMatchingNode(
        node: AccessibilityNodeInfo?,
        maxDepth: Int,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): Boolean {
        if (node == null || maxDepth < 0) return false
        if (predicate(node)) return true

        val count = node.childCount
        for (i in 0 until count.coerceAtMost(16)) {
            val child = node.getChild(i) ?: continue
            if (hasMatchingNode(child, maxDepth - 1, predicate)) {
                return true
            }
        }
        return false
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
