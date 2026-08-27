# OpenOhoho

基于原「QQ哦齁齁齁♥助手」重写的开源 OpenOhoho：通过**无障碍服务**实时把你在 QQ 里输入的文字改写为"哦齁齁齁♥"装饰文本，并新增：

- ✅ **Shizuku 支持**：检测 / 申请 Shizuku 权限，可选一键自动启用无障碍服务（省去手动到设置里开关）
- ✅ **悬浮窗日志**：屏幕顶部实时显示日志，方便调试；**日志直接输出当前聊天输入内容**与转换结果（仅本机展示，不联网外传）

> ⚠️ 安全提醒
> - 无障碍权限 & Shizuku 属于**敏感能力**，本工具仅在本地改写文本、本地显示日志，**不采集 / 不上传任何聊天数据**。
> - 请仅在你能信任的环境中安装使用。

---

## 功能

| 功能 | 说明 |
|---|---|
| 断句加"哦齁齁齁♥" | 在句号/叹号等分句，每句末尾追加 |
| `我 → 我..我我` | 替换所有"我" |
| `你 → 主..主人♥` | 替换所有"你" |
| 随机颜文字 | 消息末尾追加内置/自定义猫咪颜文字 |
| 处理模式 | `标点触发`（推荐）/ `实时处理` |
| 自定义颜文字 | 每行一个，留空用内置库 |
| 悬浮窗日志 | 显示当前输入 / 写入结果，可拖动 |
| Shizuku | 状态查看、授权、一键启用无障碍（经 UserService 以 root/shell 身份执行） |

## 环境要求

- Android Studio（Hedgehog 或更新）+ JDK 17
- Android SDK（compileSdk 34 / targetSdk 34）
- 手机 Android 7.0+ (API 23+)

## 构建

1. 用 Android Studio 打开本目录（会自动补全 gradle wrapper）。
2. 首次如需命令行构建：
   ```bash
   gradle wrapper
   ./gradlew assembleDebug
   ```
3. APK 输出在 `app/build/outputs/apk/debug/`。

## 使用

1. 安装并打开 App，点击 **授予 Shizuku 权限**（需已安装并启动 Shizuku）。
2. 点击 **通过 Shizuku 自动启用无障碍**（或去系统设置手动开启无障碍服务，选中"OpenOhoho 无障碍服务"）。
3. 授予 **悬浮窗** 权限，点击 **启动日志悬浮窗**。
4. 打开 QQ，开始打字即可看到文字被"喵化"，顶部悬浮窗会实时显示日志。

## 目录结构

```
app/src/main/
├── AndroidManifest.xml
├── kotlin/com/open/ohohoho/
│   ├── MainActivity.kt          # 控制面板
│   ├── QQAccessibilityService.kt# 无障碍服务（文字转换 + 日志）
│   ├── TextProcessor.kt         # 文字转换核心
│   ├── CatConfig.kt             # 配置 / 内置颜文字
│   ├── shizuku/ShizukuManager.kt# Shizuku 集成
│   ├── overlay/OverlayLogService.kt # 悬浮窗日志
│   ├── overlay/OverlayHelper.kt     # 悬浮窗启停
│   └── util/AppLog.kt               # 日志总线
└── res/
    ├── xml/accessibility_service_config.xml
    ├── values/{strings,themes}.xml
    └── drawable/...
```

## 许可

仅供学习交流。请遵守腾讯 QQ 用户协议与当地法律法规，勿用于恶意用途。
