package com.open.ohohoho.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import com.open.ohohoho.CatConfig
import com.open.ohohoho.TextProcessor
import com.open.ohohoho.overlay.OverlayHelper
import com.open.ohohoho.overlay.OverlayInputService
import com.open.ohohoho.shizuku.ShizukuManager
import com.open.ohohoho.util.LocalRuleManager
import com.open.ohohoho.util.RuleManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

/** 在线规则集拉取状态（sealed class：Loading / Success / Error）。 */
sealed interface OnlineRuleState {
    data object Idle : OnlineRuleState
    data object Loading : OnlineRuleState
    data class Success(val sets: List<RuleManager.RuleSet>) : OnlineRuleState
    data class Error(val msg: String) : OnlineRuleState
}

/** 已安装应用信息（用于黑白名单勾选）。 */
data class AppInfo(val label: String, val packageName: String)

/** 主界面 UI 状态。 */
data class MainUiState(
    val config: CatConfig = CatConfig(),
    val rulesText: String = "",
    val meowText: String = "",
    val emoticonsText: String = "",
    val whitelistMode: Boolean = true,
    val packagesText: String = "",
    val installedApps: List<AppInfo> = emptyList(),
    val showAppPicker: Boolean = false,
    val appSearchQuery: String = "",
    val accessibilityEnabled: Boolean = false,
    val shizukuAvailable: Boolean = false,
    val shizukuGranted: Boolean = false,
    val overlayRunning: Boolean = false,
    val inputOverlayRunning: Boolean = false,
    val autoEnable: Boolean = false,
    val onlineState: OnlineRuleState = OnlineRuleState.Idle,
    val onlineSelected: Int = 0,
    val localRuleSets: List<String> = emptyList(),
    val localSelected: Int = 0,
    val ruleSetName: String = "",
    val showTestDialog: Boolean = false,
    val testResult: String = "",
    val showConfirmEnable: Boolean = false,
)

