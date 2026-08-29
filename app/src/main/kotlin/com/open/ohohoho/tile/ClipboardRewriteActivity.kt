package com.open.ohohoho.tile

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.open.ohohoho.util.ClipboardRewriter

/**
 * 前台改写页：透明无界面，由快捷设置磁贴唤起（处于前台可读剪贴板），
 * 改写后 Toast 提示并立即 finish，用户回到原应用直接粘贴。
 */
class ClipboardRewriteActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val msg = ClipboardRewriter.rewrite(this)
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        finish()
    }
}
