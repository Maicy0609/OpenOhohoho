package com.open.ohohoho.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.open.ohohoho.R
import com.open.ohohoho.util.AppLog
import androidx.core.app.NotificationCompat

/**
 * 悬浮窗日志服务：
 * 在屏幕顶部显示一个半透明可拖动的面板，实时展示 AppLog 的日志，
 * 方便调试查看"当前聊天输入内容"与转换结果。
 *
 * 需要 SYSTEM_ALERT_WINDOW（悬浮窗）权限。
 */
class OverlayLogService : Service(), AppLog.Listener {

    private lateinit var wm: WindowManager
    private lateinit var textView: TextView
    private var rootView: View? = null
    private val uiHandler = Handler(Looper.getMainLooper())

    private val history = ArrayDeque<String>()
    private val MAX_SHOWN = 12

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (rootView == null) showOverlay()
        AppLog.register(this)
        OverlayHelper.running = true
        return START_STICKY
    }

    private fun startAsForeground() {
        if (Build.VERSION.SDK_INT < 26) return
        val chId = "overlay_log"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(chId, getString(R.string.overlay_notification_channel), NotificationManager.IMPORTANCE_LOW)
        )
        val n = NotificationCompat.Builder(this, chId)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setSmallIcon(R.drawable.ic_stat_log)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    private fun showOverlay() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        textView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 11f
            setPadding(16, 8, 16, 8)
            gravity = Gravity.START or Gravity.TOP
        }

        // 顶部标题栏 + 关闭按钮
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 2, 4, 2)
        }
        val title = TextView(this).apply {
            text = "日志"
            setTextColor(Color.rgb(255, 190, 120))
            textSize = 12f
        }
        val close = Button(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 12f
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(16, 0, 16, 0)
            setOnClickListener { stopSelf() }  // 关闭悬浮窗
        }
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(close)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(170, 20, 20, 20))
            addView(header)
            addView(textView)
            setOnTouchListener(dragTouchListener)  // 整个面板可拖动
        }

        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 160
        }

        rootView = container
        try {
            wm.addView(container, params)
        } catch (e: Exception) {
            AppLog.log("悬浮窗添加失败: ${e.message}")
            stopSelf()
        }
    }

    /** 拖动整个面板。 */
    private val dragTouchListener = View.OnTouchListener { v, event ->
        val lp = (v.layoutParams as WindowManager.LayoutParams)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                v.tag = DragState(lp.x, lp.y, event.rawX, event.rawY)
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val s = v.tag as? DragState
                if (s != null) {
                    lp.x = s.initialX + (event.rawX - s.touchX).toInt()
                    lp.y = s.initialY + (event.rawY - s.touchY).toInt()
                    try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
                }
                true
            }
            else -> false
        }
    }

    private data class DragState(val initialX: Int, val initialY: Int, val touchX: Float, val touchY: Float)

    /** AppLog.Listener：把新日志追加到悬浮窗。 */
    override fun onLog(line: String) {
        uiHandler.post {
            history.addLast(line)
            while (history.size > MAX_SHOWN) history.removeFirst()
            textView.text = history.joinToString("\n")
        }
    }

    override fun onDestroy() {
        AppLog.unregister(this)
        OverlayHelper.running = false
        try {
            rootView?.let { wm.removeView(it) }
        } catch (_: Exception) {}
        rootView = null
        super.onDestroy()
    }
}
