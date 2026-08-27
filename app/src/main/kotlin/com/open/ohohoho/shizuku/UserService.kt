package com.open.ohohoho.shizuku

import android.content.Context
import androidx.annotation.Keep
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Shizuku UserService：
 * 由 Shizuku 服务端在独立进程（root/shell 身份）中通过反射创建，
 * 用于执行需要系统权限的 shell 命令（如 `settings put secure ...`）。
 *
 * 注意：
 *  - 需要公开的无参构造（Shizuku 反射要求），Context 构造为 v13 可选。
 *  - @Keep 防止混淆时被删掉。
 */
@Keep
class UserService : IUserService.Stub() {

    /** 必需的无参构造。 */
    constructor() : super()

    /** v13+ 可选：带 Context 的构造。 */
    @Keep
    constructor(context: Context) : this()

    /** 以 root/shell 身份执行 shell 命令。 */
    override fun exec(command: String): String {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val out = readAll(p.inputStream)
            val err = readAll(p.errorStream)
            val code = p.waitFor()
            "exit=$code\n$out\n$err".trim()
        } catch (t: Throwable) {
            "err=${t.message}"
        }
    }

    override fun exit() = destroy()

    override fun destroy() {
        System.exit(0)
    }

    private fun readAll(stream: java.io.InputStream): String {
        val sb = StringBuilder()
        try {
            BufferedReader(InputStreamReader(stream)).use { br ->
                br.forEachLine { if (sb.length < 4000) sb.appendLine(it) }
            }
        } catch (_: Throwable) {}
        return sb.toString().trim()
    }
}
