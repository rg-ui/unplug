# Unplug - Reel Counter & Doomscroll Blocker (Android App)

Native Android app that counts short-form reels on **Instagram**, **YouTube Shorts**, **Snapchat**, and **Facebook**, shows a floating mascot counter pill, and blocks feeds once daily limits are reached.

---

## 🚀 Key Features

1. **Accessibility Reel Counter (`ReelAccessibilityService`)**:
   - Detects swipe/scroll gestures inside targeted apps (`com.instagram.android`, `com.google.android.youtube`, etc.).
   - Debounces scrolls so rapid gestures don't inflate counts.
   - Saves daily counts persistently with automatic midnight reset.

2. **Floating Pill Overlay (`FloatingOverlayService`)**:
   - Draggable, interactive pill widget displayed over Instagram/Shorts using `WindowManager` (`TYPE_APPLICATION_OVERLAY`).
   - Mascot face changes mood dynamically based on reels watched:
     - 🟢 **Healthy & Happy** (0–25 reels)
     - 🟡 **Slightly Tired** (26–50 reels)
     - 🟠 **Exhausted** (51–100 reels)
     - 🟣 **Unhealthy & Sick** (101–150 reels)
     - 🔴 **Rotting & Dizzy** (150+ reels)

3. **Feed Blocker (`BlockActivity`)**:
   - Triggers when daily reel limit is hit.
   - Redirects user back to their Android home screen to stop doomscrolling.
   - Emergency 5-minute snooze option for critical messages.

4. **Dashboard (`MainActivity`)**:
   - Real-time reel progress ring.
   - One-tap permission guides for Accessibility and Floating Overlay.
   - Customizable daily limit (25, 50, 75, 100, 150 reels).

---

## 🛠️ How to Open & Build

### In Android Studio:
1. Open Android Studio.
2. Select **Open an Existing Project** and choose the `/Users/raviranjankumargupta/doomscroll/android` folder.
3. Sync Gradle and click **Run (▶)** on your phone or emulator.

### Via Command Line:
```bash
cd android
./gradlew assembleDebug
```
The APK will be generated at:
`android/app/build/outputs/apk/debug/app-debug.apk`

---

## 📱 Permissions Required
When launching the app for the first time:
1. **Accessibility Permission**: Settings > Accessibility > Unplug > Enable.
2. **Display over other apps**: Settings > Apps > Special App Access > Display Over Other Apps > Unplug > Allow.
