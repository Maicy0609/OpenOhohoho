package com.open.ohohoho

import android.content.Context

/**
 * 配置数据类：控制文字转换的各个开关。
 * 与 SharedPreferences("cat_config") 双向读写。
 */
data class CatConfig(
    var enableMeow: Boolean = true,          // 断句后每句加"哦齁齁齁♥"
    var enableWoToBenmiao: Boolean = false,  // 我 -> 我..我我（已并入规则集）
    var enableNiToZhuren: Boolean = false,   // 你 -> 主..主人♥（已并入规则集）
    var enableRandomEmoticon: Boolean = true,// 末尾追加随机猫咪颜文字
    var processingMode: String = MODE_REALTIME, // 默认实时修改
    var customEmoticons: Array<String> = emptyArray(),
    var replacementRules: List<Pair<String, String>> = emptyList(), // 自定义替换规则
    var isWhitelistMode: Boolean = true,       // true=白名单，false=黑名单
    var managedPackages: List<String> = emptyList(), // 参与修改的应用包名
) {

    fun getActiveEmoticons(): Array<String> =
        if (customEmoticons.isNotEmpty()) customEmoticons else BUILTIN_EMOTICONS

    fun save(context: Context) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit()
            .putBoolean("enable_meow", enableMeow)
            .putBoolean("enable_wo", enableWoToBenmiao)
            .putBoolean("enable_ni", enableNiToZhuren)
            .putBoolean("enable_emoticon", enableRandomEmoticon)
            .putString("processing_mode", processingMode)
            .putString("custom_emoticons", customEmoticons.joinToString("\n"))
            .putString("custom_rules", replacementRules.joinToString("\n") { "${it.first}=${it.second}" })
            .putBoolean("whitelist_mode", isWhitelistMode)
            .putString("managed_packages", managedPackages.joinToString("\n"))
            .apply()
    }

    companion object {
        const val PREFS_NAME = "cat_config"
        const val MODE_PUNCTUATION = "punctuation"
        const val MODE_REALTIME = "realtime"

        /** 默认白名单包名（QQ 家族 + 微信）。 */
        val DEFAULT_PACKAGES = listOf(
            "com.tencent.mobileqq",
            "com.tencent.mobileqqi",
            "com.tencent.qqlite",
            "com.tencent.tim",
            "com.tencent.mm",
        )

        fun load(context: Context): CatConfig {
            val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return CatConfig(
                enableMeow = sp.getBoolean("enable_meow", true),
                enableWoToBenmiao = sp.getBoolean("enable_wo", false),
                enableNiToZhuren = sp.getBoolean("enable_ni", false),
                enableRandomEmoticon = sp.getBoolean("enable_emoticon", true),
                processingMode = sp.getString("processing_mode", MODE_REALTIME)
                    ?: MODE_REALTIME,
                customEmoticons = sp.getString("custom_emoticons", "")
                    ?.split("\n")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.toTypedArray() ?: emptyArray(),
                replacementRules = sp.getString("custom_rules", "")
                    ?.split("\n")
                    ?.mapNotNull { line ->
                        val t = line.trim()
                        if (t.isEmpty()) return@mapNotNull null
                        val idx = t.indexOf('=')
                        if (idx <= 0) return@mapNotNull null
                        val from = t.substring(0, idx).trim()
                        val to = t.substring(idx + 1).trim()
                        if (from.isEmpty() || to.isEmpty()) null else from to to
                    } ?: emptyList(),
                isWhitelistMode = sp.getBoolean("whitelist_mode", true),
                managedPackages = sp.getString("managed_packages", null)
                    ?.split("\n")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.ifEmpty { null } ?: DEFAULT_PACKAGES
            )
        }

        /** 内置猫咪颜文字库（来自原项目反编译清单）。 */
        val BUILTIN_EMOTICONS = arrayOf(
            "^⌯𖥦⌯^ ੭ ^", "⌯'ㅅ'⌯", "=^𖥦^=", "⌯•ㅅ•⌯", "ฅ•̀∀•́ฅ",
            "ฅ ̳͒•ˑ̫• ̳͒ฅ♡", "ฅ(̳•·̫•̳ฅ)♡", "ฅ^••^ฅ", "=^•ω•^=", "₍^ >ヮ<^₎",
            "/ᐠ - ˕ -マ Ⳋ", "ฅ^•ﻌ•^ฅ", "ฅ՞•ﻌ•՞ฅ", "(ฅ´ω`ฅ)", "ฅ(*`ω´*)ฅ",
            "ฅ꒰ ⸝˶• •˶⸝꒱ฅ", "₍˄·͈༝·͈˄*₎◞ ̑̑", "!!^⌯𖥦⌯^ ੭!!", "₍^⸝⸝> ·̫ <⸝⸝ ^₎",
            "ฅ^._.^ฅ", "₍🎀˄•͈༝•͈˄₎ฅ˒˒", "^•͈༝•^ฅ", "꒰ఎ(^ . ֑ .^)໒꒱", "ฅ●ω●ฅ",
            "₍⸍⸌·͈༝·͈⸍⸌₎◞", "(>^ω^<)", "ฅ^-﹃-^ฅ", "^ ̳ට ̫ ට ̳^", "୧₍˄·͈༝·͈˄₎୨",
            "^ ̳ᴗ  ̫ ᴗ ̳^", "˓˓ก(⸍⸌̣ʷ̣̫⸍̣⸌₎ค˒˒", "ヽ(ฅ≧へ≦)ฅ", "(`･ω･´)ฅ", "(=^･ᴥ･^=)",
            "(^ω^ฅ)", "ฅ(≧▽≦)ฅ", "ฅ(=´▽`=)ฅ", "ヾ((๑˘ㅂ˘๑)ฅ", "(ฅ◑ω◑ฅ)",
            "(๑•̀ω•́ฅ)", "(ฅ>ω<*ฅ)", "(=^.^=)", "(=´ᴥ`)", "(=ↀωↀ=)",
            "(=^-ω-^=)", "ฅ(*°ω°*ฅ)", "ヽ(=^･ω･^=)丿", "(^•ᴥ•^)", "( Φ ω Φ )",
            "(=^x^=)", "ฅ( ̳• ◡ • ̳)ฅ", "o( =•ω•= )m", "~o( =∩ω∩= )m", "≡ω≡"
        )
    }
}
