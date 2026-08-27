package com.open.ohohoho.overlay

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.open.ohohoho.util.AppLog

/** 悬浮窗日志的启停助手。 */
object OverlayHelper {

    /** 启动悬浮窗日志（仅在已授予悬浮窗权限时）。 */
    fun ensureOverlayLog(context: Context) {
        if (!Settings.canDrawOverlays(context)) {
            AppLog.log("未授予悬浮窗权限，跳过日志悬浮窗")
            return
        }
        try {
            context.startService(Intent(context, OverlayLogService::class.java))
        } catch (t: Throwable) {
            AppLog.log("启动日志悬浮窗失败: ${t.message}")
        }
    }

    fun stopOverlayLog(context: Context) {
        try {
            context.stopService(Intent(context, OverlayLogService::class.java))
        } catch (t: Throwable) {
            AppLog.log("停止日志悬浮窗失败: ${t.message}")
        }
    }
}
