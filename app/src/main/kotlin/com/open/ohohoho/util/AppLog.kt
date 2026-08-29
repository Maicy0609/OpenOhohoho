package com.open.ohohoho.util

import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 轻量级进程内日志总线：
 *  - 同时写入系统 Logcat（便于 Android Studio 调试）
 *  - 分发给悬浮窗日志服务（OverlayLogService）实时显示
 *  - 维护一个环形缓冲，最近 300 条
 *
 * 注意：日志仅在本机内存/悬浮窗展示，不进行任何网络外传。
 */
object AppLog {

    const val TAG = "OpenOhoho"

    interface Listener {
        fun onLog(line: String)
    }

    private val listeners = CopyOnWriteArrayList<Listener>()
    private val ring = ArrayDeque<String>()
    private val MAX_LINES = 300

    /** 记录一条日志。 */
    @JvmStatic
    fun log(msg: String, tag: String = TAG) {
        val line = "[${now()}] $msg"
        Log.d(tag, msg)

        synchronized(ring) {
            ring.addLast(line)
            if (ring.size > MAX_LINES) ring.removeFirst()
        }
        listeners.forEach { it.onLog(line) }
    }

    /** 便捷别名：记录"当前聊天输入内容"。 */
    @JvmStatic
    fun input(raw: String, tag: String = TAG) {
        log("当前输入: ${raw.take(200)}", tag)
    }

    /** 悬浮窗服务注册/注销。 */
    fun register(listener: Listener) {
        listeners.addIfAbsent(listener)
        replay(listener)
    }

    fun unregister(listener: Listener) {
        listeners.remove(listener)
    }

    fun recent(): List<String> = synchronized(ring) { ring.toList() }

    /** 清空环形缓冲（悬浮窗清空按钮调用）。 */
    fun clear() {
        synchronized(ring) { ring.clear() }
    }

    private fun replay(listener: Listener) {
        synchronized(ring) { ring.toList() }.forEach { listener.onLog(it) }
    }

    private fun now(): String =
        java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
}
