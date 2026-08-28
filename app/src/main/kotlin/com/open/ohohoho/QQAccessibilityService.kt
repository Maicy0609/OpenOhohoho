package com.open.ohohoho

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
        // 输入框资源 id 候选（微信 id 随版本变化，主要靠 findEditable 兜底）
        private val INPUT_IDS = setOf(
            "com.tencent.mobileqq:id/input", // QQ 输入框
            "com.tencent.mm:id/auj",         // 微信输入框(常见 id)
            "com.tencent.mm:id/b3c",         // 微信输入框(常见 id)
        )

        // 发送按钮资源 id 候选
        private val SEND_IDS = setOf(
            "com.tencent.mobileqq:id/send_btn", // QQ 发送按钮
            "com.tencent.mm:id/ai7",            // 微信发送按钮(常见 id)
        )

        // 目标应用包名：QQ 家族 + 微信
        private val TARGET_PACKAGES = setOf(
            "com.tencent.mobileqq",   // QQ 正式版
            "com.tencent.mobileqqi",  // QQ 国际版
            "com.tencent.qqlite",     // QQ 轻聊版
            "com.tencent.tim",        // TIM
            "com.tencent.mm",         // 微信
        )

        // 事件类型常量
        private const val TYPE_WINDOW_STATE_CHANGED = 0x00000020
        private const val TYPE_VIEW_CLICKED = 0x00000001
        private const val TYPE_VIEW_TEXT_CHANGED = 0x00000010
        private const val TYPE_WINDOW_CONTENT_CHANGED = 0x00000800

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
            eventTypes = TYPE_WINDOW_STATE_CHANGED or TYPE_VIEW_CLICKED or
                TYPE_VIEW_TEXT_CHANGED or TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = 0x1 or // FLAG_DEFAULT
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 50
            packageNames = TARGET_PACKAGES.toTypedArray()
        }
        setServiceInfo(info)

        cachedConfig = CatConfig.load(this)
        AppLog.log("无障碍服务已连接，包名=${TARGET_PACKAGES.joinToString()}")

        // 打开悬浮窗日志，方便调试（若用户已授予悬浮窗权限）
        OverlayHelper.ensureOverlayLog(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString() ?: ""
        if (pkg !in TARGET_PACKAGES) return

        when (event.eventType) {
            TYPE_WINDOW_STATE_CHANGED -> {
                // 窗口切换：重置状态并重新加载配置
                processing = false
                userOriginal = ""
                lastSet = ""
                lastWriteTime = 0L
                cachedConfig = CatConfig.load(this)
                // 调试开关开启时，把当前窗口所有可编辑节点 id 打到悬浮窗日志
                if (debugDumpEnabled()) dumpEditableNodes()
            }

            TYPE_VIEW_CLICKED -> {
                // 点击发送按钮：兜底处理一次
                val source = event.source
                if (source != null) {
                    val id = source.viewIdResourceName
                    source.recycle()
                    if (id in SEND_IDS) {
                        AppLog.log("点击发送，兜底处理")
                        doProcess(isFinal = true)
                    }
                }
            }

            TYPE_VIEW_TEXT_CHANGED, TYPE_WINDOW_CONTENT_CHANGED -> handleInputChange(event)
        }
    }

    override fun onInterrupt() {
        processing = false
    }

    /** 处理输入变化事件（TYPE_VIEW_TEXT_CHANGED / TYPE_WINDOW_CONTENT_CHANGED）。 */
    private fun handleInputChange(event: AccessibilityEvent) {
        // 每次重新加载，让功能开关/处理模式改动立即生效
        cachedConfig = CatConfig.load(this)
        val mode = cachedConfig?.processingMode ?: CatConfig.MODE_PUNCTUATION
        // 事件的 source 就是输入框节点本身（对微信尤其可靠）
        val source = event.source
        if (mode == CatConfig.MODE_REALTIME) {
            doProcess(isFinal = false, source = source)
        } else {
            // 标点触发模式：仅当输入以标点结尾时处理
            val text = source?.text?.toString()
            if (text != null) {
                val trimmed = text.trim()
                if (trimmed.isNotEmpty() && isPunctuationEnding(trimmed)) {
                    AppLog.log("标点触发: $trimmed")
                    doProcess(isFinal = false, source = source)
                }
            }
        }
        source?.recycle()
    }

    private fun doProcess(isFinal: Boolean, source: AccessibilityNodeInfo? = null) {
        if (processing) return
        processing = true
        try {
            val root = rootInActiveWindow ?: return
            // 双保险：确认当前活动窗口确实是目标应用，避免误写其它应用
            if (root.packageName?.toString() !in TARGET_PACKAGES) {
                root.recycle()
                return
            }
            // 优先使用事件源节点（打字事件的 source 就是输入框本身，微信最可靠）；
            // 否则回退到在根节点上查找输入框
            val node = if (source != null && (source.isEditable || source.isFocused)) {
                AccessibilityNodeInfo.obtain(source)
            } else {
                findInputNode(root)
            }
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

            // 每次处理都读取最新配置，保证开关改动即时生效
            val config = CatConfig.load(this)
            cachedConfig = config

            // 增量还原原始输入
            val stripped = reconstructUserText(text, config)

            if (stripped.isEmpty()) {
                AppLog.log("原文为空，跳过")
                node.recycle()
                return
            }

            // 生成目标文本
            var cfgForProcess = config
            // 打字过程中保持确定性（临时关闭随机颜文字），避免写回触发的事件反复重写/闪烁；
            // 仅在最终（点击发送）时附加随机颜文字
            if (!isFinal && config.enableRandomEmoticon) {
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

    /** 通过无障碍 action 写入文本并设置光标到末尾；失败则用剪贴板粘贴兜底。 */
    private fun setText(node: AccessibilityNodeInfo, text: String): Boolean {
        // 方法1：ACTION_SET_TEXT
        val setBundle = Bundle().apply {
            putCharSequence(ARG_SET_TEXT, text)
        }
        if (node.performAction(ACTION_SET_TEXT, setBundle)) {
            val selBundle = Bundle().apply {
                putInt(ARG_SEL_START, text.length)
                putInt(ARG_SEL_END, text.length)
            }
            node.performAction(ACTION_SET_SELECTION, selBundle)
            return true
        }
        // 方法2：剪贴板 + ACTION_PASTE（部分微信版本不响应 SET_TEXT）
        return try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("ohoho", text))
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        } catch (t: Throwable) {
            false
        }
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

    /** 调试开关：是否抓取界面可编辑节点。 */
    private fun debugDumpEnabled(): Boolean = try {
        getSharedPreferences("debug", MODE_PRIVATE).getBoolean("dump", false)
    } catch (t: Throwable) {
        false
    }

    /** 调试：打印当前窗口整棵 UI 树（最多 80 个节点），用于诊断微信/QQ 暴露了哪些节点。 */
    private fun dumpEditableNodes() {
        val root = rootInActiveWindow ?: run {
            AppLog.log("UI抓取: rootInActiveWindow 为空（窗口内容未暴露给无障碍）")
            return
        }
        try {
            val sb = StringBuilder()
            var n = 0
            fun walk(node: AccessibilityNodeInfo, depth: Int) {
                if (n > 80) return
                val cls = node.className?.toString() ?: ""
                val id = node.viewIdResourceName ?: ""
                val editable = if (node.isEditable) " [可编辑]" else ""
                val txt = node.text?.toString()?.take(24) ?: ""
                sb.append("\n[d$depth] cls=$cls id=$id$editable txt=\"$txt\"")
                n++
                for (i in 0 until node.childCount) {
                    val c = node.getChild(i) ?: continue
                    walk(c, depth + 1)
                    c.recycle()
                }
            }
            walk(root, 0)
            AppLog.log("UI树($n 节点): $sb")
        } finally {
            root.recycle()
        }
    }

    /** 定位聊天输入框：优先按已知 id 精确匹配（QQ/微信），兜底找第一个可见可编辑节点。 */
    private fun findInputNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (id in INPUT_IDS) {
            findNodeById(root, id)?.let { return it }
        }
        return findEditable(root)
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
