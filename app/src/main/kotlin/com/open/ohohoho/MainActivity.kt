package com.open.ohohoho

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.open.ohohoho.ui.MainViewModel
import com.open.ohohoho.ui.OpenOhohoScreen
import com.open.ohohoho.ui.theme.AppTheme

/**
 * 主界面入口：使用 Jetpack Compose + Material 3。
 * 业务逻辑全部在 [MainViewModel]，本类只负责装配主题与屏幕。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                val vm: MainViewModel = viewModel()
                OpenOhohoScreen(vm)
            }
        }
    }
}
