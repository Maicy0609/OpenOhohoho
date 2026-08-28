package com.open.ohohoho.util

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * 在线规则集管理：从 GitHub 仓库 `rules/` 目录拉取 .toml 规则集并解析。
 * 用于"一键拉取最新规则 + 一键切换规则集"。
 */
object RuleManager {

    private const val REPO = "Maicy0609/OpenOhohoho"
    private const val BRANCH = "main"
    private const val LIST_URL = "https://api.github.com/repos/$REPO/contents/rules"
    private val MAIN = Handler(Looper.getMainLooper())

    data class RuleSet(val name: String, val contentUrl: String)

    /** 拉取仓库 rules/ 目录下的 .toml 规则集列表。 */
    fun fetchRuleSets(callback: (List<RuleSet>) -> Unit) {
        Thread {
            val list = try {
                val conn = URL(LIST_URL).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                val json = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val arr = JSONArray(json)
                val result = mutableListOf<RuleSet>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val name = o.getString("name")
                    if (name.endsWith(".toml")) {
                        val url = o.optString("download_url").ifEmpty {
                            "https://raw.githubusercontent.com/$REPO/$BRANCH/rules/$name"
                        }
                        result.add(RuleSet(name, url))
                    }
                }
                result
            } catch (t: Throwable) {
                emptyList()
            }
            MAIN.post { callback(list) }
        }.start()
    }

    /** 下载并解析单个 .toml 规则集，返回 (名称, 替换规则列表)。 */
    fun fetchRule(rs: RuleSet, callback: (Pair<String?, List<Pair<String, String>>>) -> Unit) {
        Thread {
            val result = try {
                val conn = URL(rs.contentUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                val text = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                parseToml(text)
            } catch (t: Throwable) {
                null to emptyList()
            }
            MAIN.post { callback(result) }
        }.start()
    }

    /**
     * 解析简单 TOML：
     *  - `name = "xx"`  -> 规则集名
     *  - `"key" = "value"` 或 `key = "value"` -> 替换规则
     */
    fun parseToml(text: String): Pair<String?, List<Pair<String, String>>> {
        var name: String? = null
        val rules = mutableListOf<Pair<String, String>>()
        for (line in text.lines()) {
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#") || t.startsWith("[")) continue

            if (t.startsWith("name")) {
                Regex("^name\\s*=\\s*\"(.*?)\"$").find(t)?.let { name = it.groupValues[1] }
                continue
            }
            val m = Regex("^\"(.*?)\"\\s*=\\s*\"(.*?)\"$").find(t)
                ?: Regex("^([^=]+?)\\s*=\\s*\"(.*?)\"$").find(t)
            if (m != null) {
                val from = m.groupValues[1].trim()
                val to = m.groupValues[2].trim()
                if (from.isNotEmpty() && to.isNotEmpty()) rules.add(from to to)
            }
        }
        return name to rules
    }
}
