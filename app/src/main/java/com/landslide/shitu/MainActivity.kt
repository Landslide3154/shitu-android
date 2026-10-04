package com.landslide.shitu

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.landslide.shitu.data.Settings
import com.landslide.shitu.service.WatchService
import com.landslide.shitu.ui.ShituRoot
import com.landslide.shitu.ui.theme.ShituTheme
import com.landslide.shitu.ui.theme.resolveDark
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 让状态栏/导航栏图标跟随主题（浅色主题用深色图标，深色主题用浅色图标）
        enableEdgeToEdge()
        requestNotificationPermission()
        WatchService.start(this)
        val app = application as ShituApp
        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
            val dark = resolveDark(settings.themeMode, isSystemInDarkTheme())
            // 主题切换时（含手动选浅色/深色）重新设置系统栏图标颜色
            LaunchedEffect(dark) {
                val barStyle = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }
            ShituTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                ShituRoot(app)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 打开 App 立即扫一次（规格 §6.7）
        val app = application as ShituApp
        lifecycleScope.launch { runCatching { app.runDueRules(force = true) } }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }
}
