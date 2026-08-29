package com.open.ohohoho.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.open.ohohoho.R

/**
 * 保活服务：前台服务 + 持续通知 + 一个完全透明的 1px 悬浮窗，
 * 提高进程优先级，让无障碍改写服务不被系统/省电策略杀掉。
 */
class KeepAliveService : Service() {

    companion object {
        @Volatile
        var running: Boolean = false
    }

    private lateinit var wm: WindowManager
    private var overlay: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (overlay == null) showTransparentOverlay()
        running = true
        return START_STICKY
    }

    private fun startAsForeground() {
        if (Build.VERSION.SDK_INT < 26) return
        val chId = "keep_alive"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(chId, "保活", NotificationManager.IMPORTANCE_MIN)
        )
        val n = NotificationCompat.Builder(this, chId)
            .setContentTitle(getString(R.string.keepalive_title))
            .setContentText(getString(R.string.keepalive_text))
            .setSmallIcon(R.drawable.ic_stat_log)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setShowWhen(false)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    /** 添加一个完全透明、1px、不可触摸的悬浮窗（不可见，仅作保活辅助）。 */
    private fun showTransparentOverlay() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = View(this) // 默认透明背景
        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            1, 1, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSPARENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        try {
            wm.addView(view, params)
            overlay = view
        } catch (_: Throwable) {
            // 未授予悬浮窗权限等：忽略，前台服务+通知仍可保活
        }
    }

    override fun onDestroy() {
        running = false
        try {
            overlay?.let { wm.removeView(it) }
        } catch (_: Throwable) {}
        overlay = null
        super.onDestroy()
    }
}
