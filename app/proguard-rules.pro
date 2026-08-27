# ---- Shizuku 库保留 ----
-keep class moe.shizuku.** { *; }
-keep class rikka.shizuku.** { *; }

# ---- Shizuku UserService：由 Shizuku 服务端通过反射创建，需保留类与无参构造 ----
-keep class com.open.ohohoho.shizuku.UserService { *; }
-keep class com.open.ohohoho.shizuku.IUserService { *; }

# ---- 无障碍服务：清单/系统绑定引用 ----
-keep class com.open.ohohoho.QQAccessibilityService { *; }

# ---- 悬浮窗日志服务 ----
-keep class com.open.ohohoho.overlay.OverlayLogService { *; }
-keep class com.open.ohohoho.overlay.OverlayHelper { *; }

# ---- AIDL Stub ----
-keep class com.open.ohohoho.shizuku.IUserService$* { *; }
