package com.open.ohohoho

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.open.ohohoho.overlay.OverlayHelper
import com.open.ohohoho.util.AppLog

/**
 * 无障碍服务：监听 QQ 输入框，把用户输入实时改写为"哦齁齁齁♥"装饰文本。
 *
 * 安全说明：
 *  - 仅在本机处理/改写输入框文本，日志通过 AppLog 展示在悬浮窗/Logcat，
 *    不会把任何聊天内容上传网络。
 *  - 无障碍权限属于敏感能力，仅在用户手动（或在 Shizuku 确认后）启用时工作。
 */
class QQAccessibilityService : AccessibilityService() {

    companion object {
        const val ID_INPUT = "com.tencent.mobileqq:id/input"
        const val ID_SEND = "com.tencent.mobileqq:id/send_btn"

        // QQ 家族常见包名（扩展以提升识别正确率）
        private val QQ_PACKAGES = setOf(
            "com.tencent.mobileqq",   // QQ 正式版
            "com.tencent.mobileqqi",  // QQ 国际版
            "com.tencent.qqlite",     // QQ 轻聊版
            "com.tencent.tim",        // TIM
        )

        // 事件类型常量
        private const val TYPE_WINDOW_STATE_CHANGED = 0x00000020
        private const val TYPE_VIEW_CLICKED = 0x00000001
        private const val TYPE_VIEW_TEXT_CHANGED = 0x00000010

        // performAction 常量
        private const val ACTION_SET_TEXT = 0x00200000
        private const val ACTION_SET_SELECTION = 0x00020000
        private const val ARG_SET_TEXT = "ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE"
        private const val ARG_SEL_START = "ACTION_ARGUMENT_SELECTION_START_INT"
        private const val ARG_SEL_END = "ACTION_ARGUMENT_SELECTION_END_INT"
    }

    private var userOriginal = ""      // 还原后的用户原始输入
    private var lastSet = ""           // 上次写入输入框的内容
    private var processing = false
    private var lastWriteTime = 0L
    private var cachedConfig: CatConfig? = null

