package com.open.ohohoho.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.open.ohohoho.CatConfig
import com.open.ohohoho.TextProcessor

/**
 * 剪贴板改写：读取剪贴板文本 → 按规则处理 → 写回剪贴板。
 * 供快捷设置磁贴 / 前台 Activity 调用（前台读取剪贴板不受 Android 10+ 后台限制）。
 */
object ClipboardRewriter {

    /** 执行改写并返回提示消息。 */
    fun rewrite(context: Context): String {
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return "系统剪贴板不可用"

            val clip = cm.primaryClip
            val text = clip?.getItemAt(0)?.coerceToText(context)?.toString() ?: ""
            if (text.trim().isEmpty()) return "剪贴板没有文本"

            val cfg = CatConfig.load(context)
            val result = TextProcessor.process(text, cfg).ifEmpty { text }

            cm.setPrimaryClip(ClipData.newPlainText("ohoho", result))
            if (result == text) "已改写（规则未命中，内容不变）" else "已改写，去粘贴吧"
        } catch (t: Throwable) {
            "改写失败：${t.message}"
        }
    }
}
