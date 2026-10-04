package com.landslide.shitu

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.landslide.shitu.service.WatchService
import com.landslide.shitu.ui.ShituRoot
import com.landslide.shitu.ui.theme.ShituTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        WatchService.start(this)
        setContent {
            ShituTheme {
                ShituRoot(application as ShituApp)
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
