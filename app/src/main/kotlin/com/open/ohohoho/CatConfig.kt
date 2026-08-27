package com.open.ohohoho

import android.content.Context

/**
 * 配置数据类：控制文字转换的各个开关。
 * 与 SharedPreferences("cat_config") 双向读写。
 */
data class CatConfig(
    var enableMeow: Boolean = true,          // 断句后每句加"哦齁齁齁♥"
    var enableWoToBenmiao: Boolean = true,   // 我 -> 我..我我
    var enableNiToZhuren: Boolean = false,   // 你 -> 主..主人♥
    var enableRandomEmoticon: Boolean = true,// 末尾追加随机猫咪颜文字
    var processingMode: String = MODE_PUNCTUATION,
    var customEmoticons: Array<String> = emptyArray(),
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
            .apply()
    }

    companion object {
        const val PREFS_NAME = "cat_config"
        const val MODE_PUNCTUATION = "punctuation"
        const val MODE_REALTIME = "realtime"

        fun load(context: Context): CatConfig {
            val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return CatConfig(
                enableMeow = sp.getBoolean("enable_meow", true),
                enableWoToBenmiao = sp.getBoolean("enable_wo", true),
                enableNiToZhuren = sp.getBoolean("enable_ni", false),
                enableRandomEmoticon = sp.getBoolean("enable_emoticon", true),
                processingMode = sp.getString("processing_mode", MODE_PUNCTUATION)
                    ?: MODE_PUNCTUATION,
                customEmoticons = sp.getString("custom_emoticons", "")
                    ?.split("\n")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.toTypedArray() ?: emptyArray()
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