    override fun onServiceConnected() {
        super.onServiceConnected()

        val info = AccessibilityServiceInfo().apply {
            eventTypes = TYPE_WINDOW_STATE_CHANGED or TYPE_VIEW_CLICKED or TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = 0x1 or // FLAG_DEFAULT
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 50
            packageNames = QQ_PACKAGES.toTypedArray()
        }
        setServiceInfo(info)

        cachedConfig = CatConfig.load(this)
        AppLog.log("无障碍服务已连接，包名=${QQ_PACKAGES.joinToString()}")

        // 打开悬浮窗日志，方便调试（若用户已授予悬浮窗权限）
        OverlayHelper.ensureOverlayLog(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString() ?: ""
        if (pkg !in QQ_PACKAGES) return

        when (event.eventType) {
            TYPE_WINDOW_STATE_CHANGED -> {
                // 窗口切换：重置状态并重新加载配置
                processing = false
                userOriginal = ""
                lastSet = ""
                lastWriteTime = 0L
                cachedConfig = CatConfig.load(this)
            }

            TYPE_VIEW_CLICKED -> {
                // 点击发送按钮：兜底处理一次
                val source = event.source
                if (source != null) {
                    val id = source.viewIdResourceName
                    source.recycle()
                    if (id == ID_SEND) {
                        AppLog.log("点击发送，兜底处理")
                        doProcess(realtime = true)
                    }
                }
            }

            TYPE_VIEW_TEXT_CHANGED -> {
                val mode = cachedConfig?.processingMode ?: CatConfig.MODE_PUNCTUATION
                if (mode == CatConfig.MODE_REALTIME) {
                    doProcess(realtime = true)
                } else {
                    // 标点触发模式：仅当输入以标点结尾时处理
                    val root = rootInActiveWindow ?: return
                    val input = findNodeById(root, ID_INPUT) ?: findEditable(root)
                    root.recycle()
                    val text = input?.text?.toString()
                    input?.recycle()
                    if (text.isNullOrEmpty()) return
                    val trimmed = text.trim()
                    if (trimmed.isEmpty()) return
                    if (isPunctuationEnding(trimmed)) {
                        AppLog.log("标点触发: $trimmed")
                        doProcess(realtime = false)
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        processing = false
    }

    private fun doProcess(realtime: Boolean) {
        if (processing) return
        processing = true
        try {
            val root = rootInActiveWindow ?: return
            // 双保险：确认当前活动窗口确实是 QQ，避免误写其它应用
            if (root.packageName?.toString() !in QQ_PACKAGES) {
                root.recycle()
                return
            }
            val node = findNodeById(root, ID_INPUT) ?: findEditable(root)
            root.recycle()
            if (node == null) return

            val text = node.text?.toString() ?: ""
            if (text.trim().isEmpty()) {
                userOriginal = ""
                lastSet = ""
                node.recycle()
                return
            }

            // —— 日志：输出当前聊天输入内容 ——
            AppLog.input(text)

            var config = cachedConfig
            if (config == null) {
                config = CatConfig.load(this)
                cachedConfig = config
            }

            // 增量还原原始输入
            val stripped = reconstructUserText(text, config)

            if (stripped.isEmpty()) {
                AppLog.log("原文为空，跳过")
                node.recycle()
                return
            }

            // 生成目标文本
            var cfgForProcess = config
            if (!realtime && config.enableRandomEmoticon) {
                // 非实时模式下避免反复追加不同颜文字，克隆一份关闭随机颜文字
                cfgForProcess = config.copy(enableRandomEmoticon = false)
            }
            val target = TextProcessor.process(stripped, cfgForProcess)

            if (target == text) {
                node.recycle()
                return
            }

            AppLog.log("写入: raw=$text → target=$target")
            if (setText(node, target)) {
                lastSet = target
                lastWriteTime = System.currentTimeMillis()
            }
            node.recycle()
        } finally {
            processing = false
        }
    }

    /**
     * 从输入框中当前文本还原出用户真正输入的内容（去掉上次加的装饰/颜文字）。
     */
    private fun reconstructUserText(text: String, config: CatConfig): String {
        val current = text.trim()

        // 若上次写入内容为空（第一次），直接剥离本次文本
        if (lastSet.isEmpty()) {
            userOriginal = TextProcessor.stripAll(current, config)
            return userOriginal
        }

        // 前缀增量：本次文本以 lastSet 开头，说明用户是追加输入，还原时拼上增量
        if (current.startsWith(lastSet)) {
            val delta = current.substring(lastSet.length)
            userOriginal += delta
            AppLog.log("前缀增量: +$delta  userOriginal=$userOriginal")
            return userOriginal
        }

        // 不匹配：重新剥离
        userOriginal = TextProcessor.stripAll(current, config)
        return userOriginal
    }

    private fun isPunctuationEnding(s: String): Boolean {
        if (s.isEmpty()) return false
        val c = s.last()
        return c in "，。！!？? "
    }

    /** 通过无障碍 action 写入文本并设置光标到末尾。 */
    private fun setText(node: AccessibilityNodeInfo, text: String): Boolean {
        val setBundle = Bundle().apply {
            putCharSequence(ARG_SET_TEXT, text)
        }
        if (!node.performAction(ACTION_SET_TEXT, setBundle)) return false

        val selBundle = Bundle().apply {
            putInt(ARG_SEL_START, text.length)
            putInt(ARG_SEL_END, text.length)
        }
        return node.performAction(ACTION_SET_SELECTION, selBundle)
    }

    /** 深度优先按 viewId 查找节点。 */
    private fun findNodeById(root: AccessibilityNodeInfo, id: String): AccessibilityNodeInfo? {
        if (root.viewIdResourceName == id) return AccessibilityNodeInfo.obtain(root)
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findNodeById(child, id)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    /** 查找第一个「可见」的可编辑节点（避免命中隐藏控件 / 搜索框等）。 */
    private fun findEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isEditable && root.isVisibleToUser) return AccessibilityNodeInfo.obtain(root)
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findEditable(child)
            child.recycle()
            if (found != null) return found
        }
        return null
    }
}