/**
 * 主界面 ViewModel：负责所有业务动作，UI 层只做渲染。
 * 数据流保持不变，仅把原 MainActivity 的代码迁移到此。
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app
    private val _ui = MutableStateFlow(MainUiState())
    val ui: StateFlow<MainUiState> = _ui.asStateFlow()

    private var config: CatConfig = CatConfig.load(app)

    init {
        val auto = ctx.getSharedPreferences("auto_enable", Context.MODE_PRIVATE)
            .getBoolean("enabled", false)
        _ui.value = _ui.value.copy(
            config = config,
            rulesText = config.replacementRules.joinToString("\n") { "${it.first}=${it.second}" },
            meowText = config.meowText,
            emoticonsText = config.customEmoticons.joinToString("\n"),
            whitelistMode = config.isWhitelistMode,
            packagesText = config.managedPackages.joinToString("\n"),
            autoEnable = auto,
        )
        refreshStatus()
        refreshLocalRuleSets()
        if (auto) maybeAutoEnable()
    }

    // ---------- 状态辅助 ----------
    private fun refreshStatus() {
        _ui.value = _ui.value.copy(
            accessibilityEnabled = ShizukuManager.isAccessibilityServiceEnabled(ctx),
            shizukuAvailable = ShizukuManager.isAvailable(),
            shizukuGranted = ShizukuManager.isGranted(),
            overlayRunning = OverlayHelper.running,
            inputOverlayRunning = OverlayInputService.running,
        )
    }

    private fun push() {
        _ui.value = _ui.value.copy(config = config)
    }

    private fun toast(msg: String) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }

    private fun persist() {
        config.save(ctx)
        push()
    }

    // ---------- 配置修改 ----------
    // 注意：config 是 var，所有修改必须用 copy() 生成新对象再替换，
    // 否则原地改字段会使 StateFlow 的拷贝 equals 相等、不触发 UI 更新。
    fun toggleMeow(v: Boolean) { config = config.copy(enableMeow = v); persist() }
    fun toggleEmoticon(v: Boolean) { config = config.copy(enableRandomEmoticon = v); persist() }
    fun setMode(mode: String) { config = config.copy(processingMode = mode); persist() }

    fun updateRulesText(t: String) {
        _ui.value = _ui.value.copy(rulesText = t)
        config = config.copy(replacementRules = parseRules(t))
        persist()
    }

    fun updateEmoticonsText(t: String) {
        _ui.value = _ui.value.copy(emoticonsText = t)
        config = config.copy(
            customEmoticons = t.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray()
        )
        persist()
    }

    fun updateMeowText(t: String) {
        _ui.value = _ui.value.copy(meowText = t)
        config = config.copy(meowText = t)
        persist()
    }

    fun toggleMode(whitelist: Boolean) {
        config = config.copy(isWhitelistMode = whitelist)
        _ui.value = _ui.value.copy(whitelistMode = whitelist)
        persist()
    }

    fun updatePackagesText(t: String) {
        _ui.value = _ui.value.copy(packagesText = t)
        config = config.copy(
            managedPackages = t.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        )
        persist()
    }

    // ---------- 应用选择器（黑白名单可视化勾选） ----------
    fun openAppPicker() {
        val pm = ctx.packageManager
        val apps = try {
            pm.getInstalledApplications(0)
                .map { ai ->
                    val label = try { pm.getApplicationLabel(ai)?.toString() ?: ai.packageName }
                    catch (t: Throwable) { ai.packageName }
                    AppInfo(label, ai.packageName)
                }
                .sortedBy { it.label.lowercase() }
        } catch (t: Throwable) {
            emptyList()
        }
        _ui.value = _ui.value.copy(installedApps = apps, showAppPicker = true)
    }

    fun closeAppPicker() {
        _ui.value = _ui.value.copy(showAppPicker = false, appSearchQuery = "")
    }

    fun updateAppSearchQuery(q: String) {
        _ui.value = _ui.value.copy(appSearchQuery = q)
    }

    fun toggleAppPackage(pkg: String) {
        val cur = config.managedPackages.toMutableList()
        if (pkg in cur) cur.remove(pkg) else cur.add(pkg)
        config = config.copy(managedPackages = cur)
        persist()
    }

    fun clearPackages() {
        config = config.copy(managedPackages = emptyList())
        persist()
    }

    fun saveConfig() {
        persist()
        toast("设置已保存")
    }

    // ---------- 服务状态动作 ----------
    fun toggleAutoEnable(v: Boolean) {
        ctx.getSharedPreferences("auto_enable", Context.MODE_PRIVATE)
            .edit().putBoolean("enabled", v).apply()
        _ui.value = _ui.value.copy(autoEnable = v)
        if (v) maybeAutoEnable()
    }

    fun toggleOverlay() {
        if (!Settings.canDrawOverlays(ctx)) {
            try {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Throwable) {}
            return
        }
        if (OverlayHelper.running) {
            OverlayHelper.stopOverlayLog(ctx)
            toast("已关闭日志悬浮窗")
        } else {
            OverlayHelper.ensureOverlayLog(ctx)
            toast("已启动日志悬浮窗")
        }
        refreshStatus()
    }

    /** 启停"微信输入处理悬浮窗"（手动输入→处理→复制到剪贴板）。 */
    fun toggleInputOverlay() {
        if (!Settings.canDrawOverlays(ctx)) {
            try {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Throwable) {}
            return
        }
        if (OverlayInputService.running) {
            ctx.stopService(Intent(ctx, OverlayInputService::class.java))
            toast("已关闭输入悬浮窗")
        } else {
            val intent = Intent(ctx, OverlayInputService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(intent) else ctx.startService(intent)
            toast("已启动输入悬浮窗")
        }
        refreshStatus()
    }

    fun openAccessibilitySettings() {
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Throwable) {
            toast("无法打开设置")
        }
    }

    fun grantShizuku() {
        if (!ShizukuManager.isAvailable()) {
            toast("未检测到 Shizuku，请先安装并启动")
            try {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=moe.shizuku.privileged.api"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Throwable) {}
            return
        }
        if (ShizukuManager.isGranted()) { toast("Shizuku 已授权"); return }
        try {
            Shizuku.addRequestPermissionResultListener { _, _ -> refreshStatus() }
            Shizuku.requestPermission(ShizukuManager.REQUEST_CODE)
        } catch (t: Throwable) {
            toast("申请失败: ${t.message}")
        }
    }

    fun setConfirmEnable(v: Boolean) { _ui.value = _ui.value.copy(showConfirmEnable = v) }

    fun enableAccessibilityViaShizuku() {
        _ui.value = _ui.value.copy(showConfirmEnable = false)
        if (!ShizukuManager.isGranted()) { toast("请先授予 Shizuku 权限"); return }
        toast("正在通过 Shizuku 启用…")
        ShizukuManager.enableAccessibilityService(ctx) { result ->
            toast("结果: $result")
            refreshStatus()
        }
    }

    private fun maybeAutoEnable() {
        if (!ShizukuManager.isGranted()) return
        if (ShizukuManager.isAccessibilityServiceEnabled(ctx)) return
        ShizukuManager.ensureAccessibilityService(ctx) { ok ->
            toast(if (ok) "已自动开启无障碍服务" else "自动开启失败，请手动开启")
            refreshStatus()
        }
    }

    // ---------- 在线规则集 ----------
    fun refreshOnline() {
        _ui.value = _ui.value.copy(onlineState = OnlineRuleState.Loading)
        RuleManager.fetchRuleSets { list ->
            _ui.value = _ui.value.copy(
                onlineState = if (list.isEmpty()) OnlineRuleState.Error("未拉取到规则集")
                              else OnlineRuleState.Success(list)
            )
            toast("拉到 ${list.size} 个规则集")
        }
    }

    fun selectOnline(idx: Int) { _ui.value = _ui.value.copy(onlineSelected = idx) }

    fun applyOnline() {
        val sets = (_ui.value.onlineState as? OnlineRuleState.Success)?.sets ?: run {
            toast("请先「刷新」并选择规则集"); return
        }
        val idx = _ui.value.onlineSelected
        if (idx < 0 || idx >= sets.size) { toast("请先「刷新」并选择规则集"); return }
        val rs = sets[idx]
        toast("正在应用 ${rs.name}…")
        RuleManager.fetchRule(rs) { content ->
            if (content.rules.isEmpty()) { toast("解析失败或规则为空"); return@fetchRule }
            _ui.value = _ui.value.copy(
                rulesText = content.rules.joinToString("\n") { "${it.first}=${it.second}" },
                meowText = content.meowText ?: config.meowText,
            )
            config = config.copy(
                replacementRules = content.rules,
                meowText = content.meowText ?: config.meowText,
            )
            persist()
            toast("已应用规则集${if (content.name != null) "：${content.name}" else ""}（${content.rules.size} 条规则）")
        }
    }

    // ---------- 本地规则集 ----------
    fun refreshLocalRuleSets() {
        _ui.value = _ui.value.copy(localRuleSets = LocalRuleManager.list(ctx))
    }

    fun selectLocal(idx: Int) { _ui.value = _ui.value.copy(localSelected = idx) }

    fun updateRuleSetName(t: String) { _ui.value = _ui.value.copy(ruleSetName = t) }

    fun saveLocal() {
        if (config.replacementRules.isEmpty()) { toast("当前规则为空，无法保存"); return }
        val ok = LocalRuleManager.save(ctx, _ui.value.ruleSetName, config.replacementRules, config.meowText)
        toast(if (ok) "已保存到本地规则集" else "保存失败")
        refreshLocalRuleSets()
    }

    fun loadLocal() {
        val idx = _ui.value.localSelected
        val names = _ui.value.localRuleSets
        if (idx < 0 || idx >= names.size) { toast("请先「刷新」并选择本地规则集"); return }
        val content = LocalRuleManager.load(ctx, names[idx])
        if (content.rules.isEmpty()) { toast("规则为空或解析失败"); return }
        _ui.value = _ui.value.copy(
            rulesText = content.rules.joinToString("\n") { "${it.first}=${it.second}" },
            meowText = content.meowText ?: config.meowText,
        )
        config = config.copy(
            replacementRules = content.rules,
            meowText = content.meowText ?: config.meowText,
        )
        persist()
        toast("已加载${if (content.name != null) "：${content.name}" else ""}（${content.rules.size} 条规则）")
    }

    fun deleteLocal() {
        val idx = _ui.value.localSelected
        val names = _ui.value.localRuleSets
        if (idx < 0 || idx >= names.size) { toast("请先「刷新」并选择本地规则集"); return }
        LocalRuleManager.delete(ctx, names[idx])
        toast("已删除 ${names[idx]}")
        refreshLocalRuleSets()
    }

    // ---------- 测试 ----------
    fun testConfig() {
        // 保留当前配置（含随机颜文字开关），预览能直接看到颜文字是否触发
        val result = TextProcessor.process(
            "今天我很好，你准备好了吗？我们去公园玩吧！",
            config
        )
        _ui.value = _ui.value.copy(testResult = result, showTestDialog = true)
    }

    fun dismissTest() { _ui.value = _ui.value.copy(showTestDialog = false) }

    companion object {
        /** 把"原=替换"文本解析成规则列表。 */
        fun parseRules(text: String): List<Pair<String, String>> =
            text.split("\n").mapNotNull { line ->
                val t = line.trim()
                if (t.isEmpty()) return@mapNotNull null
                val idx = t.indexOf('=')
                if (idx <= 0) return@mapNotNull null
                val from = t.substring(0, idx).trim()
                val to = t.substring(idx + 1).trim()
                if (from.isEmpty() || to.isEmpty()) null else from to to
            }
    }
}
