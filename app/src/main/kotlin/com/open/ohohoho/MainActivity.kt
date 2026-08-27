package com.open.ohohoho

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.open.ohohoho.overlay.OverlayHelper
import com.open.ohohoho.shizuku.ShizukuManager
import com.open.ohohoho.util.AppLog
import android.Manifest

class MainActivity : AppCompatActivity() {

    private lateinit var config: CatConfig

    // 控件
    private lateinit var statusText: TextView
    private lateinit var shizukuStatusText: TextView
    private lateinit var btnToggleOverlay: Button

    private lateinit var rbPunctuation: CheckBox
    private lateinit var rbRealtime: CheckBox
    private lateinit var cbMeow: CheckBox
    private lateinit var cbWo: CheckBox
    private lateinit var cbNi: CheckBox
    private lateinit var cbEmoticon: CheckBox
    private lateinit var etCustom: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = try {
            CatConfig.load(this)
        } catch (e: Throwable) {
            CatConfig()
        }
        setContentView(buildUi())
        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        updateShizukuStatus()
        updateOverlayButton()
    }

    // ---------- UI 构建 ----------
    private fun buildUi(): ScrollView {
        val scroll = ScrollView(this)
        scroll.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 40, 48, 40)
            setBackgroundColor(Color.parseColor("#FFF8E1"))
        }
        scroll.addView(root)

        root.addView(title("OpenOhoho"))
        root.addView(sectionLabel("服务状态"))
        statusText = infoText()
        root.addView(statusText)
        shizukuStatusText = infoText()
        root.addView(shizukuStatusText)

        btnToggleOverlay = Button(this).apply {
            text = "启动日志悬浮窗"
            setOnClickListener { toggleOverlay() }
        }
        root.addView(btnToggleOverlay, matchParams())

        root.addView(divider())
        root.addView(btn("开启无障碍服务") { openAccessibilitySettings() })

        root.addView(btn("授予 Shizuku 权限") {
            if (!ShizukuManager.isAvailable()) {
                toast("未检测到 Shizuku，请先安装并启动 Shizuku")
                openShizukuInstallPage()
            } else if (!ShizukuManager.isGranted()) {
                ShizukuManager.requestPermission(this)
            } else {
                toast("Shizuku 已授权")
            }
        })

        root.addView(btn("通过 Shizuku 自动启用无障碍") {
            confirmShizukuEnable()
        })

        root.addView(divider())
        root.addView(sectionLabel("处理模式"))
        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 8)
        }
        rbPunctuation = CheckBox(this).apply {
            text = "标点触发 (推荐)"
            isChecked = config.processingMode == CatConfig.MODE_PUNCTUATION
            setOnCheckedChangeListener { _, checked -> if (checked) rbRealtime.isChecked = false }
        }
        rbRealtime = CheckBox(this).apply {
            text = "实时处理"
            isChecked = config.processingMode == CatConfig.MODE_REALTIME
            setOnCheckedChangeListener { _, checked -> if (checked) rbPunctuation.isChecked = false }
        }
        modeRow.addView(rbPunctuation)
        modeRow.addView(rbRealtime)
        root.addView(modeRow)
        root.addView(hint("标点触发：打字到标点处才处理\n实时处理：每输入一个字立即处理"))

        root.addView(divider())
        root.addView(sectionLabel("功能开关"))
        cbMeow = addCheckbox(root, "断句加哦齁齁齁♥", "每句末尾加哦齁齁齁♥", config.enableMeow)
        cbWo = addCheckbox(root, "我 -> 我..我我", "替换所有'我'", config.enableWoToBenmiao)
        cbNi = addCheckbox(root, "你 -> 主..主人♥", "替换所有'你'", config.enableNiToZhuren)
        cbEmoticon = addCheckbox(root, "随机颜文字", "末尾添加随机猫咪颜文字", config.enableRandomEmoticon)

        root.addView(divider())
        root.addView(sectionLabel("自定义颜文字"))
        root.addView(hint("每行一个，留空使用内置库"))
        etCustom = EditText(this).apply {
            setHint("例如: (=^w^=)")
            setLines(4)
            minLines = 4
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            config.customEmoticons.joinToString("\n").let { if (it.isNotEmpty()) setText(it) }
        }
        root.addView(etCustom, matchParams())

        root.addView(divider())
        root.addView(btn("保存设置") { saveConfig() })
        root.addView(btn("测试当前配置") { showTestDialog() })
        root.addView(hint("修改后请点击保存，服务下次触发时自动加载"))

        // 必须返回 scroll（root 已挂载为 scroll 的子视图），否则 setContentView 会因已有父布局崩溃
        return scroll
    }

    private fun addCheckbox(parent: LinearLayout, title: String, desc: String, checked: Boolean): CheckBox {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 8)
        }
        val cb = CheckBox(this).apply { isChecked = checked }
        row.addView(cb)
        // 注意：避免命名 text，以免遮蔽 TextView.text 属性
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 0, 0, 0) }
        val t1 = TextView(this).apply { text = title; textSize = 15f; setTypeface(null, Typeface.BOLD) }
        val t2 = TextView(this).apply { text = desc; textSize = 12f; setTextColor(Color.GRAY) }
        column.addView(t1); column.addView(t2)
        row.addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        parent.addView(row)
        return cb
    }

    private fun title(s: String) = TextView(this).apply {
        text = s; textSize = 22f; gravity = Gravity.CENTER; setPadding(0, 0, 0, 16)
        setTextColor(Color.rgb(230, 81, 0)); setTypeface(null, Typeface.BOLD)
    }
    private fun sectionLabel(s: String) = TextView(this).apply {
        text = s; textSize = 17f; setPadding(0, 12, 0, 4)
        setTextColor(Color.rgb(93, 64, 55)); setTypeface(null, Typeface.BOLD)
    }
    private fun infoText() = TextView(this).apply {
        textSize = 14f
        setTextColor(Color.rgb(40, 40, 40))          // 深色文字，避免在浅灰底上发虚
        setTypeface(null, Typeface.NORMAL)
        setPadding(12, 10, 12, 10)
        setBackgroundColor(Color.rgb(233, 233, 233))
    }
    private fun hint(s: String) = TextView(this).apply {
        text = s; textSize = 12f; setTextColor(Color.rgb(161, 127, 111))
        setPadding(0, 6, 0, 6)
    }
    private fun divider() = View(this).apply {
        setBackgroundColor(Color.rgb(221, 221, 221))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2).apply {
            setMargins(0, 24, 0, 8)
        }
    }
    private fun btn(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(255, 111, 0))
        setOnClickListener { onClick() }
    }
    private fun matchParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    // ---------- 行为 ----------
    private fun updateServiceStatus() {
        statusText.text = if (isAccessibilityServiceEnabled())
            "无障碍服务：已开启" else "无障碍服务：未开启"
    }

    private fun updateShizukuStatus() {
        shizukuStatusText.text = when {
            !ShizukuManager.isAvailable() -> "Shizuku：未运行"
            !ShizukuManager.isGranted() -> "Shizuku：运行中，未授权"
            else -> "Shizuku：运行中，已授权"
        }
    }

    private fun updateOverlayButton() {
        val has = Settings.canDrawOverlays(this)
        btnToggleOverlay.text =
            if (has) "日志悬浮窗：可启停" else "日志悬浮窗：未授权，点击授权"
    }

    private fun toggleOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }
        OverlayHelper.ensureOverlayLog(this)
        toast("已启动日志悬浮窗")
    }

    private fun confirmShizukuEnable() {
        if (!ShizukuManager.isGranted()) {
            toast("请先授予 Shizuku 权限")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("确认")
            .setMessage("将通过 Shizuku 自动开启本应用的无障碍服务。\n无障碍权限很敏感，请确认你信任本应用。")
            .setPositiveButton("确认开启") { _, _ ->
                toast("正在通过 Shizuku 启用…")
                ShizukuManager.enableAccessibilityService(this) { result ->
                    runOnUiThread {
                        toast("结果: $result")
                        updateServiceStatus()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val list = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            list.any { it.resolveInfo?.serviceInfo?.packageName == packageName }
        } catch (t: Throwable) {
            false
        }
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (t: Throwable) {
            toast("无法打开设置")
        }
    }

    private fun openShizukuInstallPage() {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=moe.shizuku.privileged.api"))
            )
        } catch (_: Throwable) {}
    }

    private fun saveConfig() {
        try {
            config.enableMeow = cbMeow.isChecked
            config.enableWoToBenmiao = cbWo.isChecked
            config.enableNiToZhuren = cbNi.isChecked
            config.enableRandomEmoticon = cbEmoticon.isChecked
            config.processingMode =
                if (rbRealtime.isChecked) CatConfig.MODE_REALTIME else CatConfig.MODE_PUNCTUATION
            config.customEmoticons = etCustom.text.toString()
                .split("\n").map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray()
            config.save(this)
            toast("设置已保存")
            AppLog.log("设置已保存，mode=${config.processingMode}")
        } catch (t: Throwable) {
            toast("保存失败: ${t.message}")
        }
    }

    private fun showTestDialog() {
        val original = "今天我很好，你准备好了吗？我们去公园玩吧！"
        val processed = try {
            TextProcessor.process(original, CatConfig(
                enableMeow = cbMeow.isChecked,
                enableWoToBenmiao = cbWo.isChecked,
                enableNiToZhuren = cbNi.isChecked,
                enableRandomEmoticon = false,
                processingMode = config.processingMode,
                customEmoticons = config.customEmoticons
            ))
        } catch (t: Throwable) {
            "测试失败: ${t.message}"
        }
        AlertDialog.Builder(this)
            .setTitle("预览")
            .setMessage("原始：$original\n\n处理后：$processed")
            .setPositiveButton("好", null)
            .show()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // Shizuku 授权结果回调
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == ShizukuManager.REQUEST_CODE) {
            updateShizukuStatus()
        }
    }
}
