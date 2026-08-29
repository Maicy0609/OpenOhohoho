package com.open.ohohoho.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.open.ohohoho.CatConfig
import com.open.ohohoho.R
import com.open.ohohoho.TextProcessor
import com.open.ohohoho.util.AppLog

/**
 * 微信等被无障碍屏蔽的应用的手动输入悬浮窗：
 * 用户在其中输入文字，点"处理并复制"后按软件规则处理并写入剪贴板，再回微信手动粘贴。
 * 可选项，由用户在设置中启停。
 */
class OverlayInputService : Service() {

    companion object {
        @Volatile
        var running: Boolean = false
    }

    private lateinit var wm: WindowManager
    private lateinit var editText: EditText
    private var rootView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (rootView == null) showOverlay()
        running = true
        return START_STICKY
    }

    private fun startAsForeground() {
        if (Build.VERSION.SDK_INT < 26) return
        val chId = "overlay_input"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(chId, "输入处理悬浮窗", NotificationManager.IMPORTANCE_LOW)
        )
        val n = NotificationCompat.Builder(this, chId)
            .setContentTitle("OpenOhoho 输入处理")
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

        editText = EditText(this).apply {
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(200, 200, 200))
            setHint("在此输入要处理的文字…")
            textSize = 14f
            setBackgroundColor(Color.rgb(50, 50, 50))
            setPadding(16, 12, 16, 12)
            gravity = Gravity.START or Gravity.TOP
            minLines = 4
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 2, 4, 2)
        }
        val title = TextView(this).apply {
            text = "输入处理"
            setTextColor(Color.rgb(255, 190, 120))
            textSize = 12f
        }
        val close = Button(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 12f
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(16, 0, 16, 0)
            setOnClickListener { stopSelf() }
        }
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(close)

        val clearBtn = Button(this).apply {
            text = "清空"
            setTextColor(Color.WHITE)
            textSize = 13f
            setBackgroundColor(Color.rgb(90, 90, 90))
            setOnClickListener { editText.setText("") }
        }
        val processBtn = Button(this).apply {
            text = "处理并复制"
            setTextColor(Color.WHITE)
            textSize = 13f
            setBackgroundColor(Color.rgb(255, 111, 0))
            setOnClickListener { processAndCopy() }
        }
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 4, 8, 6)
        }
        btnRow.addView(clearBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        btnRow.addView(processBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(230, 25, 25, 25))
            addView(header)
            addView(editText)
            addView(btnRow)
            setOnTouchListener(dragTouchListener)
        }

        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            // 窗口需可聚焦，点击输入框才能弹起输入法
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 120
        }

        rootView = container
        try {
            wm.addView(container, params)
            // 主动聚焦输入框并弹出输入法
            container.post {
                editText.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
            }
        } catch (e: Exception) {
            AppLog.log("输入悬浮窗添加失败: ${e.message}")
            stopSelf()
        }
    }

    /** 按规则处理输入内容并复制到剪贴板。 */
    private fun processAndCopy() {
        val raw = editText.text?.toString()?.trim().orEmpty()
        if (raw.isEmpty()) {
            toast("请输入内容")
            return
        }
        val config = CatConfig.load(this)
        val processed = TextProcessor.process(raw, config)
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("ohoho", processed))
        toast("已复制：$processed")
        AppLog.log("输入处理复制: $processed")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

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

    override fun onDestroy() {
        running = false
        try {
            rootView?.let { wm.removeView(it) }
        } catch (_: Exception) {}
        rootView = null
        super.onDestroy()
    }
}
