package com.open.ohohoho.tile

import android.content.Intent
import android.service.quicksettings.TileService

/**
 * 快捷设置磁贴：下拉通知栏点一下，打开前台改写页，
 * 把剪贴板内容按规则改写后写回，用户直接粘贴。
 */
class RewriteTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, ClipboardRewriteActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivityAndCollapse(intent)
    }
}
