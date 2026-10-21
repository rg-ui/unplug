package com.brainunplug.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.brainunplug.app.R
import com.brainunplug.app.model.BrainState
import com.brainunplug.app.util.PreferenceManager

class FloatingOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private lateinit var prefs: PreferenceManager

    private var tvCount: TextView? = null
    private var tvStatus: TextView? = null
    private var imgMascot: ImageView? = null

    companion object {
        const val ACTION_UPDATE_COUNT = "com.unplug.ACTION_UPDATE_COUNT"
        const val EXTRA_COUNT = "extra_count"
        const val ACTION_SHOW = "com.unplug.ACTION_SHOW"
        const val ACTION_HIDE = "com.unplug.ACTION_HIDE"

        fun updateCount(context: Context, count: Int) {
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_UPDATE_COUNT
                putExtra(EXTRA_COUNT, count)
            }
            context.startService(intent)
        }

        fun show(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_SHOW
            }
            context.startService(intent)
        }

        fun hide(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_HIDE
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PreferenceManager(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        initOverlay()
    }

    private fun initOverlay() {
        if (!Settings.canDrawOverlays(this)) return
        if (overlayView != null) return

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 200
        }

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_counter, null)
        tvCount = overlayView?.findViewById(R.id.tvOverlayCount)
        tvStatus = overlayView?.findViewById(R.id.tvOverlayStatus)
        imgMascot = overlayView?.findViewById(R.id.imgOverlayMascot)

        setupDragListener()
        updateUi(prefs.getTodayReels())

        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupDragListener() {
        overlayView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isClick = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val p = params ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = p.x
                        initialY = p.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isClick = true
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isClick = false
                        }
                        p.x = initialX + dx
                        p.y = initialY + dy
                        try {
                            windowManager?.updateViewLayout(overlayView, p)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isClick) {
                            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            if (launchIntent != null) {
                                startActivity(launchIntent)
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun updateUi(count: Int) {
        val state = BrainState.fromCount(count)
        tvCount?.text = "$count"
        tvStatus?.text = "● ${state.label}"
        tvStatus?.setTextColor(ContextCompat.getColor(this, state.colorRes))
        imgMascot?.setImageResource(state.drawableRes)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE_COUNT -> {
                val count = intent.getIntExtra(EXTRA_COUNT, prefs.getTodayReels())
                updateUi(count)
                if (overlayView?.visibility != View.VISIBLE && prefs.isOverlayEnabled()) {
                    overlayView?.visibility = View.VISIBLE
                }
            }
            ACTION_SHOW -> {
                if (prefs.isOverlayEnabled()) {
                    if (overlayView == null) {
                        initOverlay()
                    } else {
                        overlayView?.visibility = View.VISIBLE
                    }
                }
            }
            ACTION_HIDE -> {
                overlayView?.visibility = View.GONE
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (overlayView != null) {
                windowManager?.removeView(overlayView)
                overlayView = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
