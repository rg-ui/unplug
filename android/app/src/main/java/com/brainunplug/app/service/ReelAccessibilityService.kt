package com.brainunplug.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.brainunplug.app.BlockActivity
import com.brainunplug.app.util.PreferenceManager

class ReelAccessibilityService : AccessibilityService() {

    private lateinit var prefs: PreferenceManager
    private var lastScrollTime: Long = 0
    private var lastItemIndex: Int = -1
    private var currentPackage: String = ""

    companion object {
        // Minimum time between valid reel scrolls (1.2 seconds to prevent rapid double-counting)
        private const val SCROLL_DEBOUNCE_MS = 1200L
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PreferenceManager(this)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            // ONLY listen for actual scroll events and window transitions!
            // Crucial: DO NOT include TYPE_VIEW_SELECTED or TYPE_VIEW_CLICKED!
            eventTypes = AccessibilityEvent.TYPE_VIEW_SCROLLED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
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
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                handleScrollEvent(pkgName, event)
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

        // 1. CLASS CHECK: Filter out buttons, images, text, and non-pager views
        val className = event.className?.toString() ?: ""
        if (isNonScrollableClass(className)) {
            // User clicked or interacted with a button, text, or widget
            return
        }

        // Must be a list/pager container
        if (!isScrollContainer(className)) {
            return
        }

        val sourceNode = event.source

        // 2. VIEW SIZE CHECK: Shorts and Reels containers are FULLSCREEN vertical pagers!
        // A comment box, like button, horizontal chips, or mini-player is small (< 50% screen height)
        val screenHeight = resources.displayMetrics.heightPixels
        val viewBounds = Rect()
        sourceNode?.getBoundsInScreen(viewBounds)
        if (viewBounds.height() > 0 && viewBounds.height() < (screenHeight * 0.50)) {
            // Too small to be a short/reel video player (e.g. comments drawer, reaction tray)
            sourceNode?.recycle()
            return
        }

        // 3. VERTICAL MOTION CHECK: Reels and Shorts ONLY scroll vertically!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val deltaX = event.scrollDeltaX
            val deltaY = event.scrollDeltaY
            // If deltaX is significant and deltaY is 0, it is a horizontal swipe (e.g. Stories tray)
            if (deltaY == 0 && Math.abs(deltaX) > 10) {
                sourceNode?.recycle()
                return
            }
        }

        // 4. SCREEN CONTEXT CHECK: Are we actually in Reels / Shorts?
        val inShortsOrReels = isUserInShortsOrReelsMode(pkgName, sourceNode)
        sourceNode?.recycle()

        if (!inShortsOrReels) {
            return
        }

        // 5. DEBOUNCE & ITEM INDEX CHECK
        val now = SystemClock.uptimeMillis()
        val currentIndex = event.fromIndex

        // If the same item index is reported within 2.5s without moving to the next reel, ignore
        if (currentIndex != -1 && currentIndex == lastItemIndex && (now - lastScrollTime < 2500L)) {
            return
        }

        if (now - lastScrollTime < SCROLL_DEBOUNCE_MS) {
            return
        }

        // VALID REEL / SHORT SCROLL DETECTED!
        lastScrollTime = now
        if (currentIndex != -1) {
            lastItemIndex = currentIndex
        }

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

    private fun isNonScrollableClass(className: String): Boolean {
        val lower = className.lowercase()
        return lower.contains("button") ||
                lower.contains("imageview") ||
                lower.contains("textview") ||
                lower.contains("edittext") ||
                lower.contains("seekbar") ||
                lower.contains("progressbar") ||
                lower.contains("switch") ||
                lower.contains("checkbox") ||
                lower.contains("radiobutton") ||
                lower.contains("tab")
    }

    private fun isScrollContainer(className: String): Boolean {
        val lower = className.lowercase()
        return lower.contains("viewpager") ||
                lower.contains("recyclerview") ||
                lower.contains("scrollview") ||
                lower.contains("listview") ||
                lower.contains("reboundviewpager") ||
                lower.contains("framelayout")
    }

    private fun isUserInShortsOrReelsMode(pkgName: String, sourceNode: AccessibilityNodeInfo?): Boolean {
        val pkg = pkgName.lowercase()
        val root = rootInActiveWindow ?: return true // Fallback to allow if root inaccessible

        try {
            if (pkg.contains("youtube")) {
                // Check if current screen is YouTube Shorts
                val sourceId = sourceNode?.viewIdResourceName?.lowercase() ?: ""
                if (sourceId.contains("shorts") || sourceId.contains("reel")) {
                    return true
                }

                // Inspect active window for Shorts markers
                val shortsMarkers = listOf(
                    "shorts_container", "shorts_pager", "shorts_player",
                    "reel_recycler", "reel_watch_fragment", "reel_player"
                )
                for (id in shortsMarkers) {
                    val nodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/$id")
                    if (nodes.isNotEmpty()) return true
                }

                // Check text clues (YouTube Shorts overlay always has "Remix" or "Sound" or "Subscribe")
                val remixNodes = root.findAccessibilityNodeInfosByText("Remix")
                if (remixNodes.isNotEmpty()) return true

                val shortsNodes = root.findAccessibilityNodeInfosByText("Shorts")
                if (shortsNodes.isNotEmpty()) return true

                // If it's a ViewPager2 in YouTube that's fullscreen, it's 99.9% Shorts
                val sourceClass = sourceNode?.className?.toString()?.lowercase() ?: ""
                if (sourceClass.contains("viewpager2")) {
                    return true
                }

                return false
            } else if (pkg.contains("instagram")) {
                // Check if current screen is Instagram Reels (clips)
                val sourceId = sourceNode?.viewIdResourceName?.lowercase() ?: ""
                if (sourceId.contains("clips") || sourceId.contains("reel")) {
                    return true
                }

                val instaMarkers = listOf(
                    "clips_viewer", "clips_video_container", "clips_swipe_refresh_container",
                    "reel_viewer", "reel_recycler"
                )
                for (id in instaMarkers) {
                    val nodes = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/$id")
                    if (nodes.isNotEmpty()) return true
                }

                val audioNodes = root.findAccessibilityNodeInfosByText("Original audio")
                if (audioNodes.isNotEmpty()) return true

                val reelsNodes = root.findAccessibilityNodeInfosByText("Reels")
                if (reelsNodes.isNotEmpty()) return true

                val sourceClass = sourceNode?.className?.toString()?.lowercase() ?: ""
                if (sourceClass.contains("reboundviewpager") || sourceClass.contains("viewpager")) {
                    return true
                }

                return false
            } else if (pkg.contains("facebook")) {
                val fbMarkers = listOf("reel", "short_form_video", "fb_shorts")
                for (id in fbMarkers) {
                    val nodes = root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/$id")
                    if (nodes.isNotEmpty()) return true
                }
                return true
            } else if (pkg.contains("snapchat")) {
                val snapMarkers = listOf("spotlight", "vertical_view_pager")
                for (id in snapMarkers) {
                    val nodes = root.findAccessibilityNodeInfosByViewId("com.snapchat.android:id/$id")
                    if (nodes.isNotEmpty()) return true
                }
                val spotNodes = root.findAccessibilityNodeInfosByText("Spotlight")
                if (spotNodes.isNotEmpty()) return true
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return true
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
