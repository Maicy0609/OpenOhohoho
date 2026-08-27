package com.open.ohohoho.shizuku

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.open.ohohoho.QQAccessibilityService
import com.open.ohohoho.util.AppLog
import rikka.shizuku.Shizuku

/**
 * Shizuku 集成：
 *  - 状态检测（是否运行 / 是否已授权）
 *  - 申请 API_V23 权限
 *  - 通过 Shizuku UserService 在 root/shell 身份下执行命令，
 *    用于"自动启用无障碍服务"，省去手动去设置里开关。
 *
 * 说明：
 *  - 本模块优雅降级：未安装/未授权 Shizuku 时仅报告状态，不影响其它功能。
 *  - 自动启用无障碍属敏感操作，主界面需弹窗确认后再调用。
 */
object ShizukuManager {

    const val REQUEST_CODE = 10001

    private const val APP_ID = "com.open.ohohoho"

    private var service: IUserService? = null
    private var connected = false
    private var pendingCommand: String? = null
    private var pendingCallback: ((String) -> Unit)? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IUserService.Stub.asInterface(binder)
            connected = true
            AppLog.log("Shizuku 用户服务已连接")
            val cmd = pendingCommand
            val cb = pendingCallback
            if (cmd != null && cb != null) {
                pendingCommand = null
                pendingCallback = null
                try {
                    cb(service?.exec(cmd) ?: "service null")
                } catch (t: Throwable) {
                    cb("err=${t.message}")
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            connected = false
            service = null
        }
    }

    private val userServiceArgs: Shizuku.UserServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(APP_ID, UserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("service")
    }

    /** Shizuku 是否在运行（binder 存在）。 */
    fun isAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    /** 是否已授予 API_V23 权限。 */
    fun isGranted(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    fun requestPermission(activity: Activity): Boolean {
        return try {
            Shizuku.requestPermission(REQUEST_CODE)
            true
        } catch (t: Throwable) {
            AppLog.log("Shizuku 申请权限失败: ${t.message}")
            false
        }
    }

    /** 绑定 UserService（仅在已授权时）。 */
    fun bind() {
        if (connected || !isGranted()) return
        try {
            Shizuku.bindUserService(userServiceArgs, connection)
        } catch (t: Throwable) {
            AppLog.log("Shizuku bind 失败: ${t.message}")
        }
    }

    /**
     * 在 Shizuku 身份下执行 shell 命令。
     * 若尚未连接 UserService，会先绑定，连接建立后自动执行并通过 callback 返回。
     */
    fun exec(command: String, callback: (String) -> Unit) {
        if (!isGranted()) {
            callback("Shizuku 未授权")
            return
        }
        val svc = service
        if (connected && svc != null) {
            try {
                callback(svc.exec(command))
            } catch (t: Throwable) {
                callback("err=${t.message}")
            }
            return
        }
        pendingCommand = command
        pendingCallback = callback
        bind()
    }

    /**
     * 自动启用本应用的无障碍服务（写入 secure 设置，以 Shizuku 身份执行）。
     * 调用前应弹窗确认。
     */
    fun enableAccessibilityService(context: Context, callback: (String) -> Unit) {
        val cn = ComponentName(context, QQAccessibilityService::class.java)
        val value = "${context.packageName}/${cn.flattenToShortString()}"
        val cmd =
            "settings put secure enabled_accessibility_services \"$value\"; " +
                "settings put secure accessibility_enabled 1"
        exec(cmd) { result ->
            AppLog.log("Shizuku 启用无障碍: $result")
            callback(result)
        }
    }
}
