package com.open.ohohoho

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
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

        // 发送按钮资源 id 候选（QQ 发送按钮 id 相对稳定；可补充变体）
        private val SEND_IDS = setOf(
            "com.tencent.mobileqq:id/send_btn",     // QQ 发送按钮
            "com.tencent.mobileqq:id/ivTitleBtnRightButton", // QQ 部分版本
            "com.tencent.mm:id/ai7",                // 微信发送按钮(常见 id)
            "com.tencent.mm:id/aa0",                // 微信发送按钮(变体)
        )

        // 通用发送按钮识别关键词（配合 isSendButton）
        private val SEND_KEYWORDS = listOf("发送", "送出", "提交", "send", "submit", "enter", "➤")

        // 可编辑输入节点的类名兜底（覆盖 WebView / Compose / Flutter 输入框）
        private val EDIT_TEXT_CLASSES = listOf("EditText", "TextInput", "TextField")

        // 默认排除包名（输入法 / 桌面 / 系统设置等），防止误改写
        private val DEFAULT_EXCLUDE = setOf(
            // 输入法
            "com.android.inputmethod.latin", "com.google.android.inputmethod.latin",
            "com.sohu.inputmethod.sogou", "com.tencent.qqpinyin", "com.baidu.input",
            "com.android.inputmethod.pinyin", "com.iflytek.inputmethod",
            // 桌面启动器
            "com.android.launcher", "com.android.launcher3", "com.google.android.apps.nexuslauncher",
            "com.miui.home", "com.huawei.android.launcher", "com.oppo.launcher",
            "com.vivo.launcher", "com.sec.android.app.launcher",
            // 系统设置 / 系统界面
            "com.android.settings", "com.android.systemui", "com.android.permissioncontroller",
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
    private var pendingEmoticon: String? = null // 当前消息稳定使用的颜文字（避免循环）
    private val debounceHandler = Handler(Looper.getMainLooper()) // 流式输入防抖

    override fun onServiceConnected() {
        super.onServiceConnected()

        val cfg = CatConfig.load(this)

        val info = AccessibilityServiceInfo().apply {
            eventTypes = TYPE_WINDOW_STATE_CHANGED or TYPE_VIEW_CLICKED or
                TYPE_VIEW_TEXT_CHANGED or TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = 0x1 or // FLAG_DEFAULT
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 50
            // 监听所有应用，由 shouldProcess() 按最新配置即时过滤，
            // 从而保证黑白名单改动立即生效（无需重启服务）
            packageNames = null
        }
        setServiceInfo(info)

        cachedConfig = cfg
        AppLog.log(
            "无障碍服务已连接，模式=${if (cfg.isWhitelistMode) "白名单" else "黑名单"}，应用=${cfg.managedPackages.joinToString()}"
        )

        // 打开悬浮窗日志，方便调试（若用户已授予悬浮窗权限）
        OverlayHelper.ensureOverlayLog(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString() ?: ""
        if (pkg.isEmpty() || !shouldProcess(pkg)) return

        when (event.eventType) {
            TYPE_WINDOW_STATE_CHANGED -> {
                // 记录跳转到的应用，及按黑白名单判定是处理还是跳过
                AppLog.log("窗口切换 -> $pkg（${if (shouldProcess(pkg)) "处理" else "跳过"}）")
                // 窗口切换：重置状态并重新加载配置
                processing = false
                userOriginal = ""
                lastSet = ""
                lastWriteTime = 0L
                pendingEmoticon = null
                cachedConfig = CatConfig.load(this)
            }

            TYPE_VIEW_CLICKED -> {
                // 点击发送按钮：兜底处理一次（id 匹配 + 通用关键词识别）
                val source = event.source
                if (source != null) {
                    val isSend = source.viewIdResourceName in SEND_IDS || isSendButton(source)
                    source.recycle()
                    if (isSend) {
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
            // 流式防抖：输入稳定 400ms 后再处理，语音/粘贴时避免反复改写
            debounceHandler.removeCallbacksAndMessages(null)
            val captured = source?.let { AccessibilityNodeInfo.obtain(it) }
            debounceHandler.postDelayed({
                try {
                    doProcess(isFinal = false, source = captured)
                } catch (t: Throwable) {
                    AppLog.log("实时处理异常: ${t.message}")
                }
                captured?.recycle()
            }, 400)
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
            // 双保险：确认当前活动窗口是目标应用，避免误写其它应用
            val rpkg = root.packageName?.toString() ?: ""
            if (rpkg.isEmpty() || !shouldProcess(rpkg)) {
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
                pendingEmoticon = null
                node.recycle()
                return
            }

            // 忽略"我们自己的写回"：当前文本恰好等于上次写入的内容 → 直接跳过，避免二次处理累积
            if (lastSet.isNotEmpty() && text == lastSet) {
                node.recycle()
                return
            }

            // 拦截官方提示词 / 占位短文本（发送后出现的短提示等）
            if (text.trim().length < 2) {
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

            // 生成目标文本（先不含随机颜文字，颜文字单独稳定追加）
            val cfgBase = config.copy(enableRandomEmoticon = false)
            val target = TextProcessor.process(stripped, cfgBase)

            // 追加"稳定"颜文字：一条消息内固定同一个，避免写回触发的事件反复换字导致循环
            var finalTarget = target
            if (config.enableRandomEmoticon) {
                if (pendingEmoticon == null) {
                    pendingEmoticon = TextProcessor.getRandomEmoticon(config).ifEmpty { null }
                }
                if (pendingEmoticon != null) {
                    finalTarget = "$target ${pendingEmoticon}"
                }
            }

            if (finalTarget == text) {
                node.recycle()
                return
            }

            AppLog.log("写入: raw=$text → target=$finalTarget")

            // 光标移到颜文字前面，让后续输入插到颜文字之前（曲线救国）
            var cursorPos = finalTarget.length
            val emo = pendingEmoticon
            if (emo != null && finalTarget.endsWith(emo)) {
                cursorPos = finalTarget.length - emo.length - 1 // 前面还留了一个空格
            }

            if (setText(node, finalTarget, cursorPos)) {
                lastSet = finalTarget
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

    /** 通用发送按钮识别：可点击 + 非输入框 + 类名像按钮 + 文本/内容描述含发送关键词。 */
    private fun isSendButton(node: AccessibilityNodeInfo): Boolean {
        if (!node.isClickable) return false
        val cls = node.className?.toString() ?: return false
        if (isEditTextClass(node)) return false
        val text =
            ((node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")).lowercase()
        val looksLikeButton = cls.contains("Button") || cls.contains("ImageButton")
        return looksLikeButton && SEND_KEYWORDS.any { text.contains(it, ignoreCase = true) }
    }

    /** 通过无障碍 action 写入文本并把光标放到 [cursorPos]；失败则用剪贴板粘贴兜底。 */
    private fun setText(node: AccessibilityNodeInfo, text: String, cursorPos: Int = text.length): Boolean {
        // 方法1：ACTION_SET_TEXT
        val setBundle = Bundle().apply {
            putCharSequence(ARG_SET_TEXT, text)
        }
        if (node.performAction(ACTION_SET_TEXT, setBundle)) {
            val pos = cursorPos.coerceIn(0, text.length)
            val selBundle = Bundle().apply {
                putInt(ARG_SEL_START, pos)
                putInt(ARG_SEL_END, pos)
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

    /** 按黑白名单 + 默认排除判断是否处理该包名（每次读最新配置，保证改动即时生效）。 */
    private fun shouldProcess(pkg: String): Boolean {
        // 永远不处理自身包名（防止改写配置界面自己的输入框）
        if (pkg == packageName) return false
        // 默认排除输入法 / 桌面 / 系统设置等
        if (pkg in DEFAULT_EXCLUDE) return false

        val cfg = CatConfig.load(this)
        cachedConfig = cfg
        return if (cfg.isWhitelistMode) pkg in cfg.managedPackages
               else pkg !in cfg.managedPackages
    }

    /** 定位聊天输入框：优先按已知 id 精确匹配（QQ/微信），否则跨窗口找可编辑节点（跳过输入法窗口）。 */
    private fun findInputNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (id in INPUT_IDS) {
            findNodeById(root, id)?.let { return it }
        }
        return findEditableAcrossWindows()
    }

    /** 遍历所有窗口查找输入框，跳过输入法/无障碍覆盖层窗口（键盘弹出时不被 IME 抢占）。 */
    private fun findEditableAcrossWindows(): AccessibilityNodeInfo? {
        return try {
            for (w in getWindows()) {
                val type = w.type
                if (type == AccessibilityWindowInfo.TYPE_INPUT_METHOD ||
                    type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY
                ) continue
                val root = w.root ?: continue
                val found = findEditable(root)
                root.recycle()
                if (found != null) return found
            }
            null
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * 查找输入框节点：跳过密码框，匹配可编辑节点或 EditText/TextInput/TextField 类名；
     * 优先返回「已聚焦」的可编辑节点。
     */
    private fun findEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val isEdit = root.isEditable || isEditTextClass(root)
        if (isEdit && !root.isPassword && root.isVisibleToUser) {
            return AccessibilityNodeInfo.obtain(root)
        }
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findEditable(child)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun isEditTextClass(node: AccessibilityNodeInfo): Boolean {
        val cls = node.className?.toString() ?: return false
        return EDIT_TEXT_CLASSES.any { cls.contains(it) }
    }
}
