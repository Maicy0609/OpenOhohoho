package com.open.ohohoho.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.open.ohohoho.CatConfig

/**
 * 主界面：M3 Scaffold + 各分区 Card。
 * 所有状态来自 [MainViewModel]，UI 层只负责渲染与回调。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenOhohoScreen(viewModel: MainViewModel) {
    val state by viewModel.ui.collectAsState()

    // Android 13+ 通知权限（前台服务通知）
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT >= 33) {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("OpenOhoho", style = MaterialTheme.typography.headlineSmall) },
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ServiceStatusCard(state, viewModel)
            ProcessingModeCard(state, viewModel)
            FeatureCard(state, viewModel)
            EmoticonsCard(state, viewModel)
            RulesCard(state, viewModel)
            OnlineRulesCard(state, viewModel)
            LocalRulesCard(state, viewModel)

            Row(Modifier.fillMaxWidth()) {
                Button(onClick = { viewModel.saveConfig() }, Modifier.weight(1f)) {
                    Text("保存设置")
                }
                Spacer(Modifier.width(12.dp))
                FilledTonalButton(onClick = { viewModel.testConfig() }, Modifier.weight(1f)) {
                    Text("测试当前配置")
                }
            }
        }
    }

    if (state.showTestDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissTest() },
            title = { Text("预览") },
            text = {
                Text(
                    "原始：今天我很好，你准备好了吗？我们去公园玩吧！\n\n处理后：\n${state.testResult}"
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissTest() }) { Text("好") }
            },
        )
    }

    if (state.showConfirmEnable) {
        AlertDialog(
            onDismissRequest = { viewModel.setConfirmEnable(false) },
            title = { Text("确认") },
            text = {
                Text("将通过 Shizuku 自动开启本应用的无障碍服务。\n无障碍权限很敏感，请确认你信任本应用。")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.enableAccessibilityViaShizuku() }) { Text("确认开启") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setConfirmEnable(false) }) { Text("取消") }
            },
        )
    }
}

// ================= 服务状态 =================
@Composable
private fun ServiceStatusCard(state: MainUiState, vm: MainViewModel) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("服务状态", style = MaterialTheme.typography.titleMedium)
            StatusRow("无障碍服务", if (state.accessibilityEnabled) "已开启" else "未开启")
            StatusRow(
                "Shizuku",
                when {
                    !state.shizukuAvailable -> "未运行"
                    !state.shizukuGranted -> "运行中，未授权"
                    else -> "运行中，已授权"
                }
            )
            StatusRow("日志悬浮窗", if (state.overlayRunning) "运行中" else "未运行")

            OutlinedButton(onClick = { vm.openAccessibilitySettings() }, Modifier.fillMaxWidth()) {
                Text("开启无障碍服务")
            }
            FilledTonalButton(onClick = { vm.grantShizuku() }, Modifier.fillMaxWidth()) {
                Text("授予 Shizuku 权限")
            }
            OutlinedButton(onClick = { vm.setConfirmEnable(true) }, Modifier.fillMaxWidth()) {
                Text("通过 Shizuku 自动启用无障碍")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("启动时自动检测并开启无障碍", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = state.autoEnable, onCheckedChange = { vm.toggleAutoEnable(it) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("日志悬浮窗", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { vm.toggleOverlay() }) {
                    Text(if (state.overlayRunning) "关闭" else "启动")
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// ================= 处理模式 =================
@Composable
private fun ProcessingModeCard(state: MainUiState, vm: MainViewModel) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("处理模式", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.config.processingMode == CatConfig.MODE_REALTIME,
                    onClick = { vm.setMode(CatConfig.MODE_REALTIME) },
                    label = { Text("实时处理") },
                )
                FilterChip(
                    selected = state.config.processingMode == CatConfig.MODE_PUNCTUATION,
                    onClick = { vm.setMode(CatConfig.MODE_PUNCTUATION) },
                    label = { Text("标点触发") },
                )
            }
            Text(
                "实时：每输入一个字立即处理；标点：到标点处才处理",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ================= 功能开关 =================
@Composable
private fun FeatureCard(state: MainUiState, vm: MainViewModel) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("功能开关", style = MaterialTheme.typography.titleMedium)
            SwitchRow("断句加哦齁齁齁♥", state.config.enableMeow) { vm.toggleMeow(it) }
            SwitchRow("末尾随机颜文字", state.config.enableRandomEmoticon) { vm.toggleEmoticon(it) }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

// ================= 自定义颜文字 =================
@Composable
private fun EmoticonsCard(state: MainUiState, vm: MainViewModel) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("自定义颜文字", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.emoticonsText,
                onValueChange = { vm.updateEmoticonsText(it) },
                placeholder = { Text("每行一个，留空使用内置库") },
                modifier = Modifier.fillMaxWidth().height(110.dp),
            )
        }
    }
}

// ================= 自定义替换规则 =================
@Composable
private fun RulesCard(state: MainUiState, vm: MainViewModel) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("自定义替换规则", style = MaterialTheme.typography.titleMedium)
            Text(
                "每行一个，格式：原词=替换词，例如 我=本喵",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.rulesText,
                onValueChange = { vm.updateRulesText(it) },
                placeholder = { Text("我=本喵\n你=主人\n呢=喵") },
                modifier = Modifier.fillMaxWidth().height(130.dp),
            )
        }
    }
}

// ================= 在线规则集 =================
@Composable
private fun OnlineRulesCard(state: MainUiState, vm: MainViewModel) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("在线规则集", style = MaterialTheme.typography.titleMedium)
            Text(
                "从 GitHub 仓库 rules/ 目录拉取，一键切换",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val names = (state.onlineState as? OnlineRuleState.Success)?.sets?.map { it.name } ?: emptyList()
            RuleSetDropdown(
                label = "在线规则集",
                items = names,
                selectedIndex = state.onlineSelected,
                onSelect = { vm.selectOnline(it) },
                emptyText = if (state.onlineState is OnlineRuleState.Loading) "加载中…"
                           else if (state.onlineState is OnlineRuleState.Error) "拉取失败"
                           else "请刷新后选择",
            )
            OutlinedButton(onClick = { vm.refreshOnline() }, Modifier.fillMaxWidth()) {
                Text("刷新规则列表")
            }
            FilledTonalButton(onClick = { vm.applyOnline() }, Modifier.fillMaxWidth()) {
                Text("应用所选规则")
            }
        }
    }
}

// ================= 本地规则集 =================
@Composable
private fun LocalRulesCard(state: MainUiState, vm: MainViewModel) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("本地规则集(.toml)", style = MaterialTheme.typography.titleMedium)
            Text(
                "把当前规则保存为本地 .toml，持久化，可随时切换",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.ruleSetName,
                onValueChange = { vm.updateRuleSetName(it) },
                label = { Text("规则集名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            RuleSetDropdown(
                label = "本地规则集",
                items = state.localRuleSets,
                selectedIndex = state.localSelected,
                onSelect = { vm.selectLocal(it) },
                emptyText = "请刷新后选择",
            )
            OutlinedButton(onClick = { vm.saveLocal() }, Modifier.fillMaxWidth()) {
                Text("保存当前规则")
            }
            FilledTonalButton(onClick = { vm.loadLocal() }, Modifier.fillMaxWidth()) {
                Text("加载所选规则")
            }
            OutlinedButton(onClick = { vm.deleteLocal() }, Modifier.fillMaxWidth()) {
                Text("删除所选规则")
            }
            TextButton(onClick = { vm.refreshLocalRuleSets() }, Modifier.fillMaxWidth()) {
                Text("刷新本地列表")
            }
        }
    }
}

/** M3 下拉选择：Box + OutlinedButton + DropdownMenu。 */
@Composable
private fun RuleSetDropdown(
    label: String,
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    emptyText: String,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = items.getOrNull(selectedIndex) ?: emptyText
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, Modifier.fillMaxWidth()) {
            Text(selected, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text("▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (items.isEmpty()) {
                DropdownMenuItem(text = { Text("（空）") }, onClick = { expanded = false })
            } else {
                items.forEachIndexed { i, name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = { onSelect(i); expanded = false },
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun OpenOhohoScreenPreview() {
    com.open.ohohoho.ui.theme.AppTheme {
        Column(Modifier.padding(16.dp)) {
            Text("OpenOhoho 预览", style = MaterialTheme.typography.titleLarge)
            Text("浅色/深色 + Dynamic Color，组件见主界面。", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
