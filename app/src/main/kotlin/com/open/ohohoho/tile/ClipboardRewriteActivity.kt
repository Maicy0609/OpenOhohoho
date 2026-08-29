package com.open.ohohoho.tile

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.open.ohohoho.util.ClipboardRewriter

/**
 * 前台改写页：由快捷设置磁贴唤起（处于前台，可读剪贴板）。
 * 改写后提示并稍后关闭，用户回到原应用直接粘贴。
 */
class ClipboardRewriteActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val msg = ClipboardRewriter.rewrite(this)
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        // 延迟关闭，避免 onCreate 立刻 finish() 引发的生命周期/渲染异常
        window.decorView.postDelayed({ finish() }, 1200)
    }
}
