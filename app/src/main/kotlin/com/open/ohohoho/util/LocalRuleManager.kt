package com.open.ohohoho.util

import android.content.Context
import java.io.File

/**
 * 本地规则集管理：把规则集以 .toml 文件形式持久化到应用内部存储，
 * 支持保存 / 列出 / 加载 / 删除。
 */
object LocalRuleManager {

    private fun dir(context: Context): File =
        File(context.filesDir, "rules").apply { if (!exists()) mkdirs() }

    /** 列出本地已有 .toml 规则集文件名。 */
    fun list(context: Context): List<String> =
        dir(context).listFiles()?.filter { it.name.endsWith(".toml") }
            ?.map { it.name }?.sorted() ?: emptyList()

    /** 把规则保存为本地 .toml 文件。 */
    fun save(context: Context, name: String, rules: List<Pair<String, String>>): Boolean {
        val base = name.trim().ifEmpty { "rule_${System.currentTimeMillis()}" }
        val fileName = if (base.endsWith(".toml")) base else "$base.toml"
        return try {
            val sb = StringBuilder()
            sb.append("# OpenOhoho 本地规则集\n")
            sb.append("name = \"${base.removeSuffix(".toml")}\"\n\n")
            for ((from, to) in rules) {
                sb.append("\"$from\" = \"$to\"\n")
            }
            File(dir(context), fileName).writeText(sb.toString())
            true
        } catch (t: Throwable) {
            false
        }
    }

    /** 加载本地 .toml 规则集，返回 (名称, 替换规则)。 */
    fun load(context: Context, fileName: String): Pair<String?, List<Pair<String, String>>> {
        return try {
            RuleManager.parseToml(File(dir(context), fileName).readText())
        } catch (t: Throwable) {
            null to emptyList()
        }
    }

    fun delete(context: Context, fileName: String) {
        try {
            File(dir(context), fileName).delete()
        } catch (t: Throwable) {}
    }
}
