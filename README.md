# OpenOhoho

通过**无障碍服务**实时把你输入的普通文字改写为"哦齁齁齁♥"风格的装饰文本，可自定义替换规则、断句文字与颜文字。UI 基于 **Jetpack Compose + Material 3**（浅色/深色 + Android 12+ 动态取色）。

> 本项目基于原「QQ哦齁齁齁♥助手」（反编译的二次修改版）重写，并在设计上回归、扩展了原作者通用化的思路。

## 出处与致谢

本项目承蒙以下项目启发并参考，特此致谢：

- **原作者：QiCaiJie114514 · [QQMiaoAssistant](https://github.com/QiCaiJie114514/QQMiaoAssistant)** —— 原「QQ猫语助手」开源实现（Java，AGPL-3.0），提供了可配置替换规则、断句文字、颜文字与处理模式的核心设计思路。
- 本仓库是在其基础之上进行的 **Kotlin + Jetpack Compose 重构与功能扩展**（Shizuku、黑白名单、规则集、微信手动输入悬浮窗等）。

> 双方均为 **AGPL-3.0** 许可，使用与二次开发请遵守相应协议并保留出处。

## 功能一览

| 功能 | 说明 |
|---|---|
| 断句添加 | 在句号/叹号等分句处追加文字（默认可自定义为"哦齁齁齁♥"、"喵呜"等） |
| 自定义替换规则 | 任意 `原词=替换词`（如 `我=本喵`、`你=主人`），支持在线/本地规则集一键切换 |
| 随机颜文字 | 打字过程中稳定追加一个猫咪颜文字（内置 53 个，支持自定义） |
| 处理模式 | `实时处理` / `标点触发` |
| 黑白名单 | 读取已安装应用列表，可视化勾选"只改哪些/不改哪些"应用 |
| 在线规则集 | 从 GitHub 仓库 `rules/*.toml` 一键拉取并应用 |
| 本地规则集 | 把当前规则保存为设备上的 `.toml`，持久化，随时切换 |
| 悬浮窗日志 | 实时显示当前输入内容与写入结果，可拖动、可一键清空、可关闭 |
| 微信输入悬浮窗 | 被无障碍屏蔽的应用（如微信）的绕开方案：输入→处理→复制剪贴板→手动粘贴 |
| Shizuku | 授权后一键自动启用无障碍服务（经 UserService 以 shell 身份执行） |

> ⚠️ 安全提醒
> - 无障碍权限 & Shizuku 属于**敏感能力**，本工具仅在本地改写文本、本地显示日志，**不采集 / 不上传任何聊天数据**（在线规则集仅拉取公开规则文件）。
> - 请仅在你能信任的环境中安装使用。

---

## 环境要求

- Android Studio（Hedgehog 或更新）+ JDK 17
- Android SDK（compileSdk 34 / targetSdk 34）
- 手机 Android 7.0+ (minSdk 24)

## 构建

```bash
gradle wrapper
./gradlew assembleDebug          # 调试包
./gradlew assembleRelease        # release（CI 中用 GitHub Secrets 签名）
```

- APK 输出在 `app/build/outputs/apk/{debug,release}/`
- GitHub Actions（`.github/workflows/build.yml`）会自动构建并上传签名 release APK 到 **Artifacts**
- 签名 keystore 通过 GitHub Secrets 提供（`KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`）

## 使用

1. 安装并打开 App → **服务页**：授予悬浮窗权限、授权 Shizuku（可选）、一键开启无障碍。
2. **设置页**：配置处理模式、断句文字、功能开关、自定义替换规则，以及**目标应用黑白名单**（勾选应用）。
3. **规则页**：从 GitHub 拉取在线规则集，或把当前规则保存为本地 `.toml`。
4. 打开 QQ / 其它无障碍可读的应用，打字即被改写；**微信**用"微信输入悬浮窗"手动处理+粘贴。

## 目录结构

```
app/src/main/
├── AndroidManifest.xml
├── kotlin/com/open/ohohoho/
│   ├── MainActivity.kt              # Compose 入口（ComponentActivity + edge-to-edge）
│   ├── ui/
│   │   ├── theme/Theme.kt           # AppTheme（浅/深色 + Dynamic Color + M3 色板）
│   │   ├── MainViewModel.kt         # 状态管理（StateFlow + sealed class）
│   │   └── OpenOhohoScreen.kt       # 主界面（Scaffold + 底部三页导航 + 组件）
│   ├── QQAccessibilityService.kt    # 无障碍服务（文字转换、黑白名单过滤、写回守卫）
│   ├── TextProcessor.kt             # 文字转换核心（断句/替换/颜文字/逆向还原）
│   ├── CatConfig.kt                 # 配置模型 / 内置颜文字
│   ├── shizuku/ShizukuManager.kt    # Shizuku 集成
│   ├── overlay/
│   │   ├── OverlayLogService.kt     # 悬浮窗日志（可拖动/清空/关闭）
│   │   ├── OverlayInputService.kt   # 微信输入悬浮窗（输入→处理→复制）
│   │   └── OverlayHelper.kt
│   └── util/
│       ├── AppLog.kt                # 日志总线
│       ├── RuleManager.kt           # 在线规则集拉取 + .toml 解析
│       └── LocalRuleManager.kt      # 本地规则集持久化
└── res/
    ├── xml/accessibility_service_config.xml
    ├── values/{strings,themes}.xml
    ├── mipmap-*/                    # 应用图标
    └── drawable/...

rules/                              # 在线规则集（.toml），App 内一键拉取
```

## 规则集格式（`rules/*.toml`）

```toml
name = "本喵说话"
meow = "喵呜"          # 断句末尾文字（可省）

"我" = "本喵"
"你" = "主人"
"呢" = "喵"
```

## 许可

本项目基于 **GNU Affero General Public License v3.0 (AGPL-3.0)** 开源，详见 [LICENSE](LICENSE)。

使用本项目请遵守腾讯 QQ / 微信用户协议与当地法律法规，勿用于恶意用途。
