package com.open.ohohoho

import java.util.Random
import kotlin.math.max

/**
 * 文字转换核心：把普通输入转换成"哦齁齁齁♥"装饰文本。
 * 逻辑与原项目一致。
 */
object TextProcessor {

    private val RANDOM = Random()

    /** 分句用的标点/空白正则。 */
    private val SENTENCE_SPLIT_PATTERN = Regex("([，,。！!？?\\s]+)")

    /** 需要剥离的"自装饰"尾巴正则（连续 3 个以上符号/空白）。 */
    private val DECORATION_STRIP = Regex("\\s*[\\p{S}\\p{So}\\p{Sm}\\p{Sk}\\p{P}]{3,}\\s*")

    /**
     * 主入口：按配置转换文本。
     */
    fun process(text: String, config: CatConfig): String {
        var out = text.trim()
        // 自定义替换规则优先（可覆盖内置的我/你规则）
        for ((from, to) in config.replacementRules) {
            out = out.replace(from, to)
        }
        if (config.enableWoToBenmiao) out = out.replace("我", "我..我我")
        if (config.enableNiToZhuren) out = out.replace("你", "主..主人♥")
        if (config.enableMeow) out = addMeow(out, config.meowText)
        if (config.enableRandomEmoticon) {
            val emoji = getRandomEmoticon(config)
            if (emoji.isNotEmpty()) out = "$out $emoji"
        }
        return out
    }

    /**
     * 断句：在句号、叹号、问号等处分句，每句末尾追加 [meowText]（可配置）。
     */
    fun addMeow(text: String, meowText: String): String {
        val separators = SENTENCE_SPLIT_PATTERN.findAll(text)
            .map { it.groupValues[1] }
            .toList()

        val segments = SENTENCE_SPLIT_PATTERN.split(text)

        val sb = StringBuilder()
        for (i in segments.indices) {
            val seg = segments[i].trim()
            if (seg.isNotEmpty()) {
                sb.append(seg).append(meowText)
                if (i < separators.size) sb.append(separators[i])
            }
        }
        return sb.toString().trim()
    }

    fun getRandomEmoticon(config: CatConfig): String {
        val list = config.getActiveEmoticons()
        if (list.isEmpty()) return ""
        return list[RANDOM.nextInt(list.size)]
    }

    /**
     * 反向剥离：从输入框中读到的已转换文本中，移除上次添加的颜文字 / "哦齁齁齁♥"，
     * 还原出用户真正的原始输入，避免重复叠加。
     */
    fun stripAll(text: String, config: CatConfig): String {
        var result = text

        // 1. 移除所有颜文字（长的优先，避免误删子串）
        val emoticons = config.getActiveEmoticons()
            .filter { it.isNotEmpty() }
            .sortedByDescending { it.length }
        for (e in emoticons) {
            var idx = result.indexOf(e)
            while (idx >= 0) {
                var start = idx
                if (start > 0 && result[start - 1] == ' ') start--
                result = result.substring(0, start) + result.substring(idx + e.length)
                idx = result.indexOf(e)
            }
        }

        // 2. 移除断句文字（meowText）
        if (config.meowText.isNotEmpty()) {
            result = result.replace(config.meowText, "")
        }

        // 3. 逆向还原自定义替换规则（to→from，逆序）——关键：否则再次处理会累积爆炸
        for ((from, to) in config.replacementRules.asReversed()) {
            if (from.isNotEmpty() && to.isNotEmpty()) {
                result = result.replace(to, from)
            }
        }

        // 4. 内置 我/你 规则逆向
        if (config.enableWoToBenmiao) result = result.replace("我..我我", "我")
        if (config.enableNiToZhuren) result = result.replace("主..主人♥", "你")

        // 5. 清理连续符号装饰
        result = result.replace(DECORATION_STRIP, " ")

        return result.trim()
    }
}
